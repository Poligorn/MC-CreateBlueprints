package com.blueprintforge.data;

import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Assembly recipes the current datapack actually supports. */
public final class AssemblyRegistry {
    private static volatile Map<ResourceLocation, AssemblyRecipe> byId = Map.of();

    private AssemblyRegistry() {
    }

    public static void replace(Map<ResourceLocation, AssemblyRecipe> loaded) {
        byId = Map.copyOf(loaded);
    }

    public static Map<ResourceLocation, AssemblyRecipe> all() {
        return byId;
    }

    public static Optional<AssemblyRecipe> get(ResourceLocation id) {
        return Optional.ofNullable(byId.get(id));
    }
}
