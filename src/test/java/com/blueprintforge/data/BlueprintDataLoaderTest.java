package com.blueprintforge.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class BlueprintDataLoaderTest {
    private static final ResourceLocation TIERS_FILE = id("tiers");
    private static final Predicate<ResourceLocation> ITEMS = Set.of(
            ResourceLocation.withDefaultNamespace("iron_sword"),
            ResourceLocation.withDefaultNamespace("iron_pickaxe"))::contains;

    private static final String TIERS = """
            {
              "blueprintforge:tier1": {"display": {"translate": "tier.blueprintforge.tier1"}, "color": "#9E9E9E",
                "requires_blueprint": false, "global_durability_multiplier": 1.0, "fitting_slots": 0},
              "blueprintforge:tier2": {"display": {"translate": "tier.blueprintforge.tier2"}, "color": "#4CAF50",
                "requires_blueprint": true, "global_durability_multiplier": 1.1, "fitting_slots": 0},
              "blueprintforge:tier3": {"display": "Broken", "color": "green",
                "requires_blueprint": true, "global_durability_multiplier": 1.25, "fitting_slots": 1}
            }
            """;

    private static final String GUILD_BLADE = """
            {
              "tier": 2, "class": "original", "target": "minecraft:iron_sword",
              "output": {"attributes": [{"attribute": "minecraft:generic.attack_damage", "mode": "multiply_total", "value": 0.15}]},
              "material_efficiency": {"min": 0, "max": 30, "step": 3},
              "time_efficiency": {"min": 0, "max": 40, "step": 5},
              "copy": {"default_runs": 10, "max_runs": 50, "copy_cost": [{"item": "minecraft:paper", "count": 8},
                {"fluid": "minecraft:water", "amount": 250}], "cost_scaling": 1.35},
              "display": {"name": {"translate": "blueprint.blueprintforge.guild_blade"}},
              "tags": ["blueprintforge:weapons", "blueprintforge:tier2"]
            }
            """;

    private static final String CHEST_SOURCE = """
            {"blueprint": "blueprintforge:guild_blade", "weight": 1,
             "sources": [{"type": "loot_injection", "target_tables": ["minecraft:chests/abandoned_mineshaft"], "add_entries": 1, "chance": 0.05}]}
            """;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("blueprintforge", path);
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static BlueprintDataLoader.Result load(Map<ResourceLocation, String> blueprints, Map<ResourceLocation, String> sources) {
        Map<ResourceLocation, JsonElement> blueprintJson = new TreeMap<>();
        blueprints.forEach((k, v) -> blueprintJson.put(k, json(v)));
        Map<ResourceLocation, JsonElement> sourceJson = new TreeMap<>();
        sources.forEach((k, v) -> sourceJson.put(k, json(v)));
        BlueprintDataLoader.RawData raw = new BlueprintDataLoader.RawData(
                Map.of(TIERS_FILE, json(TIERS)), blueprintJson, sourceJson, new ArrayList<>());
        return BlueprintDataLoader.load(raw, JsonOps.INSTANCE, ITEMS);
    }

    private static boolean hasError(BlueprintDataLoader.Result result, String... fragments) {
        return result.errors().stream().anyMatch(error -> List.of(fragments).stream().allMatch(error::contains));
    }

    @Test
    void referencePackLoads() {
        BlueprintDataLoader.Result result = load(Map.of(id("guild_blade"), GUILD_BLADE), Map.of(id("guild_blade_chests"), CHEST_SOURCE));

        BlueprintDefinition blade = result.blueprints().get(id("guild_blade"));
        assertEquals(id("tier2"), blade.tier());
        assertEquals(BlueprintClass.ORIGINAL, blade.clazz());
        assertEquals(30, blade.materialEfficiencyOrFixed().max());
        assertEquals(OutputModifier.Mode.MULTIPLY_TOTAL, blade.output().attributes().getFirst().mode());
        assertEquals(10, blade.copy().orElseThrow().mePenalty());
        assertTrue(blade.remake().isEmpty());
        assertTrue(blade.copy().orElseThrow().copyCost().get(1).itemOrFluid().right().isPresent());

        SourceDefinition source = result.sources().get(id("guild_blade_chests"));
        assertEquals(0.05F, source.lootInjections().getFirst().chance());
    }

    @Test
    void brokenTierEntryIsSkippedAndOthersLoad() {
        BlueprintDataLoader.Result result = load(Map.of(), Map.of());
        assertEquals(Set.of(id("tier1"), id("tier2")), result.tiers().keySet());
        assertTrue(hasError(result, "data/blueprintforge/tier/tiers.json#blueprintforge:tier3", "#RRGGBB"));
        assertEquals(0x4CAF50, result.tiers().get(id("tier2")).color());
    }

    @Test
    void brokenBlueprintIsSkippedAndTheRestOfThePackLoads() {
        String badClass = GUILD_BLADE.replace("\"original\"", "\"legendary\"");
        BlueprintDataLoader.Result result = load(
                Map.of(id("guild_blade"), GUILD_BLADE, id("broken"), badClass),
                Map.of(id("guild_blade_chests"), CHEST_SOURCE));

        assertTrue(result.blueprints().containsKey(id("guild_blade")));
        assertFalse(result.blueprints().containsKey(id("broken")));
        assertTrue(hasError(result, "data/blueprintforge/blueprint/broken.json"));
        assertTrue(result.sources().containsKey(id("guild_blade_chests")));
    }

    @Test
    void unknownTierIsAnError() {
        BlueprintDataLoader.Result result = load(Map.of(id("t4"), GUILD_BLADE.replace("\"tier\": 2", "\"tier\": 4")), Map.of());
        assertTrue(result.blueprints().isEmpty());
        assertTrue(hasError(result, "blueprint/t4.json", "unknown tier blueprintforge:tier4"));
    }

    @Test
    void schemaRulesPerClass() {
        String originalWithoutTarget = GUILD_BLADE.replace("\"target\": \"minecraft:iron_sword\",", "");
        String fragmentWithCopy = GUILD_BLADE.replace("\"original\"", "\"fragment\"");
        String fragment = """
                {"tier": 2, "class": "fragment", "display": {"name": "Scrap"}, "tags": []}
                """;
        BlueprintDataLoader.Result result = load(Map.of(
                id("no_target"), originalWithoutTarget,
                id("fragment_copy"), fragmentWithCopy,
                id("fragment"), fragment), Map.of());

        assertTrue(hasError(result, "no_target.json", "'target' is required"));
        assertTrue(hasError(result, "fragment_copy.json", "'copy' is not allowed"));
        assertEquals(Set.of(id("fragment")), result.blueprints().keySet());
        assertEquals(0, result.blueprints().get(id("fragment")).materialEfficiencyOrFixed().max());
    }

    @Test
    void remakeProcessingTimeLoadsAndRejectsZero() {
        String withRemake = GUILD_BLADE.replace("\"copy\":", "\"remake\": {\"processing_time\": 100}, \"copy\":");
        BlueprintDefinition blade = load(Map.of(id("guild_blade"), withRemake), Map.of()).blueprints().get(id("guild_blade"));
        assertEquals(100, blade.remake().orElseThrow().processingTime());

        String zero = withRemake.replace("100", "0");
        BlueprintDataLoader.Result rejected = load(Map.of(id("zero"), zero), Map.of());
        assertTrue(rejected.blueprints().isEmpty());
        assertTrue(hasError(rejected, "zero.json"));
    }

    @Test
    void invalidRangesAreRejected() {
        String badRange = GUILD_BLADE.replace("{\"min\": 0, \"max\": 30, \"step\": 3}", "{\"min\": 40, \"max\": 30, \"step\": 3}");
        String badRuns = GUILD_BLADE.replace("\"default_runs\": 10", "\"default_runs\": 60");
        BlueprintDataLoader.Result result = load(Map.of(id("range"), badRange, id("runs"), badRuns), Map.of());
        assertTrue(result.blueprints().isEmpty());
        assertTrue(hasError(result, "range.json", "greater than max"));
        assertTrue(hasError(result, "runs.json", "greater than max_runs"));
    }

    @Test
    void missingTargetItemDeactivatesSilently() {
        String modded = GUILD_BLADE.replace("minecraft:iron_sword", "othermod:steel_sword");
        String source = CHEST_SOURCE.replace("guild_blade", "modded");
        BlueprintDataLoader.Result result = load(Map.of(id("modded"), modded), Map.of(id("modded_chests"), source));
        assertTrue(result.blueprints().isEmpty());
        assertEquals(Set.of(id("modded")), result.inactiveBlueprints());
        assertTrue(result.sources().isEmpty());
        assertTrue(result.errors().stream().noneMatch(e -> e.contains("modded")), result.errors().toString());
    }

    @Test
    void unsupportedSourceTypesAreSkippedWithOneErrorPerFile() {
        String mixed = """
                {"blueprint": "blueprintforge:guild_blade",
                 "sources": [
                   {"type": "mob_drop", "entities": ["minecraft:zombie"], "chance": 0.1, "requires_player_kill": true},
                   {"type": "teleporter"},
                   {"type": "loot_injection", "target_tables": ["minecraft:chests/pillager_outpost"], "add_entries": 2, "chance": 0.5}
                 ]}
                """;
        BlueprintDataLoader.Result result = load(Map.of(id("guild_blade"), GUILD_BLADE), Map.of(id("mixed"), mixed));

        SourceDefinition source = result.sources().get(id("mixed"));
        assertEquals(1, source.lootInjections().size());
        assertEquals(2, source.lootInjections().getFirst().addEntries());
        assertEquals(1, result.errors().stream().filter(e -> e.contains("blueprint_source/mixed.json")).count());
        assertTrue(hasError(result, "mob_drop", "teleporter"));
    }

    @Test
    void sourceForUnknownBlueprintIsAnError() {
        BlueprintDataLoader.Result result = load(Map.of(), Map.of(id("orphan"), CHEST_SOURCE));
        assertTrue(result.sources().isEmpty());
        assertTrue(hasError(result, "orphan.json", "unknown blueprint blueprintforge:guild_blade"));
    }

    @Test
    void brokenJsonSyntaxIsReportedByTheReader() {
        assertThrows(RuntimeException.class, () -> BlueprintDataLoader.parseJson(new StringReader("{\"tier\": 2,, }")));
        assertThrows(RuntimeException.class, () -> BlueprintDataLoader.parseJson(new StringReader("")));
        assertTrue(BlueprintDataLoader.parseJson(new StringReader(GUILD_BLADE)).isJsonObject());
    }

    @Test
    void blueprintComponentRoundTrips() {
        BlueprintData data = new BlueprintData(UUID.randomUUID(), id("guild_blade"), BlueprintClass.COPY, id("tier2"),
                Optional.of(ResourceLocation.withDefaultNamespace("iron_sword")), 7, 20, 30,
                Optional.of(UUID.randomUUID()), Optional.of("Engineer"), Optional.empty(), Optional.empty(),
                Optional.of(UUID.randomUUID()), Optional.of("Owner"), Optional.of(new CompoundTag()));
        JsonElement encoded = BlueprintData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(data, BlueprintData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());

        ForgedItemData forged = new ForgedItemData(id("guild_blade"), id("tier2"), List.of(new OutputModifier(
                ResourceLocation.withDefaultNamespace("generic.attack_damage"), OutputModifier.Mode.MULTIPLY_TOTAL, 0.15, Optional.empty())));
        JsonElement forgedJson = ForgedItemData.CODEC.encodeStart(JsonOps.INSTANCE, forged).getOrThrow();
        assertEquals(forged, ForgedItemData.CODEC.parse(JsonOps.INSTANCE, forgedJson).getOrThrow());
    }

    @Test
    void issuingATemplateSetsANewInstanceAndOwnerOnly() {
        BlueprintData template = new BlueprintData(BlueprintData.UNISSUED, id("guild_blade"), BlueprintClass.ORIGINAL, id("tier2"),
                Optional.of(ResourceLocation.withDefaultNamespace("iron_sword")), -1, 0, 0,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertTrue(template.isUnissued());

        UUID instance = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        BlueprintData issued = template.issuedTo(instance, Optional.of(owner), Optional.of("Dev"));
        assertFalse(issued.isUnissued());
        assertEquals(instance, issued.instanceId());
        assertEquals(Optional.of(owner), issued.ownerUuid());
        assertEquals(Optional.of("Dev"), issued.ownerName());
        assertEquals(template, issued.issuedTo(BlueprintData.UNISSUED, Optional.empty(), Optional.empty()));
    }

    @Test
    void definitionsSurviveTheClientSyncEncoding() {
        BlueprintDefinition blade = load(Map.of(id("guild_blade"), GUILD_BLADE), Map.of()).blueprints().get(id("guild_blade"));
        JsonElement encoded = BlueprintDefinition.CODEC.encodeStart(JsonOps.INSTANCE, blade).getOrThrow();
        assertEquals(blade, BlueprintDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
}
