package com.blueprintforge.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

/**
 * Generator version 1 (task 8). The vectors were computed independently with a Python SplitMix64 that follows the
 * documented draw order: base length, base picks, [fix of an all-same base], deployer count, per deployer pool index and
 * count, then per deployer the insert position.
 */
class DeployerChainTest {
    private static final ResourceLocation PRESS = ResourceLocation.fromNamespaceAndPath("blueprintforge", "press");
    private static final ResourceLocation BLAST = ResourceLocation.fromNamespaceAndPath("blueprintforge", "blast");
    private static final List<ResourceLocation> OPS = List.of(PRESS, BLAST);

    private static final List<ResourceEntry> T1_POOL = List.of(entry("copper", 1, 2));
    private static final List<ResourceEntry> T3_POOL = List.of(entry("gold", 2, 4), entry("diamond", 1, 2));

    private static ResourceEntry entry(String item, int min, int max) {
        return new ResourceEntry(ResourceLocation.fromNamespaceAndPath("minecraft", item), new IntRange(min, max));
    }

    /** {@code P B D(gold*2)}: the press/blast letters and deployer steps with the item path and the count. */
    private static String text(List<ChainStep> chain) {
        return chain.stream().map(step -> step.isDeploy()
                ? "D(" + step.item().getPath() + "*" + step.count() + ")"
                : step.operation().equals(PRESS) ? "P" : "B").collect(Collectors.joining(" "));
    }

    private static List<ChainStep> t1(long seed, int version) {
        return ResearchChain.steps(seed, 3, 4, OPS, new IntRange(0, 1), T1_POOL, version);
    }

    private static List<ChainStep> t3(long seed, int version) {
        return ResearchChain.steps(seed, 6, 8, OPS, new IntRange(2, 3), T3_POOL, version);
    }

    @Test
    void newBlueprintsGetGeneratorVersionOne() {
        assertEquals(1, ResearchChain.GENERATOR_VERSION);
    }

    @Test
    void tierOneVectors() {
        Map<Long, String> expected = Map.of(0L, "P B P B", 1L, "B P B B", 42L, "B P P P", -1L, "B B P",
                1234567890123L, "D(copper*1) P B P");
        expected.forEach((seed, chain) -> assertEquals(chain, text(t1(seed, 1)), "seed " + seed));
    }

    @Test
    void tierThreeVectors() {
        Map<Long, String> expected = Map.of(
                0L, "P B D(diamond*2) P D(gold*2) D(gold*3) B P B P",
                1L, "B P B B D(diamond*1) P B B P D(gold*3)",
                42L, "B D(diamond*1) P D(gold*4) P P D(gold*4) P B P",
                -1L, "B B P P D(diamond*2) B D(diamond*1) B P P",
                1234567890123L, "P B D(diamond*2) D(gold*4) P B P D(diamond*1) P P");
        expected.forEach((seed, chain) -> assertEquals(chain, text(t3(seed, 1)), "seed " + seed));
    }

    @Test
    void theDeployerStepsComeOnTopOfTheBaseRange() {
        for (long seed = 0; seed < 500; seed++) {
            List<ChainStep> chain = t3(seed * 104729, 1);
            long deployers = chain.stream().filter(ChainStep::isDeploy).count();
            assertTrue(deployers >= 2 && deployers <= 3, "deployers " + deployers);
            assertTrue(chain.size() >= 8 && chain.size() <= 11, "length " + chain.size());
            assertTrue(chain.size() - deployers >= 6 && chain.size() - deployers <= 8);
        }
    }

    @Test
    void deployerResourcesStayInThePoolAndItsCountRanges() {
        for (long seed = 0; seed < 500; seed++) {
            for (ChainStep step : t3(seed * 31, 1)) {
                if (!step.isDeploy()) {
                    continue;
                }
                String item = step.item().getPath();
                assertTrue(item.equals("gold") && step.count() >= 2 && step.count() <= 4
                        || item.equals("diamond") && step.count() >= 1 && step.count() <= 2, step.key());
            }
        }
    }

    @Test
    void sameSeedAndVersionAlwaysGiveTheSameChainIncludingResources() {
        for (long seed = -50; seed < 50; seed++) {
            assertEquals(t3(seed, 1), t3(seed, 1));
            assertEquals(t3(seed, 1).stream().map(ChainStep::key).toList(), t3(seed, 1).stream().map(ChainStep::key).toList());
        }
    }

    @Test
    void versionZeroStaysPressAndBlastOnlyAndKeepsItsVectors() {
        // The v0 reference vectors of ResearchChainTest, now through the version 1 entry point with a deployer-rich tier.
        assertEquals("P B P B P B P", text(t3(0, 0)));
        assertEquals("B P B B P B B P", text(t3(1, 0)));
        for (long seed = -30; seed < 30; seed++) {
            assertTrue(t3(seed, 0).stream().noneMatch(ChainStep::isDeploy));
        }
    }

    @Test
    void versionOneKeepsThePressBlastPartOfVersionZeroInOrder() {
        for (long seed = -100; seed < 100; seed++) {
            List<ResourceLocation> v0 = ResearchChain.generate(seed, 6, 8, OPS, 0);
            List<ResourceLocation> v1 = t3(seed, 1).stream().filter(step -> !step.isDeploy()).map(ChainStep::operation).toList();
            assertEquals(v0, v1, "seed " + seed);
        }
    }

    @Test
    void thePressBlastPartIsNeverOneRepeatedOperation() {
        for (long seed = 0; seed < 2000; seed++) {
            long distinct = t1(seed * 7919, 1).stream().filter(step -> !step.isDeploy()).map(ChainStep::operation).distinct().count();
            assertTrue(distinct >= 2, "seed " + seed);
        }
    }

    @Test
    void theChainIsCutToSixteenAndTheLastDrawnDeployersGoFirst() {
        List<ResourceEntry> pool = List.of(entry("x", 1, 1));
        // Seed 0: 14 or 15 base steps plus three deployers cannot all fit.
        String[] expected = {
                "P B P B P B P B P B P B D(x*1) B B B",
                "B P B B P B B P P B P P D(x*1) P P B",
                "P B P B B P D(x*1) D(x*1) B B P B B B P B",
                "B D(x*1) B B P B P P P P P B P B P P"};
        for (int seed = 0; seed < expected.length; seed++) {
            List<ChainStep> chain = ResearchChain.steps(seed, 14, 15, OPS, new IntRange(3, 3), pool, 1);
            assertEquals(expected[seed], text(chain), "seed " + seed);
            assertEquals(16, chain.size());
        }
        for (long seed = 0; seed < 300; seed++) {
            assertTrue(ResearchChain.steps(seed, 16, 16, OPS, new IntRange(3, 3), pool, 1).stream().noneMatch(ChainStep::isDeploy),
                    "a full base chain leaves no room");
        }
    }

    @Test
    void aTierWithoutDeployersOrWithoutAPoolAddsNothing() {
        assertEquals(text(ResearchChain.steps(5, 3, 4, OPS, new IntRange(0, 0), List.of(), 1)),
                text(ResearchChain.steps(5, 3, 4, OPS, new IntRange(0, 0), T1_POOL, 1)));
        assertTrue(ResearchChain.steps(5, 3, 4, OPS, new IntRange(2, 2), List.of(), 1).stream().noneMatch(ChainStep::isDeploy));
    }

    @Test
    void anUnknownVersionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> t3(1, 2));
        assertThrows(IllegalArgumentException.class, () -> ResearchChain.generate(1, 3, 4, OPS, 1), "the pure v0 core is v0 only");
    }

    @Test
    void deployerKeysCompareByItemAndCount() {
        ResourceLocation gold = ResourceLocation.parse("minecraft:gold_ingot");
        ResourceLocation iron = ResourceLocation.parse("minecraft:iron_ingot");
        assertEquals("deploy:minecraft:gold_ingot*2", ChainStep.deploy(gold, 2).key());
        assertNotEquals(ChainStep.deploy(gold, 2).key(), ChainStep.deploy(iron, 2).key(), "wrong item is no match");
        assertNotEquals(ChainStep.deploy(gold, 2).key(), ChainStep.deploy(gold, 3).key(), "wrong count is no match");
        assertEquals("press", ChainStep.of(PRESS).key());
        RunScore score = RunScore.of(List.of("press", ChainStep.deploy(gold, 2).key()), List.of("press", ChainStep.deploy(iron, 2).key()));
        assertEquals(1, score.matched());
        assertEquals(50, score.percent());
    }

    // ---- shipped tiers ----

    private static Map<String, ResearchTier> shippedTiers() throws IOException {
        Map<ResourceLocation, JsonElement> tiers = new TreeMap<>();
        for (String name : new String[] {"t1", "t2", "t3", "t4", "t5"}) {
            String resource = "data/blueprintforge/research_tier/" + name + ".json";
            try (InputStream in = DeployerChainTest.class.getClassLoader().getResourceAsStream(resource)) {
                assertTrue(in != null, "missing " + resource);
                tiers.put(ResourceLocation.fromNamespaceAndPath("blueprintforge", name),
                        JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        ResearchDataLoader.Result result = ResearchDataLoader.load(
                new ResearchDataLoader.RawData(tiers, new TreeMap<>(), new ArrayList<>()), JsonOps.INSTANCE, id -> true);
        assertEquals(List.of(), result.errors());
        Map<String, ResearchTier> byName = new TreeMap<>();
        result.tiers().forEach((id, tier) -> byName.put(id.getPath(), tier));
        return byName;
    }

    @Test
    void shippedTiersInsertDeployersAccordingToTheirJson() throws IOException {
        Map<String, ResearchTier> tiers = shippedTiers();
        for (String name : new String[] {"t1", "t2", "t3", "t4", "t5"}) {
            ResearchTier tier = tiers.get(name);
            IntRange deployers = tier.attemptCost().deployers();
            for (long seed = 0; seed < 400; seed++) {
                List<ChainStep> chain = ResearchChain.steps(seed * 9973, tier, ResearchChain.GENERATOR_VERSION);
                long feeds = chain.stream().filter(ChainStep::isDeploy).count();
                assertTrue(chain.size() <= ResearchTier.MAX_STEPS, name + " is over 16");
                assertTrue(feeds <= deployers.max(), name + " has " + feeds + " deployers");
                if (name.equals("t1") || name.equals("t2") || name.equals("t3")) {
                    // For the three MVP tiers the cap never cuts anything.
                    assertTrue(feeds >= deployers.min(), name + " has " + feeds + " deployers");
                    assertTrue(chain.size() <= tier.steps().max() + deployers.max());
                }
                for (ChainStep step : chain) {
                    if (step.isDeploy()) {
                        ResourceEntry entry = tier.attemptCost().pool().stream().filter(e -> e.item().equals(step.item())).findFirst().orElseThrow();
                        assertTrue(step.count() >= entry.count().min() && step.count() <= entry.count().max(), name + " " + step.key());
                    }
                }
            }
        }
    }

    @Test
    void anAttemptOfTierThreeCostsClearlyMoreThanTierOne() throws IOException {
        Map<String, ResearchTier> tiers = shippedTiers();
        long t1Feeds = 0;
        long t3Feeds = 0;
        long t1Items = 0;
        long t3Items = 0;
        int runs = 1000;
        for (long seed = 0; seed < runs; seed++) {
            List<ChainStep> a = ResearchChain.steps(seed * 7, tiers.get("t1"), ResearchChain.GENERATOR_VERSION);
            List<ChainStep> b = ResearchChain.steps(seed * 7, tiers.get("t3"), ResearchChain.GENERATOR_VERSION);
            long aFeeds = a.stream().filter(ChainStep::isDeploy).count();
            long bFeeds = b.stream().filter(ChainStep::isDeploy).count();
            assertTrue(bFeeds >= 2 && bFeeds > aFeeds, "T3 always has more deployer steps than T1");
            t1Feeds += aFeeds;
            t3Feeds += bFeeds;
            t1Items += AttemptCost.totals(a).values().stream().mapToInt(Integer::intValue).sum();
            t3Items += AttemptCost.totals(b).values().stream().mapToInt(Integer::intValue).sum();
        }
        assertTrue(t3Feeds >= 2 * t1Feeds, "deployer steps: " + t1Feeds + " vs " + t3Feeds);
        assertTrue(t3Items >= 4 * t1Items, "items: " + t1Items + " vs " + t3Items);
        // The T3 pool is gold ingots and diamonds, the T1 pool is copper ingots.
        assertEquals(List.of(ResourceLocation.parse("minecraft:copper_ingot")),
                tiers.get("t1").attemptCost().pool().stream().map(ResourceEntry::item).toList());
        assertTrue(tiers.get("t3").attemptCost().pool().stream().anyMatch(e -> e.item().equals(ResourceLocation.parse("minecraft:diamond"))));
    }

    @Test
    void totalsAreSortedByItemSoTheStepOrderDoesNotLeak() {
        ResourceLocation gold = ResourceLocation.parse("minecraft:gold_ingot");
        ResourceLocation copper = ResourceLocation.parse("minecraft:copper_ingot");
        List<ChainStep> a = List.of(ChainStep.deploy(gold, 2), ChainStep.of(PRESS), ChainStep.deploy(copper, 1), ChainStep.deploy(gold, 3));
        List<ChainStep> b = List.of(ChainStep.deploy(copper, 1), ChainStep.deploy(gold, 3), ChainStep.of(BLAST), ChainStep.deploy(gold, 2));
        assertEquals(List.of(copper, gold), List.copyOf(AttemptCost.totals(a).keySet()));
        assertEquals(AttemptCost.totals(a), AttemptCost.totals(b));
        assertEquals(5, AttemptCost.totals(a).get(gold));
    }

    @Test
    void aStoredBlueprintWithoutAVersionStaysOnVersionZeroAndNewOnesAreWrittenAsVersionOne() {
        ResourceLocation tier = ResourceLocation.fromNamespaceAndPath("blueprintforge", "t1");
        ResourceLocation target = ResourceLocation.fromNamespaceAndPath("blueprintforge", "sword_t1");
        ResourceLocation item = ResourceLocation.parse("minecraft:wooden_sword");
        ResearchBlueprint old = new ResearchBlueprint(tier, target, item, ResearchType.SWORD, 1, false, java.util.Optional.of(7L), 0);
        com.google.gson.JsonElement oldJson = ResearchBlueprint.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow();
        assertEquals(0, ResearchBlueprint.CODEC.parse(JsonOps.INSTANCE, oldJson).getOrThrow().generatorVersion());
        // An item stored before the field existed has no such key at all.
        com.google.gson.JsonObject legacy = oldJson.getAsJsonObject().deepCopy();
        legacy.remove("generator_version");
        assertEquals(0, ResearchBlueprint.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow().generatorVersion());

        ResearchBlueprint fresh = old.withGeneratorVersion(ResearchChain.GENERATOR_VERSION);
        com.google.gson.JsonElement freshJson = ResearchBlueprint.CODEC.encodeStart(JsonOps.INSTANCE, fresh).getOrThrow();
        assertEquals(1, freshJson.getAsJsonObject().get("generator_version").getAsInt());
        assertEquals(1, ResearchBlueprint.CODEC.parse(JsonOps.INSTANCE, freshJson).getOrThrow().generatorVersion());
    }
}
