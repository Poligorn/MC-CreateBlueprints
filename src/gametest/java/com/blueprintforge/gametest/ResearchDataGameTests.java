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
        for (String tier : List.of("t1", "t2", "t3")) {
            helper.assertTrue(ResearchRegistry.tier(id(tier)).isPresent(), "tier " + tier + " must load");
        }
        for (String target : List.of("iron_sword", "iron_pickaxe", "iron_chestplate")) {
            helper.assertTrue(ResearchRegistry.target(id(target)).isPresent(), "target " + target + " must load");
        }
        int t1 = ResearchRegistry.tier(id("t1")).orElseThrow().attemptCost().deployers().max();
        int t3 = ResearchRegistry.tier(id("t3")).orElseThrow().attemptCost().deployers().max();
        helper.assertTrue(t3 > t1, "a T3 attempt must ask for more deployers than T1");
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
