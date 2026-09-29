package com.blueprintforge.data;

import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Tiers of the current datapack load. Replaced as a whole on reload and on client sync. */
public final class TierRegistry {
    private static volatile Map<ResourceLocation, TierDefinition> tiers = Map.of();

    private TierRegistry() {
    }

    public static Map<ResourceLocation, TierDefinition> all() {
        return tiers;
    }

    public static Optional<TierDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(tiers.get(id));
    }

    static void replace(Map<ResourceLocation, TierDefinition> loaded) {
        tiers = Map.copyOf(loaded);
    }
}
