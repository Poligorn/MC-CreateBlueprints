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

    public record MobDropSource(ResourceLocation sourceId, ResourceLocation blueprint, SourceDefinition.Conditions conditions,
                                SourceDefinition.MobDrop drop) {
    }

    public record FishingSource(ResourceLocation sourceId, ResourceLocation blueprint, SourceDefinition.Conditions conditions,
                                SourceDefinition.Fishing fishing) {
    }

    private static volatile Map<ResourceLocation, SourceDefinition> sources = Map.of();
    private static volatile Map<ResourceLocation, List<Injection>> byTable = Map.of();
    private static volatile List<MobDropSource> mobDrops = List.of();
    private static volatile List<FishingSource> fishing = List.of();

    private SourceRegistry() {
    }

    public static Map<ResourceLocation, SourceDefinition> all() {
        return sources;
    }

    public static List<Injection> injectionsFor(ResourceLocation lootTable) {
        return byTable.getOrDefault(lootTable, List.of());
    }

    public static List<MobDropSource> mobDrops() {
        return mobDrops;
    }

    public static List<FishingSource> fishing() {
        return fishing;
    }

    static void replace(Map<ResourceLocation, SourceDefinition> loaded) {
        Map<ResourceLocation, List<Injection>> index = new HashMap<>();
        List<MobDropSource> mobs = new ArrayList<>();
        List<FishingSource> rods = new ArrayList<>();
        loaded.forEach((id, source) -> {
            for (SourceDefinition.LootInjection injection : source.lootInjections()) {
                for (ResourceLocation table : injection.targetTables()) {
                    index.computeIfAbsent(table, t -> new ArrayList<>()).add(
                            new Injection(id, source.blueprint(), source.conditions(), injection.chance(), injection.addEntries()));
                }
            }
            for (SourceDefinition.MobDrop drop : source.mobDrops()) {
                mobs.add(new MobDropSource(id, source.blueprint(), source.conditions(), drop));
            }
            for (SourceDefinition.Fishing rod : source.fishing()) {
                rods.add(new FishingSource(id, source.blueprint(), source.conditions(), rod));
            }
        });
        index.replaceAll((table, list) -> List.copyOf(list));
        sources = Map.copyOf(loaded);
        byTable = Map.copyOf(index);
        mobDrops = List.copyOf(mobs);
        fishing = List.copyOf(rods);
    }
}
