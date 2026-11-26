package com.blueprintforge.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

import com.blueprintforge.item.ResearchBlueprintItem;
import com.blueprintforge.research.ResearchRegistry;
import com.blueprintforge.research.ResearchTarget;
import com.blueprintforge.research.ResearchTier;
import com.blueprintforge.research.SeedAssigner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** What a roll of a loot table adds: the rules of blueprint loot, apart from NeoForge's modifier plumbing. */
public final class BlueprintLoot {
    /** T4 and T5 do not come from loot in 0.1 (they are 0.4 and 0.5 content). */
    public static final int MAX_LOOT_TIER = 3;

    private BlueprintLoot() {
    }

    /** {@code chance} scaled by {@code rarity.multiplier} and clamped to 0..1. */
    public static float effectiveChance(float chance, double multiplier) {
        return (float) Mth.clamp(chance * multiplier, 0.0, 1.0);
    }

    /**
     * Blueprints to add to one roll of {@code tableId}. Nothing for mob loot, nothing at multiplier 0. Every blueprint is an
     * original with the current generator version, a valid tier and a target of that tier, and already has its seed.
     */
    public static List<ItemStack> roll(ResourceLocation tableId, boolean mobLoot, double multiplier, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        if (mobLoot || multiplier <= 0) {
            return out;
        }
        for (BlueprintSource source : BlueprintSourceRegistry.sourcesFor(tableId)) {
            float chance = effectiveChance(source.chance(), multiplier);
            if (chance > 0 && random.nextFloat() < chance) {
                pick(source, random).ifPresent(out::add);
            }
        }
        return out;
    }

    private static Optional<ItemStack> pick(BlueprintSource source, RandomSource random) {
        Optional<ResourceLocation> tierId = pickTier(source.tierWeights(), BlueprintLoot::lootable, random);
        if (tierId.isEmpty()) {
            return Optional.empty();
        }
        ResearchTier tier = ResearchRegistry.tier(tierId.get()).orElseThrow();
        List<Map.Entry<ResourceLocation, ResearchTarget>> targets = targetsOf(tierId.get());
        Map.Entry<ResourceLocation, ResearchTarget> target = targets.get(random.nextInt(targets.size()));
        ItemStack stack = ResearchBlueprintItem.create(tierId.get(), tier, target.getKey(), target.getValue(), false);
        SeedAssigner.assignIfAbsent(stack, random);
        return Optional.of(stack);
    }

    /** A tier that is loaded, within T1..T3 and has at least one target. */
    private static boolean lootable(ResourceLocation tierId) {
        return ResearchRegistry.tier(tierId)
                .filter(tier -> tier.spriteTier() >= 1 && tier.spriteTier() <= MAX_LOOT_TIER)
                .isPresent() && !targetsOf(tierId).isEmpty();
    }

    private static List<Map.Entry<ResourceLocation, ResearchTarget>> targetsOf(ResourceLocation tierId) {
        return new TreeMap<>(ResearchRegistry.targets()).entrySet().stream()
                .filter(entry -> entry.getValue().tier().equals(tierId))
                .toList();
    }

    /** Weighted pick among the eligible tiers, in id order so the result depends only on the random source. Empty if none is eligible. */
    public static Optional<ResourceLocation> pickTier(Map<ResourceLocation, Integer> weights, Predicate<ResourceLocation> eligible, RandomSource random) {
        Map<ResourceLocation, Integer> candidates = new TreeMap<>();
        weights.forEach((tier, weight) -> {
            if (weight > 0 && eligible.test(tier)) {
                candidates.put(tier, weight);
            }
        });
        int total = candidates.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Map.Entry<ResourceLocation, Integer> entry : candidates.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }
}
