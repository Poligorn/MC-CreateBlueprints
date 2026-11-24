package com.blueprintforge.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.item.ResearchBlueprintItem;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFCreativeTabs;
import com.blueprintforge.registry.BFItems;
import com.blueprintforge.research.ResearchBlueprint;
import com.blueprintforge.research.ResearchRegistry;
import com.blueprintforge.research.SeedAssigner;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The creative tab lists every blueprint, and a blueprint gets its seed when it reaches an inventory. */
@GameTestHolder(BlueprintForge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlueprintInventoryGameTests {
    private static final String EMPTY = "empty";

    private BlueprintInventoryGameTests() {
    }

    private static ItemStack blueprint(boolean copy) {
        ResourceLocation tierId = BlueprintForge.id("t3");
        ResourceLocation targetId = BlueprintForge.id("pickaxe_t3");
        return ResearchBlueprintItem.create(tierId, ResearchRegistry.tier(tierId).orElseThrow(), targetId, ResearchRegistry.target(targetId).orElseThrow(), copy);
    }

    private static ResearchBlueprint data(ItemStack stack) {
        return stack.get(BFComponents.RESEARCH_BLUEPRINT.get());
    }

    @GameTest(template = EMPTY)
    public static void aBlueprintInAnInventoryGetsItsOwnSeedOnce(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().add(blueprint(false));
        player.getInventory().add(blueprint(true));
        player.getInventory().add(blueprint(false));
        player.getInventory().add(new ItemStack(Items.IRON_PICKAXE));

        // The inventory holds its own copies of what was added, so read the stacks back from it.
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            stacks.add(player.getInventory().getItem(slot));
        }
        List<ItemStack> blueprints = stacks.stream().filter(stack -> stack.is(BFItems.RESEARCH_BLUEPRINT.get())).toList();
        helper.assertTrue(blueprints.size() == 3, "three blueprints are in the inventory, got " + blueprints.size());
        helper.assertTrue(blueprints.stream().allMatch(stack -> data(stack).seed().isEmpty()), "none has a seed before");

        helper.assertTrue(SeedAssigner.assignAll(stacks, player.getRandom()) == 3, "three blueprints, three seeds");
        Set<Long> seeds = new HashSet<>();
        for (ItemStack stack : blueprints) {
            helper.assertTrue(data(stack).seed().isPresent(), "originals and copies both get a seed");
            seeds.add(data(stack).seed().get());
        }
        helper.assertTrue(blueprints.stream().anyMatch(stack -> data(stack).copy()), "a copy was among them");
        helper.assertTrue(seeds.size() == 3, "every blueprint has its own seed");

        long before = data(blueprints.getFirst()).seed().get();
        helper.assertTrue(SeedAssigner.assignAll(stacks, player.getRandom()) == 0, "a second pass changes nothing");
        helper.assertTrue(data(blueprints.getFirst()).seed().get() == before, "the seed of a blueprint never changes");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theCreativeTabListsAnOriginalAndACopyForEveryTierAndTarget(GameTestHelper helper) {
        CreativeModeTab tab = BFCreativeTabs.MAIN.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, false, helper.getLevel().registryAccess()));
        List<ItemStack> blueprints = tab.getDisplayItems().stream().filter(stack -> stack.is(BFItems.RESEARCH_BLUEPRINT.get())).toList();

        // A target belongs to one tier: an original and a copy for each of them.
        int expected = ResearchRegistry.targets().size() * 2;
        helper.assertTrue(ResearchRegistry.targets().size() == 70, "14 types x 5 tiers = 70 targets, got " + ResearchRegistry.targets().size());
        helper.assertTrue(blueprints.size() == expected, "expected " + expected + " blueprints, got " + blueprints.size());
        helper.assertTrue(blueprints.stream().filter(stack -> data(stack).copy()).count() == expected / 2, "half of them are copies");
        helper.assertTrue(blueprints.stream().anyMatch(stack -> data(stack).spriteTier() == 5), "tier 5 is listed");
        for (com.blueprintforge.research.ResearchType type : com.blueprintforge.research.ResearchType.values()) {
            helper.assertTrue(blueprints.stream().filter(stack -> data(stack).symbol() == type).count() == 10, type + " must have 5 tiers of original and copy");
        }
        helper.assertTrue(blueprints.stream().allMatch(stack -> data(stack).seed().isEmpty()), "tab stacks carry no seed yet");
        helper.assertTrue(tab.getDisplayItems().stream().anyMatch(stack -> stack.is(BFItems.TERMINAL.get())), "the terminal is listed");
        helper.succeed();
    }
}
