package com.blueprintforge.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public final class LineRegistry {
    private static Map<ResourceLocation, LineDefinition> lines = Map.of();
    private static Map<ResourceLocation, ResourceLocation> byRecipe = Map.of();

    private LineRegistry() {
    }

    public static void replace(Map<ResourceLocation, LineDefinition> next) {
        Map<ResourceLocation, LineDefinition> copy = new LinkedHashMap<>(next);
        Map<ResourceLocation, ResourceLocation> recipes = new LinkedHashMap<>();
        copy.forEach((id, line) -> recipes.put(line.recipe(), id));
        lines = Collections.unmodifiableMap(copy);
        byRecipe = Collections.unmodifiableMap(recipes);
    }

    public static Map<ResourceLocation, LineDefinition> all() {
        return lines;
    }

    public static Optional<LineDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(lines.get(id));
    }

    public static Optional<ResourceLocation> idForRecipe(ResourceLocation recipe) {
        return Optional.ofNullable(byRecipe.get(recipe));
    }

    public static boolean isLineRecipe(ResourceLocation recipe) {
        return byRecipe.containsKey(recipe);
    }
}
