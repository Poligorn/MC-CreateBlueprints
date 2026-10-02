package com.blueprintforge.event;

import java.util.ArrayList;
import java.util.List;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.ForgedItemData;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.logic.ProductionMath;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** MVP tooltip: document class and tier on blueprints, tier and source on forged items. */
@EventBusSubscriber(modid = BlueprintForge.MOD_ID, value = Dist.CLIENT)
public final class TooltipHandler {
    private static final TagKey<Item> HIDE_T0 = TagKey.create(Registries.ITEM, BlueprintForge.id("hide_t0"));

    private TooltipHandler() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> lines = event.getToolTip();
        int insertAt = Math.min(1, lines.size());

        if (stack.is(BFItems.BLUEPRINT.get())) {
            lines.addAll(insertAt, blueprintLines(stack, event));
            return;
        }
        ForgedItemData forged = stack.get(BFComponents.FORGED.get());
        if (forged != null) {
            Component source = BlueprintRegistry.get(forged.blueprintId())
                    .<Component>map(def -> def.display().name())
                    .orElseGet(() -> Component.translatable("item.blueprintforge.blueprint.unknown"));
            lines.add(insertAt, Component.translatable("tooltip.blueprintforge.forged", tierName(forged.tierId()), source)
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        markVanillaTarget(stack, lines, insertAt);
    }

    private static void markVanillaTarget(ItemStack stack, List<Component> lines, int insertAt) {
        if (stack.is(HIDE_T0)) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean visible = false;
        for (var entry : BlueprintRegistry.all().entrySet()) {
            if (entry.getValue().hideT0() || entry.getValue().target().isEmpty()) {
                continue;
            }
            if (entry.getValue().target().get().equals(itemId)) {
                visible = true;
                break;
            }
        }
        if (visible) {
            lines.add(insertAt, Component.translatable("tooltip.blueprintforge.t0").withStyle(ChatFormatting.GRAY));
        }
    }

    private static MutableComponent foundLine(BlueprintData data, ItemTooltipEvent event) {
        if (data.foundGameTime() <= 0 || event.getEntity() == null || event.getEntity().level() == null) {
            return Component.translatable("tooltip.blueprintforge.found_unknown");
        }
        long age = event.getEntity().level().getGameTime() - data.foundGameTime();
        if (age < 24000L) {
            return Component.translatable("tooltip.blueprintforge.found_today");
        }
        return Component.translatable("tooltip.blueprintforge.found_days", age / 24000L);
    }

    private static List<Component> blueprintLines(ItemStack stack, ItemTooltipEvent event) {
        BlueprintData data = BlueprintItem.data(stack).orElse(null);
        if (data == null) {
            return List.of(Component.translatable("tooltip.blueprintforge.blank").withStyle(ChatFormatting.GRAY));
        }
        Component classLine = switch (data.clazz()) {
            case ORIGINAL -> Component.translatable("tooltip.blueprintforge.class.original");
            case COPY -> Component.translatable("tooltip.blueprintforge.class.copy", data.runsRemaining());
            case ANCIENT -> Component.translatable("tooltip.blueprintforge.class.ancient");
            case FRAGMENT -> Component.translatable("tooltip.blueprintforge.class.fragment");
        };
        List<Component> lines = new ArrayList<>();
        lines.add(classLine.copy().withStyle(ChatFormatting.GRAY));
        if (data.clazz() == BlueprintClass.ORIGINAL || data.clazz() == BlueprintClass.COPY) {
            lines.add(Component.translatable("tooltip.blueprintforge.production").withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("tooltip.blueprintforge.tier", tierName(data.tierId())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.blueprintforge.efficiency", data.materialEfficiency(), data.flux(), data.potency())
                .withStyle(ChatFormatting.GRAY));
        lines.add(foundLine(data, event).withStyle(ChatFormatting.GRAY));
        BlueprintRegistry.get(data.definitionId()).flatMap(def -> def.target()).ifPresent(target ->
                lines.add(Component.translatable("tooltip.blueprintforge.target", target.toString()).withStyle(ChatFormatting.GRAY)));
        data.researcherName().ifPresent(name ->
                lines.add(Component.translatable("tooltip.blueprintforge.researcher", name).withStyle(ChatFormatting.GRAY)));
        data.copierName().ifPresent(name ->
                lines.add(Component.translatable("tooltip.blueprintforge.copier", name).withStyle(ChatFormatting.GRAY)));
        data.ownerName().ifPresent(owner ->
                lines.add(Component.translatable("tooltip.blueprintforge.owner", owner).withStyle(ChatFormatting.GRAY)));
        if (ProductionMath.warnRuns(data.runsRemaining())) {
            lines.add(Component.translatable("message.blueprintforge.copy_runs", data.runsRemaining()).withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private static Component tierName(ResourceLocation tierId) {
        TierDefinition tier = TierRegistry.get(tierId).orElse(null);
        if (tier == null) {
            return Component.translatable("tooltip.blueprintforge.tier.unknown");
        }
        MutableComponent name = tier.display().copy();
        return name.withColor(tier.color());
    }
}
