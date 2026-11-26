package com.blueprintforge.loot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.resources.ResourceLocation;

/** Loot sources of the current datapack load, indexed by loot table id and replaced as a whole on every reload. */
public final class BlueprintSourceRegistry {
    private static volatile Map<ResourceLocation, List<BlueprintSource>> byTable = Map.of();

    private BlueprintSourceRegistry() {
    }

    public static List<BlueprintSource> sourcesFor(ResourceLocation tableId) {
        return byTable.getOrDefault(tableId, List.of());
    }

    public static Map<ResourceLocation, List<BlueprintSource>> all() {
        return byTable;
    }

    /** Called by the loader on every reload; also lets GameTests install a source of their own. Order follows the source ids. */
    public static void replace(Map<ResourceLocation, BlueprintSource> sources) {
        Map<ResourceLocation, List<BlueprintSource>> index = new HashMap<>();
        new TreeMap<>(sources).values().forEach(source ->
                source.tables().forEach(table -> index.computeIfAbsent(table, k -> new ArrayList<>()).add(source)));
        index.replaceAll((table, list) -> List.copyOf(list));
        byTable = Map.copyOf(index);
    }
}
