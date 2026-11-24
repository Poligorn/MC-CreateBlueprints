package com.blueprintforge.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

class ResearchDataLoaderTest {
    private static final Predicate<ResourceLocation> ITEMS = Set.of(
            "minecraft:iron_sword", "minecraft:iron_pickaxe", "minecraft:iron_chestplate", "minecraft:golden_pickaxe",
            "minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:diamond")
            .stream().map(ResourceLocation::parse).collect(java.util.stream.Collectors.toSet())::contains;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("blueprintforge", path);
    }

    private static JsonElement shipped(String directory, String name) throws IOException {
        String resource = "data/blueprintforge/" + directory + "/" + name + ".json";
        try (InputStream in = ResearchDataLoaderTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(in != null, "missing shipped file " + resource);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static ResearchDataLoader.Result load(Map<String, String> tiers, Map<String, String> targets) {
        Map<ResourceLocation, JsonElement> tierJson = new TreeMap<>();
        tiers.forEach((k, v) -> tierJson.put(id(k), JsonParser.parseString(v)));
        Map<ResourceLocation, JsonElement> targetJson = new TreeMap<>();
        targets.forEach((k, v) -> targetJson.put(id(k), JsonParser.parseString(v)));
        return ResearchDataLoader.load(new ResearchDataLoader.RawData(tierJson, targetJson, new ArrayList<>()), JsonOps.INSTANCE, ITEMS);
    }

    private static String tier(String steps, String operations, String cost) {
        return """
                {"display": "T", "color": "#4A90E6", "steps": %s, "operations": %s, "bonus_multiplier": 1.1, "attempt_cost": %s}
                """.formatted(steps, operations, cost);
    }

    private static final String OK_STEPS = "{\"min\": 3, \"max\": 4}";
    private static final String OK_OPS = "[\"blueprintforge:press\", \"blueprintforge:blast\"]";
    private static final String NO_COST = "{\"deployers\": {\"min\": 0, \"max\": 0}}";

    private static boolean hasError(ResearchDataLoader.Result result, String... fragments) {
        return result.errors().stream().anyMatch(e -> java.util.Arrays.stream(fragments).allMatch(e::contains));
    }

    @Test
    void shippedReferencePackLoadsWithoutErrors() throws IOException {
        Map<String, String> tiers = new TreeMap<>();
        for (String name : new String[] {"t1", "t2", "t3"}) {
            tiers.put(name, shipped("research_tier", name).toString());
        }
        Map<String, String> targets = new TreeMap<>();
        for (String name : new String[] {"sword_t3", "pickaxe_t3", "chestplate_t3"}) {
            targets.put(name, shipped("research_target", name).toString());
        }
        ResearchDataLoader.Result result = load(tiers, targets);

        assertEquals(java.util.List.of(), result.errors());
        assertEquals(3, result.tiers().size());
        assertEquals(3, result.targets().size());
        assertEquals(ResearchType.CHESTPLATE, result.targets().get(id("chestplate_t3")).type());
    }

    @Test
    void shippedTiersGrowWithTheTier() throws IOException {
        Map<String, String> tiers = new TreeMap<>();
        for (String name : new String[] {"t1", "t2", "t3"}) {
            tiers.put(name, shipped("research_tier", name).toString());
        }
        ResearchDataLoader.Result result = load(tiers, Map.of());
        ResearchTier t1 = result.tiers().get(id("t1"));
        ResearchTier t2 = result.tiers().get(id("t2"));
        ResearchTier t3 = result.tiers().get(id("t3"));

        // Draft values (task 2 proposal: deployers T1 0-1, T2 1-2, T3 2-3); the test pins the shape, not the balance.
        assertEquals(new IntRange(0, 1), t1.attemptCost().deployers());
        assertEquals(new IntRange(1, 2), t2.attemptCost().deployers());
        assertEquals(new IntRange(2, 3), t3.attemptCost().deployers());
        assertTrue(t1.steps().max() <= t2.steps().max() && t2.steps().max() <= t3.steps().max());
        assertTrue(t1.bonusMultiplier() < t2.bonusMultiplier() && t2.bonusMultiplier() < t3.bonusMultiplier());
        assertEquals(java.util.List.of(ResourceLocation.parse("blueprintforge:press"), ResourceLocation.parse("blueprintforge:blast")), t3.operations());
    }

    @Test
    void shippedTiersCarryTheOfficialIconColour() throws IOException {
        Map<String, String> tiers = new TreeMap<>();
        for (String name : new String[] {"t1", "t2", "t3"}) {
            tiers.put(name, shipped("research_tier", name).toString());
        }
        ResearchDataLoader.Result result = load(tiers, Map.of());
        assertEquals(1, result.tiers().get(id("t1")).spriteTier());
        assertEquals(2, result.tiers().get(id("t2")).spriteTier());
        assertEquals(3, result.tiers().get(id("t3")).spriteTier());
    }

    @Test
    void spriteTierDefaultsToOneAndMustBeInRange() {
        ResearchDataLoader.Result result = load(Map.of(
                "plain", tier(OK_STEPS, OK_OPS, NO_COST),
                "gold", tier(OK_STEPS, OK_OPS, NO_COST).replace("\"bonus_multiplier\"", "\"sprite_tier\": 5, \"bonus_multiplier\""),
                "too_high", tier(OK_STEPS, OK_OPS, NO_COST).replace("\"bonus_multiplier\"", "\"sprite_tier\": 6, \"bonus_multiplier\"")), Map.of());
        assertEquals(1, result.tiers().get(id("plain")).spriteTier());
        assertEquals(5, result.tiers().get(id("gold")).spriteTier());
        assertFalse(result.tiers().containsKey(id("too_high")));
        assertTrue(hasError(result, "too_high"), result.errors().toString());
    }

    @Test
    void invalidTierIsSkippedAndTheRestLoads() {
        ResearchDataLoader.Result result = load(Map.of(
                "good", tier(OK_STEPS, OK_OPS, NO_COST),
                "reversed_steps", tier("{\"min\": 5, \"max\": 2}", OK_OPS, NO_COST),
                "zero_steps", tier("{\"min\": 0, \"max\": 2}", OK_OPS, NO_COST),
                "no_operations", tier(OK_STEPS, "[]", NO_COST),
                "duplicate_operations", tier(OK_STEPS, "[\"blueprintforge:press\", \"blueprintforge:press\"]", NO_COST),
                "deployers_without_pool", tier(OK_STEPS, OK_OPS, "{\"deployers\": {\"min\": 1, \"max\": 2}}"),
                "not_an_object", "[1, 2]",
                "missing_field", "{\"display\": \"T\"}"), Map.of());

        assertEquals(Set.of(id("good")), result.tiers().keySet());
        assertTrue(hasError(result, "reversed_steps", "greater than max"), result.errors().toString());
        assertTrue(hasError(result, "zero_steps", "steps.min"), result.errors().toString());
        assertTrue(hasError(result, "no_operations", "must not be empty"), result.errors().toString());
        assertTrue(hasError(result, "duplicate_operations", "duplicates"), result.errors().toString());
        assertTrue(hasError(result, "deployers_without_pool", "pool is empty"), result.errors().toString());
        assertTrue(hasError(result, "not_an_object"), result.errors().toString());
        assertTrue(hasError(result, "missing_field"), result.errors().toString());
        assertEquals(7, result.errors().size());
    }

    @Test
    void tierWithDeployersAndPoolIsAccepted() {
        ResearchDataLoader.Result result = load(Map.of("t", tier(OK_STEPS, OK_OPS,
                "{\"deployers\": {\"min\": 1, \"max\": 2}, \"pool\": [{\"item\": \"minecraft:iron_ingot\", \"count\": {\"min\": 2, \"max\": 4}}]}")), Map.of());
        assertEquals(java.util.List.of(), result.errors());
        assertEquals(1, result.tiers().get(id("t")).attemptCost().pool().size());
    }

    @Test
    void poolEntryMustAskForAtLeastOneItem() {
        ResearchDataLoader.Result result = load(Map.of("t", tier(OK_STEPS, OK_OPS,
                "{\"deployers\": {\"min\": 1, \"max\": 1}, \"pool\": [{\"item\": \"minecraft:iron_ingot\", \"count\": {\"min\": 0, \"max\": 4}}]}")), Map.of());
        assertTrue(result.tiers().isEmpty());
        assertTrue(hasError(result, "count.min"), result.errors().toString());
    }

    @Test
    void invalidTargetIsSkippedAndAMissingItemIsInactiveNotAnError() {
        ResearchDataLoader.Result result = load(Map.of("ok", tier(OK_STEPS, OK_OPS, NO_COST)), Map.of(
                "iron_sword", "{\"item\": \"minecraft:iron_sword\", \"type\": \"sword\", \"tier\": \"blueprintforge:ok\"}",
                "unknown_type", "{\"item\": \"minecraft:iron_pickaxe\", \"type\": \"wand\", \"tier\": \"blueprintforge:ok\"}",
                "no_item_field", "{\"type\": \"sword\", \"tier\": \"blueprintforge:ok\"}",
                "bad_item_id", "{\"item\": \"Not An Id\", \"type\": \"sword\", \"tier\": \"blueprintforge:ok\"}",
                "other_mod_item", "{\"item\": \"othermod:blade\", \"type\": \"sword\", \"tier\": \"blueprintforge:ok\"}"));

        assertEquals(Set.of(id("iron_sword")), result.targets().keySet());
        assertEquals(Set.of(id("other_mod_item")), result.inactiveTargets());
        assertTrue(hasError(result, "unknown_type"), result.errors().toString());
        assertTrue(hasError(result, "no_item_field"), result.errors().toString());
        assertTrue(hasError(result, "bad_item_id"), result.errors().toString());
        assertFalse(hasError(result, "other_mod_item"), "a missing item is a pack choice, not an error");
        assertEquals(3, result.errors().size());
    }

    @Test
    void aTargetOfAnUnknownTierIsSkippedWithAnError() {
        ResearchDataLoader.Result result = load(Map.of("ok", tier(OK_STEPS, OK_OPS, NO_COST)), Map.of(
                "good", "{\"item\": \"minecraft:iron_sword\", \"type\": \"sword\", \"tier\": \"blueprintforge:ok\"}",
                "orphan", "{\"item\": \"minecraft:iron_sword\", \"type\": \"sword\", \"tier\": \"blueprintforge:nope\"}",
                "no_tier", "{\"item\": \"minecraft:iron_sword\", \"type\": \"sword\"}"));
        assertEquals(Set.of(id("good")), result.targets().keySet());
        assertTrue(hasError(result, "orphan", "unknown tier"), result.errors().toString());
        assertTrue(hasError(result, "no_tier"), result.errors().toString());
    }

    @Test
    void aChainLongerThanSixteenStepsIsRefused() {
        ResearchDataLoader.Result result = load(Map.of(
                "sixteen", tier("{\"min\": 12, \"max\": 16}", OK_OPS, NO_COST),
                "seventeen", tier("{\"min\": 12, \"max\": 17}", OK_OPS, NO_COST)), Map.of());
        assertEquals(Set.of(id("sixteen")), result.tiers().keySet());
        assertTrue(hasError(result, "seventeen", "limit of 16"), result.errors().toString());
    }

    @Test
    void brokenSyntaxIsReportedByTheParserNotSwallowed() {
        assertThrows(RuntimeException.class, () -> ResearchDataLoader.parseJson(new StringReader("{\"item\": ")));
        assertThrows(RuntimeException.class, () -> ResearchDataLoader.parseJson(new StringReader("")));
    }

    @Test
    void everyIconTypeIsAcceptedAndNothingElse() {
        for (ResearchType type : ResearchType.values()) {
            ResearchDataLoader.Result result = load(Map.of("ok", tier(OK_STEPS, OK_OPS, NO_COST)),
                    Map.of("t", "{\"item\": \"minecraft:iron_sword\", \"type\": \"" + type.getSerializedName() + "\", \"tier\": \"blueprintforge:ok\"}"));
            assertEquals(type, result.targets().get(id("t")).type());
        }
        assertEquals(14, ResearchType.values().length);
    }
}
