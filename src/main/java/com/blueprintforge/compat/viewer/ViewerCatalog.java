package com.blueprintforge.compat.viewer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.blueprintforge.BFConfig;
import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.ResearchProfile;
import com.blueprintforge.data.ResearchRegistry;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.logic.EfficiencyMath;
import com.blueprintforge.logic.EnchantPolicy;
import com.blueprintforge.logic.ResearchAxis;
import com.blueprintforge.registry.BFComponents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Plain pages for JEI and EMI. This class does not import either mod, so the game still loads when both are absent.
 * Create's own viewer shows machine recipes; {@code BlueprintSlotIngredient} already fills the slot with real documents.
 */
public final class ViewerCatalog {
    public static final int WIDTH = 160;
    public static final int HEIGHT = 72;

    private ViewerCatalog() {
    }

    /** One ME or TE step, priced the way the Archive charges it. The document is not spent. */
    public record ResearchStep(
            ResourceLocation id,
            ItemStack document,
            List<ItemStack> cost,
            ItemStack result,
            Component label
    ) {
    }

    /** A print at {@code default_runs}. The cost stacks are the live price of that run count. */
    public record CopyPrint(
            ResourceLocation id,
            ItemStack original,
            List<ItemStack> cost,
            ItemStack copy,
            Component label
    ) {
    }

    /** One tier frame: name, color, whether a blueprint is required, and the durability multiplier. */
    public record TierPage(
            ResourceLocation id,
            Component name,
            boolean requiresBlueprint,
            String durability,
            List<ItemStack> examples,
            Component requirement,
            @Nullable Component enchantLine
    ) {
    }

    public static List<ResearchStep> researchSteps() {
        List<ResearchStep> steps = new ArrayList<>();
        for (var entry : BlueprintRegistry.all().entrySet()) {
            BlueprintDefinition definition = entry.getValue();
            if (definition.clazz() != BlueprintClass.ORIGINAL && definition.clazz() != BlueprintClass.COPY) {
                continue;
            }
            ResearchProfile profile = ResearchRegistry.forBlueprint(entry.getKey()).orElse(null);
            if (profile == null) {
                continue;
            }
            if (definition.clazz() == BlueprintClass.COPY && !profile.allowResearchOnCopy()) {
                continue;
            }
            addStep(steps, entry.getKey(), definition, profile, ResearchAxis.MATERIAL);
            addStep(steps, entry.getKey(), definition, profile, ResearchAxis.FLUX);
            addStep(steps, entry.getKey(), definition, profile, ResearchAxis.POTENCY);
        }
        return List.copyOf(steps);
    }

    public static List<CopyPrint> copyPrints() {
        List<CopyPrint> prints = new ArrayList<>();
        for (var entry : BlueprintRegistry.all().entrySet()) {
            BlueprintDefinition definition = entry.getValue();
            BlueprintDefinition.CopyRules rules = definition.copy().orElse(null);
            if (rules == null || !rules.enabled()) {
                continue;
            }
            if (definition.clazz() == BlueprintClass.COPY && !rules.allowFromCopy()) {
                continue;
            }
            if (definition.clazz() != BlueprintClass.ORIGINAL && definition.clazz() != BlueprintClass.COPY) {
                continue;
            }
            if (ResearchRegistry.forBlueprint(entry.getKey()).isEmpty()) {
                continue;
            }
            if (hasFluid(rules.copyCost())) {
                continue;
            }
            ItemStack original = document(entry.getKey(), definition);
            BlueprintData data = BlueprintItem.data(original).orElseThrow();
            int me = EfficiencyMath.penalized(data.materialEfficiency(), rules.mePenalty());
            int flux = EfficiencyMath.penalized(data.flux(), rules.fluxPenalty());
            ItemStack copy = original.copy();
            copy.set(BFComponents.BLUEPRINT.get(), data.printedCopy(
                    stable(entry.getKey() + "/copy"), rules.defaultRuns(), me, flux, data.potency(), Optional.empty(), Optional.empty()));
            prints.add(new CopyPrint(
                    recipeId("copy/" + slug(entry.getKey())),
                    original,
                    itemCosts(rules.copyCost(), rules.defaultRuns(), rules.defaultRuns(), rules.costScaling(), true),
                    copy,
                    Component.translatable("gui.blueprintforge.viewer.copy_line",
                            rules.defaultRuns(), rules.mePenalty(), rules.fluxPenalty())));
        }
        return List.copyOf(prints);
    }

    public static List<TierPage> tiers() {
        EnchantPolicy.Mode mode = enchantingMode();
        List<TierPage> pages = new ArrayList<>();
        TierRegistry.all().entrySet().stream()
                .sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
                .forEach(entry -> {
                    TierDefinition tier = entry.getValue();
                    List<ItemStack> examples = new ArrayList<>();
                    for (var blueprint : BlueprintRegistry.all().entrySet()) {
                        if (blueprint.getValue().tier().equals(entry.getKey()) && examples.size() < 6) {
                            examples.add(document(blueprint.getKey(), blueprint.getValue()));
                        }
                    }
                    pages.add(new TierPage(
                            recipeId("tier/" + slug(entry.getKey())),
                            tier.display().copy().withColor(tier.color()),
                            tier.requiresBlueprint(),
                            formatMultiplier(tier.globalDurabilityMultiplier()),
                            List.copyOf(examples),
                            Component.translatable(tier.requiresBlueprint()
                                    ? "gui.blueprintforge.viewer.tier_requires"
                                    : "gui.blueprintforge.viewer.tier_free"),
                            enchantLine(mode, entry.getKey())));
                });
        return List.copyOf(pages);
    }

    /** Documents a Create recipe can accept. Fragments never enter the slot. */
    public static List<ItemStack> productionDocuments() {
        List<ItemStack> stacks = new ArrayList<>();
        for (var entry : BlueprintRegistry.all().entrySet()) {
            if (entry.getValue().clazz() != BlueprintClass.FRAGMENT) {
                stacks.add(document(entry.getKey(), entry.getValue()));
            }
        }
        return List.copyOf(stacks);
    }

    public static List<Component> slotRule() {
        return List.of(Component.translatable("gui.blueprintforge.viewer.slot_rule"));
    }

    /** Empty in {@code off}: the table is vanilla there, so the viewer must not say otherwise. */
    public static List<Component> enchantNote() {
        return switch (enchantingMode()) {
            case OFF -> List.of();
            case RESTRICTED -> List.of(Component.translatable("gui.blueprintforge.viewer.enchant_restricted"));
            case FULL -> List.of(Component.translatable("gui.blueprintforge.viewer.enchant_full"));
            case SCALED -> List.of(Component.translatable("gui.blueprintforge.viewer.enchant_restricted"));
        };
    }

    private static void addStep(List<ResearchStep> steps, ResourceLocation blueprintId, BlueprintDefinition definition,
                                ResearchProfile profile, ResearchAxis axis) {
        List<BlueprintDefinition.CostEntry> cost = switch (axis) {
            case MATERIAL -> profile.meStepCost();
            case FLUX -> profile.fluxStepCost();
            case POTENCY -> profile.potencyStepCost();
        };
        double multiplier = definition.range(axis).costMultiplier();
        if (multiplier != 1.0) {
            List<BlueprintDefinition.CostEntry> scaled = new ArrayList<>();
            for (BlueprintDefinition.CostEntry entry : cost) {
                int amount = (int) Math.ceil(entry.amount() * multiplier - 1.0E-9);
                if (amount > 0) {
                    scaled.add(new BlueprintDefinition.CostEntry(entry.itemOrFluid(), amount));
                }
            }
            cost = scaled;
        }
        if (hasFluid(cost)) {
            return;
        }
        BlueprintDefinition.EfficiencyRange range = definition.range(axis);
        OptionalInt next = EfficiencyMath.nextStep(range.min(), range.max(), range.step());
        if (next.isEmpty()) {
            return;
        }
        ItemStack document = document(blueprintId, definition);
        BlueprintData data = BlueprintItem.data(document).orElseThrow();
        int me = axis == ResearchAxis.MATERIAL ? next.getAsInt() : data.materialEfficiency();
        int flux = axis == ResearchAxis.FLUX ? next.getAsInt() : data.flux();
        int potency = axis == ResearchAxis.POTENCY ? next.getAsInt() : data.potency();
        ItemStack result = document.copy();
        result.set(BFComponents.BLUEPRINT.get(), data.withResearch(me, flux, potency, Optional.empty(), Optional.empty()));
        String axisName = switch (axis) {
            case MATERIAL -> "me";
            case FLUX -> "flux";
            case POTENCY -> "potency";
        };
        String key = switch (axis) {
            case MATERIAL -> "gui.blueprintforge.viewer.step_me";
            case FLUX -> "gui.blueprintforge.viewer.step_flux";
            case POTENCY -> "gui.blueprintforge.viewer.step_potency";
        };
        steps.add(new ResearchStep(
                recipeId("research/" + slug(blueprintId) + "_" + axisName),
                document,
                itemCosts(cost, 1, 1, 1.0, false),
                result,
                Component.translatable(key, range.min(), next.getAsInt(), range.max())));
    }

    private static @Nullable Component enchantLine(EnchantPolicy.Mode mode, ResourceLocation tierId) {
        return switch (mode) {
            case OFF -> null;
            case FULL -> Component.translatable("gui.blueprintforge.viewer.enchant_full");
            case RESTRICTED -> {
                boolean requiresBlueprint = TierRegistry.get(tierId).map(TierDefinition::requiresBlueprint).orElse(true);
                yield requiresBlueprint ? Component.translatable("gui.blueprintforge.viewer.tier_no_table") : null;
            }
            case SCALED -> {
                boolean requiresBlueprint = TierRegistry.get(tierId).map(TierDefinition::requiresBlueprint).orElse(true);
                yield requiresBlueprint ? Component.translatable("gui.blueprintforge.viewer.tier_no_table") : null;
            }
        };
    }

    private static EnchantPolicy.Mode enchantingMode() {
        try {
            return BFConfig.enchantingMode();
        } catch (RuntimeException ignored) {
            return EnchantPolicy.Mode.RESTRICTED;
        }
    }

    private static boolean hasFluid(List<BlueprintDefinition.CostEntry> entries) {
        for (BlueprintDefinition.CostEntry entry : entries) {
            if (entry.itemOrFluid().right().isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemStack> itemCosts(List<BlueprintDefinition.CostEntry> entries, int runs, int defaultRuns,
                                             double scaling, boolean scale) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlueprintDefinition.CostEntry entry : entries) {
            ResourceLocation itemId = entry.itemOrFluid().left().orElse(null);
            if (itemId == null) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null || item == Items.AIR) {
                continue;
            }
            int count = scale ? EfficiencyMath.copyCost(entry.amount(), runs, defaultRuns, scaling) : entry.amount();
            while (count > 0) {
                int pile = Math.min(item.getDefaultMaxStackSize(), count);
                stacks.add(new ItemStack(item, pile));
                count -= pile;
            }
        }
        return List.copyOf(stacks);
    }

    private static ItemStack document(ResourceLocation id, BlueprintDefinition definition) {
        return BlueprintItem.createInstance(id, definition, stable(id.toString()));
    }

    private static UUID stable(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static ResourceLocation recipeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(BlueprintForge.MOD_ID, path);
    }

    private static String slug(ResourceLocation id) {
        return id.getNamespace() + "_" + id.getPath();
    }

    private static String formatMultiplier(double value) {
        return new BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString();
    }
}
