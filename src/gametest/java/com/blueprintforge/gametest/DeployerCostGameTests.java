package com.blueprintforge.gametest;

import java.util.ArrayList;
import java.util.List;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.compat.createapi.SpikeLine;
import com.blueprintforge.item.ResearchBlueprintItem;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.research.AttemptCost;
import com.blueprintforge.research.ChainStep;
import com.blueprintforge.research.ResearchBlueprint;
import com.blueprintforge.research.ResearchChain;
import com.blueprintforge.research.ResearchRegistry;
import com.blueprintforge.research.ResearchTier;
import com.blueprintforge.terminal.TerminalBlock;
import com.blueprintforge.terminal.TerminalBlockEntity;
import com.blueprintforge.terminal.TerminalSlots;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Task 8: deployer steps cost resources. The scenes use a real Create belt and a real deployer; the terminal issues the
 * workpiece. The press and fan steps of the chain are not rebuilt here (the spike tests cover them): where a test needs
 * a full chain it appends those operations to the log by hand.
 */
@GameTestHolder(BlueprintForge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeployerCostGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos TERMINAL = new BlockPos(2, 1, 0);
    private static final BlockPos BELT_START = new BlockPos(0, 1, 2);
    private static final BlockPos BELT_END = new BlockPos(4, 1, 2);
    private static final BlockPos DEPLOYER = new BlockPos(2, 3, 2);
    private static final ResourceLocation T1 = BlueprintForge.id("t1");
    private static final ResourceLocation T3 = BlueprintForge.id("t3");
    private static final ResourceLocation PICKAXE = BlueprintForge.id("pickaxe_t3");

    private DeployerCostGameTests() {
    }

    /** A T3 blueprint whose chain starts with a deployer step, so the first machine on the line is the deployer. */
    private static ResearchBlueprint blueprintStartingWithADeployer() {
        ResearchTier tier = ResearchRegistry.tier(T3).orElseThrow();
        for (long seed = 1; seed < 100000; seed++) {
            if (ResearchChain.steps(seed, tier, ResearchChain.GENERATOR_VERSION).getFirst().isDeploy()) {
                ItemStack stack = ResearchBlueprintItem.create(T3, tier, PICKAXE, ResearchRegistry.target(PICKAXE).orElseThrow(), false);
                return stack.get(BFComponents.RESEARCH_BLUEPRINT.get()).withSeed(seed);
            }
        }
        throw new IllegalStateException("no seed starts with a deployer");
    }

    private static TerminalBlockEntity terminalWith(GameTestHelper helper, ResearchBlueprint blueprint) {
        helper.setBlock(TERMINAL, BFBlocks.TERMINAL.get().defaultBlockState().setValue(TerminalBlock.FACING, Direction.NORTH));
        TerminalBlockEntity be = helper.getBlockEntity(TERMINAL);
        ItemStack stack = new ItemStack(com.blueprintforge.registry.BFItems.RESEARCH_BLUEPRINT.get());
        stack.set(BFComponents.RESEARCH_BLUEPRINT.get(), blueprint);
        helper.assertTrue(be.inventory().insertItem(TerminalSlots.BLUEPRINT, stack, false).isEmpty(), "the blueprint must fit");
        return be;
    }

    private static ItemStack issueWorkpiece(GameTestHelper helper, TerminalBlockEntity be) {
        helper.assertTrue(be.inventory().insertItem(TerminalSlots.TARGET, new ItemStack(Items.IRON_PICKAXE), false).isEmpty(), "the target must fit");
        helper.assertTrue(be.createWorkpiece() == null, "the workpiece must be issued");
        return be.inventory().extractItem(TerminalSlots.OUTPUT, 1, false);
    }

    @GameTest(template = EMPTY)
    public static void terminalCountsDeployersAndShowsAPriceWithoutAnOrder(GameTestHelper helper) {
        ResearchBlueprint blueprint = blueprintStartingWithADeployer();
        TerminalBlockEntity be = terminalWith(helper, blueprint);
        List<ChainStep> chain = be.steps();
        int deployers = (int) chain.stream().filter(ChainStep::isDeploy).count();
        helper.assertTrue(deployers >= 2, "a T3 chain has at least two deployer steps, got " + deployers);
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_DEPLOY) == deployers, "the menu data counts the deployer steps");
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_STEPS) == chain.size(), "the total includes them");
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_PRESS) + be.data.get(TerminalBlockEntity.DATA_BLAST)
                + be.data.get(TerminalBlockEntity.DATA_DEPLOY) + be.data.get(TerminalBlockEntity.DATA_OTHER) == chain.size(), "counts add up");
        // The price per item matches the totals and is listed by item id.
        var totals = AttemptCost.totals(chain);
        int slot = 0;
        for (var total : totals.entrySet()) {
            int rawId = be.data.get(TerminalBlockEntity.DATA_COST + 2 * slot);
            helper.assertTrue(BuiltInRegistries.ITEM.byId(rawId) == BuiltInRegistries.ITEM.get(total.getKey()), "cost item " + slot);
            helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_COST + 2 * slot + 1) == total.getValue(), "cost count " + slot);
            slot++;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aTierThreeBlueprintAsksForMoreThanATierOneBlueprint(GameTestHelper helper) {
        ResearchTier t1 = ResearchRegistry.tier(T1).orElseThrow();
        ResearchTier t3 = ResearchRegistry.tier(T3).orElseThrow();
        int items1 = 0;
        int items3 = 0;
        for (long seed = 0; seed < 300; seed++) {
            items1 += AttemptCost.totals(ResearchChain.steps(seed, t1, ResearchChain.GENERATOR_VERSION)).values().stream().mapToInt(Integer::intValue).sum();
            items3 += AttemptCost.totals(ResearchChain.steps(seed, t3, ResearchChain.GENERATOR_VERSION)).values().stream().mapToInt(Integer::intValue).sum();
        }
        helper.assertTrue(items3 >= 4 * items1, "the datapack tiers: T1 " + items1 + " items, T3 " + items3 + " items over 300 seeds");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aDeployerStepScoresByItemAndCount(GameTestHelper helper) {
        TerminalBlockEntity be = terminalWith(helper, blueprintStartingWithADeployer());
        List<String> keys = be.steps().stream().map(ChainStep::key).toList();
        int firstDeploy = 0;
        ChainStep step = be.steps().get(firstDeploy);
        helper.assertTrue(step.isDeploy(), "the chosen seed starts with a deployer step");

        ItemStack workpiece = issueWorkpiece(helper, be);
        ItemStack exact = workpiece;
        for (String key : keys) {
            exact = SpikeLine.withOperation(exact, key);
        }
        be.inventory().insertItem(TerminalSlots.RETURN, exact, false);
        helper.assertTrue(be.checkRun() == null, "the run is checked");
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_PERCENT) == 100, "the exact keys give 100%");
        be.inventory().extractItem(TerminalSlots.OUTPUT, 1, false);

        List<String> wrongItem = new ArrayList<>(keys);
        wrongItem.set(firstDeploy, ChainStep.deployKey(ResourceLocation.parse("minecraft:stick"), step.count()));
        List<String> wrongCount = new ArrayList<>(keys);
        wrongCount.set(firstDeploy, ChainStep.deployKey(step.item(), step.count() + 1));
        for (List<String> run : List.of(wrongItem, wrongCount)) {
            helper.assertTrue(be.inventory().insertItem(TerminalSlots.TARGET, new ItemStack(Items.IRON_PICKAXE), false).isEmpty(), "target");
            helper.assertTrue(be.createWorkpiece() == null, "workpiece");
            ItemStack out = be.inventory().extractItem(TerminalSlots.OUTPUT, 1, false);
            for (String key : run) {
                out = SpikeLine.withOperation(out, key);
            }
            be.inventory().insertItem(TerminalSlots.RETURN, out, false);
            helper.assertTrue(be.checkRun() == null, "checked");
            helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_PERCENT) < 100, "a wrong deployer step is not a match: " + run);
            helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_MATCHED) == keys.size() - 1, "only that position is lost");
            helper.assertTrue(be.inventory().extractItem(TerminalSlots.OUTPUT, 1, false).is(com.blueprintforge.registry.BFItems.SCRAP.get()), "scrap");
        }
        helper.succeed();
    }

    // ---- a real deployer on a real belt ----

    private static void beltAndDeployer(GameTestHelper helper) {
        BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        helper.setBlock(BELT_START, shaft);
        helper.setBlock(BELT_END, shaft);
        BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(BELT_START), helper.absolutePos(BELT_END));
        helper.setBlock(BELT_START.north(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        BlockState deployer = AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN);
        helper.setBlock(DEPLOYER, deployer);
        Direction.Axis axis = AllBlocks.DEPLOYER.get().getRotationAxis(deployer);
        Direction toDeployer = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        helper.setBlock(DEPLOYER.relative(toDeployer.getOpposite()),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, toDeployer));
    }

    private static void putOnBelt(GameTestHelper helper, ItemStack stack) {
        BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
        helper.assertTrue(controller != null, "belt controller missing");
        TransportedItemStack item = new TransportedItemStack(stack);
        float direction = Math.signum(controller.getDirectionAwareBeltMovementSpeed());
        item.beltPosition = direction >= 0 ? 0.5F : controller.beltLength - 0.5F;
        controller.getInventory().addItem(item);
    }

    private static DeployerBlockEntity deployerBe(GameTestHelper helper) {
        return (DeployerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEPLOYER));
    }

    private static List<ItemStack> workpieces(GameTestHelper helper) {
        List<ItemStack> result = new ArrayList<>();
        BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
        if (controller != null) {
            controller.getInventory().getTransportedItems().forEach(t -> result.add(t.stack));
        }
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(DEPLOYER)).inflate(8)).forEach(e -> result.add(e.getItem()));
        return result.stream().filter(s -> s.is(Items.IRON_PICKAXE)).toList();
    }

    private static String describe(GameTestHelper helper) {
        DeployerBlockEntity deployer = deployerBe(helper);
        return "deployer speed=" + deployer.getSpeed() + " hand=" + (deployer.getPlayer() == null ? "no player" : deployer.getPlayer().getMainHandItem())
                + " items=" + workpieces(helper) + " log=" + workpieces(helper).stream().map(SpikeLine::operationsOf).toList();
    }

    /** Runs the first deployer step of a chain on a real line with {@code hand} in the deployer, then checks the log and the hand. */
    private static void liveDeployerRun(GameTestHelper helper, ItemStack hand, String expectedKey, int expectedLeft, boolean finishAsSuccess) {
        ResearchBlueprint blueprint = blueprintStartingWithADeployer();
        TerminalBlockEntity terminal = terminalWith(helper, blueprint);
        ItemStack workpiece = issueWorkpiece(helper, terminal);
        beltAndDeployer(helper);
        helper.runAfterDelay(10, () -> {
            DeployerBlockEntity deployer = deployerBe(helper);
            helper.assertTrue(deployer.getPlayer() != null, "the deployer has no fake player yet");
            deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, hand.copy());
            putOnBelt(helper, workpiece);
        });
        helper.succeedWhen(() -> {
            List<ItemStack> found = workpieces(helper);
            helper.assertTrue(found.size() == 1, "one workpiece expected | " + describe(helper));
            helper.assertTrue(SpikeLine.operationsOf(found.getFirst()).equals(List.of(expectedKey)),
                    "the deployer must record " + expectedKey + " | " + describe(helper));
            helper.assertTrue(deployerBe(helper).getPlayer().getMainHandItem().getCount() == expectedLeft,
                    "the hand must be left with " + expectedLeft + " | " + describe(helper));
            if (finishAsSuccess) {
                // The rest of the chain is appended by hand (press and fan are covered by the spike tests) and scored.
                ItemStack run = found.getFirst();
                List<ChainStep> chain = terminal.steps();
                for (int i = 1; i < chain.size(); i++) {
                    run = SpikeLine.withOperation(run, chain.get(i).key());
                }
                terminal.inventory().insertItem(TerminalSlots.RETURN, run, false);
                helper.assertTrue(terminal.checkRun() == null, "the run is checked");
                helper.assertTrue(terminal.data.get(TerminalBlockEntity.DATA_PERCENT) == 100, "a live deployer step plus the rest in order is 100%");
            } else {
                terminal.inventory().insertItem(TerminalSlots.RETURN, found.getFirst(), false);
                helper.assertTrue(terminal.checkRun() == null, "the run is checked");
                helper.assertTrue(terminal.data.get(TerminalBlockEntity.DATA_PERCENT) < 100, "a failed run is below 100%");
                helper.assertTrue(terminal.inventory().getStackInSlot(TerminalSlots.OUTPUT).is(com.blueprintforge.registry.BFItems.SCRAP.get()), "and gives scrap");
            }
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 900)
    public static void deployerOnTheLineSpendsItsResourceAndRecordsTheStepOnASuccessfulRun(GameTestHelper helper) {
        ChainStep first = ResearchChain.steps(blueprintStartingWithADeployer().seed().orElseThrow(),
                ResearchRegistry.tier(T3).orElseThrow(), ResearchChain.GENERATOR_VERSION).getFirst();
        ItemStack hand = new ItemStack(BuiltInRegistries.ITEM.get(first.item()), first.count() + 5);
        liveDeployerRun(helper, hand, first.key(), 5, true);
    }

    @GameTest(template = EMPTY, timeoutTicks = 900)
    public static void deployerOnTheLineSpendsAWrongResourceTooAndTheRunFails(GameTestHelper helper) {
        liveDeployerRun(helper, new ItemStack(Items.STICK, 5), ChainStep.deployKey(ResourceLocation.parse("minecraft:stick"), 1), 4, false);
    }
}
