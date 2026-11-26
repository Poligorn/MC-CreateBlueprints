package com.blueprintforge.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

class BlueprintSourceTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("blueprintforge", path);
    }

    private static BlueprintSourceLoader.Result load(Map<String, String> files) {
        Map<ResourceLocation, JsonElement> json = new TreeMap<>();
        files.forEach((name, text) -> json.put(id(name), JsonParser.parseString(text)));
        return BlueprintSourceLoader.load(new BlueprintSourceLoader.RawData(json, new ArrayList<>()), JsonOps.INSTANCE);
    }

    private static String source(String type, String tables, String chance, String weights) {
        return """
                {"type": %s, "tables": %s, "chance": %s, "tier_weights": %s}
                """.formatted(type, tables, chance, weights);
    }

    private static final String OK_TABLES = "[\"minecraft:chests/simple_dungeon\"]";
    private static final String OK_WEIGHTS = "{\"blueprintforge:t1\": 3, \"blueprintforge:t2\": 1}";

    private static boolean hasError(BlueprintSourceLoader.Result result, String... fragments) {
        return result.errors().stream().anyMatch(e -> java.util.Arrays.stream(fragments).allMatch(e::contains));
    }

    @Test
    void validSourceLoadsAndIgnoresUnknownFields() {
        BlueprintSourceLoader.Result result = load(Map.of("ok",
                "{\"_comment\": \"x\", \"type\": \"chest\", \"tables\": " + OK_TABLES + ", \"chance\": 0.1, \"tier_weights\": " + OK_WEIGHTS + "}"));
        assertEquals(List.of(), result.errors());
        BlueprintSource loaded = result.sources().get(id("ok"));
        assertEquals(BlueprintSource.Type.CHEST, loaded.type());
        assertEquals(0.1F, loaded.chance());
        assertEquals(3, loaded.tierWeights().get(id("t1")));
    }

    @Test
    void brokenFileIsSkippedAndTheRestLoads() {
        BlueprintSourceLoader.Result result = load(Map.of(
                "good", source("\"chest\"", OK_TABLES, "0.1", OK_WEIGHTS),
                "not_an_object", "[1, 2]",
                "missing_field", "{\"type\": \"chest\"}",
                "chance_too_high", source("\"chest\"", OK_TABLES, "1.5", OK_WEIGHTS),
                "negative_chance", source("\"chest\"", OK_TABLES, "-0.1", OK_WEIGHTS),
                "no_tables", source("\"chest\"", "[]", "0.1", OK_WEIGHTS),
                "no_weights", source("\"chest\"", OK_TABLES, "0.1", "{}"),
                "zero_weight", source("\"chest\"", OK_TABLES, "0.1", "{\"blueprintforge:t1\": 0}"),
                "bad_table_id", source("\"chest\"", "[\"Not A Table\"]", "0.1", OK_WEIGHTS)));

        assertEquals(java.util.Set.of(id("good")), result.sources().keySet());
        assertEquals(8, result.errors().size(), result.errors().toString());
    }

    @Test
    void mobDropTypeIsRefusedOnPurpose() {
        BlueprintSourceLoader.Result result = load(Map.of(
                "mob", source("\"mob_drop\"", OK_TABLES, "0.5", OK_WEIGHTS),
                "good", source("\"chest\"", OK_TABLES, "0.1", OK_WEIGHTS)));
        assertEquals(java.util.Set.of(id("good")), result.sources().keySet());
        assertTrue(hasError(result, "mob", "mob_drop"), result.errors().toString());
    }

    @Test
    void unknownTypeIsRefused() {
        BlueprintSourceLoader.Result result = load(Map.of("future", source("\"structure\"", OK_TABLES, "0.5", OK_WEIGHTS)));
        assertTrue(result.sources().isEmpty());
        assertTrue(hasError(result, "future", "unknown source type"), result.errors().toString());
    }

    @Test
    void entityLootTablesAreRefused() {
        BlueprintSourceLoader.Result result = load(Map.of(
                "zombies", source("\"chest\"", "[\"minecraft:entities/zombie\"]", "0.5", OK_WEIGHTS),
                "mixed", source("\"chest\"", "[\"minecraft:chests/simple_dungeon\", \"minecraft:entities/zombie\"]", "0.5", OK_WEIGHTS)));
        assertTrue(result.sources().isEmpty());
        assertEquals(2, result.errors().size(), result.errors().toString());
    }

    @Test
    void shippedSourcesLoadWithoutErrorsAndNeverListMobsOrHighTiers() throws IOException {
        Map<String, String> files = new TreeMap<>();
        for (String name : new String[] {"simple_chests", "common_dungeons", "rare_structures"}) {
            String resource = "data/blueprintforge/blueprint_source/" + name + ".json";
            try (InputStream in = BlueprintSourceTest.class.getClassLoader().getResourceAsStream(resource)) {
                assertTrue(in != null, "missing shipped file " + resource);
                files.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        BlueprintSourceLoader.Result result = load(files);
        assertEquals(List.of(), result.errors());
        assertEquals(3, result.sources().size());
        result.sources().values().forEach(source -> {
            assertTrue(source.chance() > 0 && source.chance() < 1);
            assertTrue(source.tierWeights().keySet().stream().allMatch(t -> List.of("t1", "t2", "t3").contains(t.getPath())), source.toString());
            assertTrue(source.tables().stream().noneMatch(t -> t.getPath().startsWith("entities/")));
        });
        // Simple chests are T1 only and clearly rarer than dungeons.
        assertEquals(java.util.Set.of(id("t1")), result.sources().get(id("simple_chests")).tierWeights().keySet());
        assertTrue(result.sources().get(id("simple_chests")).chance() < result.sources().get(id("common_dungeons")).chance());
    }

    @Test
    void effectiveChanceScalesAndClamps() {
        assertEquals(0.1F, BlueprintLoot.effectiveChance(0.1F, 1.0), 1e-6);
        assertEquals(0.2F, BlueprintLoot.effectiveChance(0.1F, 2.0), 1e-6);
        assertEquals(0.05F, BlueprintLoot.effectiveChance(0.1F, 0.5), 1e-6);
        assertEquals(0.0F, BlueprintLoot.effectiveChance(0.1F, 0.0));
        assertEquals(1.0F, BlueprintLoot.effectiveChance(0.1F, 1000.0));
        assertEquals(1.0F, BlueprintLoot.effectiveChance(1.0F, 1.0));
        assertEquals(0.0F, BlueprintLoot.effectiveChance(0.0F, 1000.0));
        assertEquals(0.0F, BlueprintLoot.effectiveChance(0.1F, -3.0), "a negative value cannot turn into a chance");
    }

    @Test
    void tierPickFollowsWeightsAndSkipsIneligibleTiers() {
        Map<ResourceLocation, Integer> weights = Map.of(id("t1"), 90, id("t2"), 10, id("t4"), 1000, id("t5"), 1000);
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 10_000; i++) {
            ResourceLocation picked = BlueprintLoot.pickTier(weights, t -> !t.getPath().equals("t4") && !t.getPath().equals("t5"), random).orElseThrow();
            counts.merge(picked, 1, Integer::sum);
        }
        assertEquals(java.util.Set.of(id("t1"), id("t2")), counts.keySet());
        assertTrue(counts.get(id("t1")) > 8_700 && counts.get(id("t1")) < 9_300, counts.toString());
    }

    @Test
    void tierPickIsEmptyWhenNothingIsEligible() {
        assertFalse(BlueprintLoot.pickTier(Map.of(id("t4"), 5), t -> false, RandomSource.create(1)).isPresent());
        assertFalse(BlueprintLoot.pickTier(Map.of(), t -> true, RandomSource.create(1)).isPresent());
    }

    @Test
    void tierPickIsDeterministicForASeed() {
        Map<ResourceLocation, Integer> weights = Map.of(id("t1"), 1, id("t2"), 1, id("t3"), 1);
        List<ResourceLocation> a = new ArrayList<>();
        List<ResourceLocation> b = new ArrayList<>();
        RandomSource r1 = RandomSource.create(7);
        RandomSource r2 = RandomSource.create(7);
        for (int i = 0; i < 50; i++) {
            a.add(BlueprintLoot.pickTier(weights, t -> true, r1).orElseThrow());
            b.add(BlueprintLoot.pickTier(weights, t -> true, r2).orElseThrow());
        }
        assertEquals(a, b);
    }
}
