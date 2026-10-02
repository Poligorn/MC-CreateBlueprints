package com.blueprintforge.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.LineDefinition;
import com.blueprintforge.data.LineMark;
import com.blueprintforge.data.LineRegistry;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.machine.BlueprintDockBlockEntity;
import com.blueprintforge.recipe.BlueprintOutputApplicator;
import com.blueprintforge.registry.BFComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;

/**
 * The stamp step of a sequenced line. The belt item must be the line's raw ingredient.
 * A finished tool is not an input. Materials and a copy run are spent when the stamp lands,
 * including when the later roll comes out as scrap.
 */
public final class LineProduction {
    private LineProduction() {
    }

    public static Optional<LineMark> tryStamp(Level level, BlockPos deployerPos, ItemStack input, ItemStack stamp) {
        if (level == null || deployerPos == null || input.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation inputId = BuiltInRegistries.ITEM.getKey(input.getItem());
        for (var entry : LineRegistry.all().entrySet()) {
            LineDefinition line = entry.getValue();
            if (!line.ingredient().equals(inputId)) {
                continue;
            }
            BlueprintDockBlockEntity dock = findDock(level, deployerPos, line.dockRadius(), entry.getKey());
            if (dock == null) {
                continue;
            }
            Optional<LineMark> marked = dock.stamp(entry.getKey(), line, stamp);
            if (marked.isPresent()) {
                return marked;
            }
        }
        return Optional.empty();
    }

    public static ItemStack finish(ServerLevel level, LineMark mark, float roll01) {
        LineDefinition line = LineRegistry.get(mark.lineId()).orElse(null);
        BlueprintDefinition definition = BlueprintRegistry.get(mark.blueprintId()).orElse(null);
        if (line == null || definition == null || definition.target().isEmpty()) {
            return ItemStack.EMPTY;
        }
        double chance = FluxMath.scrapChance(line.scrapChance(), mark.flux());
        if (FluxMath.scraps(chance, roll01)) {
            Item scrap = BuiltInRegistries.ITEM.get(line.scrapItem());
            return scrap == null || scrap == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(scrap);
        }
        Item target = BuiltInRegistries.ITEM.get(definition.target().get());
        if (target == null || target == net.minecraft.world.item.Items.AIR) {
            return ItemStack.EMPTY;
        }
        TierDefinition tier = TierRegistry.get(definition.tier()).orElse(null);
        if (tier == null || level == null) {
            return ItemStack.EMPTY;
        }
        ItemStack forged = BlueprintOutputApplicator.apply(new ItemStack(target), mark.blueprintId(), definition, tier, level.registryAccess());
        applyPotency(level, forged, definition, mark.potency());
        return forged;
    }

    private static void applyPotency(ServerLevel level, ItemStack stack, BlueprintDefinition definition, int potency) {
        if (potency <= 0) {
            return;
        }
        List<BlueprintDefinition.EnchantGrant> grants = definition.potencyOutput().stream()
                .filter(grant -> grant.level() == potency)
                .findFirst()
                .map(BlueprintDefinition.PotencyGrant::enchantments)
                .orElse(List.of());
        if (grants.isEmpty()) {
            return;
        }
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (BlueprintDefinition.EnchantGrant grant : grants) {
            Optional<Holder.Reference<Enchantment>> holder = enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, grant.enchantment()));
            if (holder.isEmpty()) {
                BlueprintForge.LOGGER.debug("Skipping unknown enchantment {} on blueprint {}", grant.enchantment(), definition.tier());
                continue;
            }
            mutable.set(holder.get(), grant.level());
        }
        if (!mutable.toImmutable().isEmpty()) {
            stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        }
    }

    private static BlueprintDockBlockEntity findDock(Level level, BlockPos origin, int radius, ResourceLocation lineId) {
        int r = Math.max(1, radius);
        BlockPos min = origin.offset(-r, -r, -r);
        BlockPos max = origin.offset(r, r, r);
        List<BlueprintDockBlockEntity> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(pos) instanceof BlueprintDockBlockEntity dock && dock.holdsLine(lineId) && !dock.isSwapping()) {
                found.add(dock);
            }
        }
        return found.isEmpty() ? null : found.getFirst();
    }

    /** True when the stamp's component names this definition and the item is in the line's stamp tag. */
    public static boolean stampMatches(ItemStack stamp, LineDefinition line, ResourceLocation blueprintId) {
        TagKey<Item> stampTag = TagKey.create(Registries.ITEM, line.stampTag());
        if (stamp.isEmpty() || !stamp.is(stampTag)) {
            return false;
        }
        var data = stamp.get(BFComponents.STAMP.get());
        return data != null && blueprintId.equals(data.blueprintId());
    }

    public static List<BlueprintDefinition.CostEntry> materialCost(LineDefinition line, int materialEfficiency) {
        List<BlueprintDefinition.CostEntry> scaled = new ArrayList<>();
        for (BlueprintDefinition.CostEntry entry : line.materials()) {
            int amount = EfficiencyMath.consumed(entry.amount(), materialEfficiency);
            if (amount > 0) {
                scaled.add(new BlueprintDefinition.CostEntry(entry.itemOrFluid(), amount));
            }
        }
        return scaled;
    }

    public static boolean canSpendRuns(BlueprintData data) {
        if (data == null) {
            return false;
        }
        if (data.clazz() == BlueprintClass.ORIGINAL) {
            return true;
        }
        return data.clazz() == BlueprintClass.COPY && data.runsRemaining() > 0;
    }

    public static BlueprintData afterStamp(BlueprintData data) {
        if (data.clazz() != BlueprintClass.COPY) {
            return data;
        }
        return data.withRuns(Math.max(0, data.runsRemaining() - 1));
    }
}
