package com.blueprintforge.research;

import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Tiers and targets of the current datapack load, replaced as a whole on every reload. Server side only for now. */
public final class ResearchRegistry {
    private static volatile Map<ResourceLocation, ResearchTier> tiers = Map.of();
    private static volatile Map<ResourceLocation, ResearchTarget> targets = Map.of();

    private ResearchRegistry() {
    }

    public static Map<ResourceLocation, ResearchTier> tiers() {
        return tiers;
    }

    public static Map<ResourceLocation, ResearchTarget> targets() {
        return targets;
    }

    /** Empty for an id that is not loaded, including one whose file was broken. */
    public static Optional<ResearchTier> tier(ResourceLocation id) {
        return Optional.ofNullable(tiers.get(id));
    }

    public static Optional<ResearchTarget> target(ResourceLocation id) {
        return Optional.ofNullable(targets.get(id));
    }

    static void replace(Map<ResourceLocation, ResearchTier> loadedTiers, Map<ResourceLocation, ResearchTarget> loadedTargets) {
        tiers = Map.copyOf(loadedTiers);
        targets = Map.copyOf(loadedTargets);
    }
}
