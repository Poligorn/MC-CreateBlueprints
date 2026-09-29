package com.blueprintforge.data;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import net.minecraft.resources.ResourceLocation;

/** Blueprint definitions of the current datapack load. Replaced as a whole on reload and on client sync. */
public final class BlueprintRegistry {
    private static volatile Map<ResourceLocation, BlueprintDefinition> definitions = Map.of();

    private BlueprintRegistry() {
    }

    /** Sorted by id so the creative tab order is stable. */
    public static Map<ResourceLocation, BlueprintDefinition> all() {
        return definitions;
    }

    public static Optional<BlueprintDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(definitions.get(id));
    }

    static void replace(Map<ResourceLocation, BlueprintDefinition> loaded) {
        definitions = Collections.unmodifiableMap(new TreeMap<>(loaded));
    }
}
