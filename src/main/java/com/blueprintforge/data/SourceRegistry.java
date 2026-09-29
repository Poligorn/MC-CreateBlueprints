package com.blueprintforge.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/** Server-side index of blueprint sources, keyed by the loot table they inject into. */
public final class SourceRegistry {
    public record Injection(ResourceLocation sourceId, ResourceLocation blueprint, SourceDefinition.Conditions conditions, float chance, int addEntries) {
    }

    private static volatile Map<ResourceLocation, SourceDefinition> sources = Map.of();
    private static volatile Map<ResourceLocation, List<Injection>> byTable = Map.of();

    private SourceRegistry() {
    }

    public static Map<ResourceLocation, SourceDefinition> all() {
        return sources;
    }

    public static List<Injection> injectionsFor(ResourceLocation lootTable) {
        return byTable.getOrDefault(lootTable, List.of());
    }

    static void replace(Map<ResourceLocation, SourceDefinition> loaded) {
        Map<ResourceLocation, List<Injection>> index = new HashMap<>();
        loaded.forEach((id, source) -> {
            for (SourceDefinition.LootInjection injection : source.lootInjections()) {
                for (ResourceLocation table : injection.targetTables()) {
                    index.computeIfAbsent(table, t -> new ArrayList<>()).add(
                            new Injection(id, source.blueprint(), source.conditions(), injection.chance(), injection.addEntries()));
                }
            }
        });
        index.replaceAll((table, list) -> List.copyOf(list));
        sources = Map.copyOf(loaded);
        byTable = Map.copyOf(index);
    }
}
