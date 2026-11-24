package com.blueprintforge.gametest;

import java.util.List;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.research.ResearchDataLoader;
import com.blueprintforge.research.ResearchRegistry;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Task 2: tiers and targets come from the real datapack, and broken files do not take the server down. */
@GameTestHolder(BlueprintForge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResearchDataGameTests {
    private static final String EMPTY = "empty";

    private ResearchDataGameTests() {
    }

    private static ResourceLocation id(String path) {
        return BlueprintForge.id(path);
    }

    private static ResourceLocation testId(String path) {
        return ResourceLocation.fromNamespaceAndPath("blueprintforge_test", path);
    }

    @GameTest(template = EMPTY)
    public static void researchTiersAndTargetsLoadFromTheDatapack(GameTestHelper helper) {
        for (String tier : List.of("t1", "t2", "t3", "t4", "t5")) {
            helper.assertTrue(ResearchRegistry.tier(id(tier)).isPresent(), "tier " + tier + " must load");
        }
        for (String target : List.of("sword_t1", "pickaxe_t3", "chestplate_t5", "helmet_t1", "bow_t2", "mace_t5")) {
            helper.assertTrue(ResearchRegistry.target(id(target)).isPresent(), "target " + target + " must load");
        }
        int t1 = ResearchRegistry.tier(id("t1")).orElseThrow().attemptCost().deployers().max();
        int t3 = ResearchRegistry.tier(id("t3")).orElseThrow().attemptCost().deployers().max();
        helper.assertTrue(t3 > t1, "a T3 attempt must ask for more deployers than T1");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyTypeHasATargetForEveryTierWithTheMaterialOfThatTier(GameTestHelper helper) {
        String[] pickaxes = {"wooden", "golden", "iron", "diamond", "netherite"};
        for (int tier = 1; tier <= 5; tier++) {
            var target = ResearchRegistry.target(id("pickaxe_t" + tier)).orElseThrow(() -> new IllegalStateException("missing pickaxe target"));
            helper.assertTrue(target.item().getPath().equals(pickaxes[tier - 1] + "_pickaxe"), "tier " + tier + " pickaxe is " + target.item());
            helper.assertTrue(target.tier().equals(id("t" + tier)), "the target names its tier");
        }
        helper.assertTrue(ResearchRegistry.target(id("chestplate_t1")).orElseThrow().item().getPath().equals("leather_chestplate"), "tier 1 armor is leather");
        helper.assertTrue(ResearchRegistry.targets().size() == 70, "70 targets, got " + ResearchRegistry.targets().size());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void brokenResearchFilesAreReportedAndSkippedWhileTheRestLoads(GameTestHelper helper) {
        List<String> errors = ResearchDataLoader.lastErrors();
        helper.assertTrue(errors.stream().anyMatch(e -> e.contains("blueprintforge_test/research_tier/broken_syntax.json")),
                "syntax error must be reported: " + errors);
        helper.assertTrue(errors.stream().anyMatch(e -> e.contains("blueprintforge_test/research_tier/reversed_steps.json")),
                "invalid steps must be reported: " + errors);
        helper.assertTrue(errors.stream().anyMatch(e -> e.contains("blueprintforge_test/research_target/unknown_type.json")),
                "unknown type must be reported: " + errors);

        helper.assertTrue(errors.stream().anyMatch(e -> e.contains("blueprintforge_test/research_tier/too_long.json") && e.contains("limit of 16")),
                "a chain over 16 steps must be reported: " + errors);
        helper.assertTrue(errors.stream().anyMatch(e -> e.contains("blueprintforge_test/research_target/unknown_tier.json") && e.contains("unknown tier")),
                "a target of an unknown tier must be reported: " + errors);
        helper.assertFalse(ResearchRegistry.tier(testId("too_long")).isPresent(), "a tier over the step limit must be skipped");
        helper.assertFalse(ResearchRegistry.target(testId("unknown_tier")).isPresent(), "a target of an unknown tier must be skipped");
        helper.assertFalse(ResearchRegistry.tier(testId("broken_syntax")).isPresent(), "broken tier must be skipped");
        helper.assertFalse(ResearchRegistry.tier(testId("reversed_steps")).isPresent(), "invalid tier must be skipped");
        helper.assertFalse(ResearchRegistry.target(testId("unknown_type")).isPresent(), "invalid target must be skipped");
        helper.assertFalse(ResearchRegistry.target(testId("missing_item")).isPresent(), "target of a missing item must be inactive");
        helper.assertTrue(errors.stream().noneMatch(e -> e.contains("missing_item")), "a missing item is not an error: " + errors);
        helper.assertFalse(ResearchRegistry.tier(id("no_such_tier")).isPresent(), "an unknown tier id is simply absent");
        helper.assertTrue(ResearchRegistry.tier(id("t1")).isPresent(), "valid files must survive the broken ones");
        helper.succeed();
    }
}
