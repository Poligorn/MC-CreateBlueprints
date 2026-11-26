package com.blueprintforge.gametest;

import java.util.List;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.compat.createapi.SpikeLine;
import com.blueprintforge.item.ResearchBlueprintItem;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFItems;
import com.blueprintforge.research.ChainStep;
import com.blueprintforge.research.ResearchBlueprint;
import com.blueprintforge.research.ResearchChain;
import com.blueprintforge.research.ResearchRegistry;
import com.blueprintforge.research.ResearchRun;
import com.blueprintforge.research.ResearchTier;
import com.blueprintforge.research.RunScore;
import com.blueprintforge.terminal.LineScanner;
import com.blueprintforge.terminal.TerminalBlock;
import com.blueprintforge.terminal.TerminalBlockEntity;
import com.blueprintforge.terminal.TerminalMenu;
import com.blueprintforge.terminal.TerminalScreenState;
import com.blueprintforge.terminal.TerminalSlots;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** The terminal on the server, from blueprint to scored run. Rendering and the screen are not covered here. */
@GameTestHolder(BlueprintForge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerminalGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos TERMINAL = new BlockPos(2, 1, 2);
    private static final ResourceLocation T3 = BlueprintForge.id("t3");
    private static final ResourceLocation PICKAXE = BlueprintForge.id("pickaxe_t3");

    private TerminalGameTests() {
    }

    private static TerminalBlockEntity terminal(GameTestHelper helper) {
        BlockState lower = BFBlocks.TERMINAL.get().defaultBlockState().setValue(TerminalBlock.FACING, Direction.NORTH);
        helper.setBlock(TERMINAL, lower);
        helper.setBlock(TERMINAL.above(), lower.setValue(TerminalBlock.HALF, DoubleBlockHalf.UPPER));
        return helper.getBlockEntity(TERMINAL);
    }

    private static ItemStack blueprint(ResourceLocation tierId, boolean copy) {
        ResearchTier tier = ResearchRegistry.tier(tierId).orElseThrow(() -> new IllegalStateException(tierId + " not loaded"));
        return ResearchBlueprintItem.create(tierId, tier, PICKAXE, ResearchRegistry.target(PICKAXE).orElseThrow(), copy);
    }

    private static ItemStack pickaxe() {
        return new ItemStack(Items.IRON_PICKAXE);
    }

    private static void insertBlueprint(GameTestHelper helper, TerminalBlockEntity be, ResourceLocation tier) {
        helper.assertTrue(be.inventory().insertItem(TerminalSlots.BLUEPRINT, blueprint(tier, false), false).isEmpty(), "the blueprint must fit");
    }

    /** A workpiece out of the terminal, with the log filled in by {@code operations}. */
    private static ItemStack runWith(GameTestHelper helper, TerminalBlockEntity be, List<String> operations) {
        helper.assertTrue(be.inventory().insertItem(TerminalSlots.ITEM, pickaxe(), false).isEmpty(), "the target must fit");
        helper.assertTrue(be.createWorkpiece() == null, "the workpiece must be issued");
        ItemStack workpiece = be.inventory().extractItem(TerminalSlots.OUTPUT, 1, false);
        helper.assertFalse(workpiece.isEmpty(), "the workpiece must be in the output");
        for (String operation : operations) {
            workpiece = SpikeLine.withOperation(workpiece, operation);
        }
        return workpiece;
    }

    private static List<String> chainOf(TerminalBlockEntity be) {
        return be.steps().stream().map(ChainStep::key).toList();
    }

    @GameTest(template = EMPTY)
    public static void terminalGivesASeedAndChangesItsScreenWhenABlueprintIsInserted(GameTestHelper helper) {
        TerminalBlockEntity be = terminal(helper);
        helper.assertTrue(helper.getBlockState(TERMINAL).getValue(TerminalBlock.SCREEN) == TerminalScreenState.IDLE, "empty terminal is idle");
        insertBlueprint(helper, be, T3);

        ResearchBlueprint stored = be.inventory().getStackInSlot(TerminalSlots.BLUEPRINT).get(BFComponents.RESEARCH_BLUEPRINT.get());
        helper.assertTrue(stored.seed().isPresent(), "the seed is assigned on the first insertion");
        long seed = stored.seed().get();
        helper.assertTrue(helper.getBlockState(TERMINAL).getValue(TerminalBlock.SCREEN) == TerminalScreenState.ACTIVE, "a blueprint lights the screen");
        int steps = be.chain().size();
        ResearchTier tier = ResearchRegistry.tier(T3).orElseThrow();
        helper.assertTrue(steps >= tier.steps().min() + tier.attemptCost().deployers().min() && steps <= tier.steps().max() + tier.attemptCost().deployers().max(), "steps within the tier range plus deployers, got " + steps);
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_STEPS) == steps, "the menu data reports the step count");
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_PRESS) + be.data.get(TerminalBlockEntity.DATA_BLAST) + be.data.get(TerminalBlockEntity.DATA_DEPLOY) + be.data.get(TerminalBlockEntity.DATA_OTHER) == steps,
                "operation counts add up to the steps");

        // Taking the blueprint out and putting it back keeps the seed, so the chain does not change.
        ItemStack taken = be.inventory().extractItem(TerminalSlots.BLUEPRINT, 1, false);
        helper.assertTrue(helper.getBlockState(TERMINAL).getValue(TerminalBlock.SCREEN) == TerminalScreenState.IDLE, "back to idle");
        be.inventory().insertItem(TerminalSlots.BLUEPRINT, taken, false);
        helper.assertTrue(be.inventory().getStackInSlot(TerminalSlots.BLUEPRINT).get(BFComponents.RESEARCH_BLUEPRINT.get()).seed().orElse(0L) == seed,
                "the seed survives taking the blueprint out");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void terminalTakesOnlyTheBlueprintsItemAndSpendsItForAWorkpiece(GameTestHelper helper) {
        TerminalBlockEntity be = terminal(helper);
        helper.assertFalse(be.inventory().insertItem(TerminalSlots.ITEM, pickaxe(), false).isEmpty(), "no target slot without a blueprint");
        helper.assertFalse(be.inventory().insertItem(TerminalSlots.BLUEPRINT, new ItemStack(Items.PAPER), false).isEmpty(), "only blueprints go in the blueprint slot");
        insertBlueprint(helper, be, T3);
        helper.assertFalse(be.inventory().insertItem(TerminalSlots.ITEM, new ItemStack(Items.IRON_SWORD), false).isEmpty(), "a sword is not this blueprint's target");
        helper.assertFalse(be.inventory().insertItem(TerminalSlots.OUTPUT, pickaxe(), false).isEmpty(), "the output slot takes nothing from outside");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        be.inventory().insertItem(TerminalSlots.ITEM, pickaxe(), false);
        TerminalMenu menu = new TerminalMenu(1, player.getInventory(), be);
        helper.assertTrue(menu.clickMenuButton(player, TerminalMenu.BUTTON_ACTION), "the button is handled");

        helper.assertTrue(be.inventory().getStackInSlot(TerminalSlots.ITEM).isEmpty(), "the target item is spent when the workpiece is issued");
        ItemStack workpiece = be.inventory().getStackInSlot(TerminalSlots.OUTPUT);
        helper.assertTrue(workpiece.is(Items.IRON_PICKAXE) && SpikeLine.isWorkpiece(workpiece), "the workpiece is the target item with a log");
        helper.assertTrue(SpikeLine.operationsOf(workpiece).isEmpty(), "the log starts empty");
        ResearchRun run = workpiece.get(BFComponents.RESEARCH_RUN.get());
        ResearchBlueprint stored = be.inventory().getStackInSlot(TerminalSlots.BLUEPRINT).get(BFComponents.RESEARCH_BLUEPRINT.get());
        helper.assertTrue(run != null && run.seed() == stored.seed().orElseThrow() && run.tier().equals(T3), "the workpiece remembers tier and seed");
        helper.assertTrue(run.steps() == be.chain().size(), "the workpiece knows how long its chain is, the M of 'N of M'");

        // The returned workpiece shares the item slot with the target, but is never taken for a fresh target.
        helper.assertTrue(be.inventory().insertItem(TerminalSlots.ITEM, workpiece.copy(), false).isEmpty(), "a workpiece fits the item slot");
        helper.assertTrue(be.createWorkpiece() != null, "a workpiece in the item slot is not a target");
        be.inventory().extractItem(TerminalSlots.ITEM, 1, false);

        // A second workpiece cannot be issued while the first sits in the output.
        be.inventory().insertItem(TerminalSlots.ITEM, pickaxe(), false);
        helper.assertTrue(be.createWorkpiece() != null, "a busy output refuses a second workpiece");
        helper.assertFalse(be.inventory().getStackInSlot(TerminalSlots.ITEM).isEmpty(), "and does not spend the target");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aRunMatchingTheChainGivesTheItemAndAnyOtherRunGivesScrap(GameTestHelper helper) {
        TerminalBlockEntity be = terminal(helper);
        insertBlueprint(helper, be, T3);
        List<String> chain = chainOf(be);

        // Perfect run: the log equals the chain.
        ItemStack perfect = runWith(helper, be, chain);
        helper.assertTrue(be.inventory().insertItem(TerminalSlots.ITEM, perfect, false).isEmpty(), "the workpiece goes into the return slot");
        helper.assertTrue(be.checkRun() == null, "the run is checked");
        ItemStack reward = be.inventory().extractItem(TerminalSlots.OUTPUT, 1, false);
        helper.assertTrue(reward.is(Items.IRON_PICKAXE) && !SpikeLine.isWorkpiece(reward), "100% gives the target item (placeholder for the enhanced one)");
        helper.assertTrue(Boolean.TRUE.equals(reward.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "the result glints");
        helper.assertTrue(reward.get(DataComponents.CUSTOM_NAME) != null
                && reward.get(DataComponents.CUSTOM_NAME).getStyle().getColor() != null
                && reward.get(DataComponents.CUSTOM_NAME).getStyle().getColor().getValue() == net.minecraft.ChatFormatting.GOLD.getColor(),
                "the result has a gold name");
        helper.assertTrue(reward.get(DataComponents.LORE) != null && reward.get(DataComponents.LORE).lines().size() == 1,
                "the result names its tier in one lore line");
        helper.assertTrue(be.inventory().getStackInSlot(TerminalSlots.ITEM).isEmpty(), "the workpiece is spent");
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_PERCENT) == 100, "last run is 100%");
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_MATCHED) == chain.size() && be.data.get(TerminalBlockEntity.DATA_TOTAL) == chain.size(), "all steps matched");

        // Wrong run: every step is a press, but the chain has at least one blast.
        List<String> wrong = chain.stream().map(step -> SpikeLine.PRESS).toList();
        ItemStack failed = runWith(helper, be, wrong);
        be.inventory().insertItem(TerminalSlots.ITEM, failed, false);
        helper.assertTrue(be.checkRun() == null, "the second run is checked");
        ItemStack junk = be.inventory().extractItem(TerminalSlots.OUTPUT, 1, false);
        helper.assertTrue(junk.is(BFItems.SCRAP.get()), "anything but 100% gives scrap");
        RunScore expected = RunScore.of(chain, wrong);
        helper.assertTrue(expected.percent() < 100, "the wrong run is below 100%");
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_PERCENT) == expected.percent(), "percent by position: " + expected.percent());
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_MATCHED) == expected.matched(), "matched positions are reported");
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_LAST_RECORDED) == wrong.size(), "the hint counts the steps the run recorded");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void anUnrunWorkpieceScoresZeroAndAnExtraStepIsNotAHundred(GameTestHelper helper) {
        TerminalBlockEntity be = terminal(helper);
        insertBlueprint(helper, be, T3);
        List<String> chain = chainOf(be);

        be.inventory().insertItem(TerminalSlots.ITEM, runWith(helper, be, List.of()), false);
        helper.assertTrue(be.checkRun() == null, "an empty log is still a run");
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_PERCENT) == 0, "no recorded steps is 0%");
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_LAST_RECORDED) == 0, "and no recorded steps is reported as 0");
        helper.assertTrue(be.inventory().extractItem(TerminalSlots.OUTPUT, 1, false).is(BFItems.SCRAP.get()), "and gives scrap");

        List<String> longer = new java.util.ArrayList<>(chain);
        longer.add(SpikeLine.PRESS);
        be.inventory().insertItem(TerminalSlots.ITEM, runWith(helper, be, longer), false);
        be.checkRun();
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_PERCENT) == 99, "every step right plus an extra one is 99%, not 100%");
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_LAST_RECORDED) == chain.size() + 1, "recorded steps can exceed the chain: 'N of M' shows it");
        helper.assertTrue(be.inventory().extractItem(TerminalSlots.OUTPUT, 1, false).is(BFItems.SCRAP.get()), "so it gives scrap");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theOutputCanBeTakenOnlyFromTheBrassPortSide(GameTestHelper helper) {
        TerminalBlockEntity be = terminal(helper);
        insertBlueprint(helper, be, T3);
        be.inventory().insertItem(TerminalSlots.ITEM, pickaxe(), false);
        be.createWorkpiece();

        BlockPos pos = helper.absolutePos(TERMINAL);
        Direction port = Direction.NORTH.getClockWise();
        for (Direction side : Direction.values()) {
            IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
            helper.assertTrue((handler != null) == (side == port), "only the " + port + " side has an item handler, " + side + " gave " + handler);
        }
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, port);
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.PAPER), false).is(Items.PAPER), "nothing can be inserted");
        ItemStack out = handler.extractItem(0, 1, false);
        helper.assertTrue(SpikeLine.isWorkpiece(out), "the workpiece comes out of the port");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theLineScannerFindsAPressAndAFanBehindLavaButNotOtherFans(GameTestHelper helper) {
        // A radius of 3 keeps the scan inside this scene: the neighbouring test scenes hold presses and lava fans too.
        BlockPos origin = helper.absolutePos(TERMINAL);
        int both = LineScanner.PRESS | LineScanner.BLAST;
        helper.assertTrue(LineScanner.missing(helper.getLevel(), origin, true, true, 3) == both, "an empty area lacks both machines");
        helper.assertTrue(LineScanner.missing(helper.getLevel(), origin, false, false, 3) == 0, "nothing needed, nothing missing");

        helper.setBlock(TERMINAL.offset(1, 1, 1), AllBlocks.MECHANICAL_PRESS.getDefaultState());
        helper.assertTrue(LineScanner.missing(helper.getLevel(), origin, true, true, 3) == LineScanner.BLAST, "with a press only the fan is missing");

        helper.setBlock(TERMINAL.offset(-1, 0, 2), AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        helper.setBlock(TERMINAL.offset(-1, 0, 1), Blocks.WATER);
        helper.assertTrue(LineScanner.missing(helper.getLevel(), origin, true, true, 3) == LineScanner.BLAST, "a fan behind water is not a blasting fan");

        helper.setBlock(TERMINAL.offset(-1, 0, 1), Blocks.LAVA);
        helper.assertTrue(LineScanner.missing(helper.getLevel(), origin, true, true, 3) == 0, "a press and a fan behind lava are enough");
        helper.assertTrue(LineScanner.missing(helper.getLevel(), origin, false, true, 3) == 0, "a machine that is not needed is never reported");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theTerminalReportsOnlyMachinesTheChainNeeds(GameTestHelper helper) {
        TerminalBlockEntity be = terminal(helper);
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_MISSING) == 0, "no blueprint, nothing is missing");
        insertBlueprint(helper, be, T3);
        int missing = be.data.get(TerminalBlockEntity.DATA_MISSING);
        helper.assertTrue((missing & ~(LineScanner.PRESS | LineScanner.BLAST | LineScanner.DEPLOYER)) == 0, "only the three known machines can be reported, got " + missing);
        be.inventory().extractItem(TerminalSlots.BLUEPRINT, 1, false);
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_MISSING) == 0, "taking the blueprint out clears the report");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theTerminalIsTwoBlocksTallAndBreaksAsOne(GameTestHelper helper) {
        TerminalBlockEntity be = terminal(helper);
        insertBlueprint(helper, be, T3);
        helper.assertTrue(helper.getBlockState(TERMINAL.above()).getValue(TerminalBlock.SCREEN) == TerminalScreenState.ACTIVE, "the upper half shows the screen state too");
        helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(TERMINAL.above())) == null, "only the lower half holds the block entity");
        helper.destroyBlock(TERMINAL.above());
        helper.assertTrue(helper.getBlockState(TERMINAL).isAir(), "breaking the upper half removes the lower one");
        long blueprints = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM, TERMINAL, 3.0).stream()
                .filter(e -> e.getItem().is(BFItems.RESEARCH_BLUEPRINT.get())).count();
        helper.assertTrue(blueprints == 1, "the contents drop exactly once, got " + blueprints);
        long terminals = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM, TERMINAL, 3.0).stream()
                .filter(e -> e.getItem().is(BFItems.TERMINAL.get())).count();
        helper.assertTrue(terminals <= 1, "one terminal item at most, got " + terminals);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theTerminalDoesNotFitUnderAnObstacle(GameTestHelper helper) {
        helper.setBlock(TERMINAL.above(), Blocks.STONE);
        BlockState placed = BFBlocks.TERMINAL.get().getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(
                helper.makeMockPlayer(GameType.SURVIVAL), net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(BFItems.TERMINAL.get()),
                new net.minecraft.world.phys.BlockHitResult(helper.absolutePos(TERMINAL).getCenter(), Direction.UP, helper.absolutePos(TERMINAL), false)));
        helper.assertTrue(placed == null, "no terminal when the space above is taken");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void brokenTerminalDropsWhatItHolds(GameTestHelper helper) {
        TerminalBlockEntity be = terminal(helper);
        insertBlueprint(helper, be, T3);
        be.inventory().insertItem(TerminalSlots.ITEM, pickaxe(), false);
        helper.destroyBlock(TERMINAL);
        List<ItemEntity> drops = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM, TERMINAL, 3.0);
        helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(BFItems.RESEARCH_BLUEPRINT.get())), "the blueprint is dropped");
        helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.IRON_PICKAXE)), "the target item is dropped");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aBlueprintOfAnUnknownTierGivesNoChainAndNoWorkpiece(GameTestHelper helper) {
        TerminalBlockEntity be = terminal(helper);
        ItemStack stack = blueprint(T3, false);
        ResearchBlueprint base = stack.get(BFComponents.RESEARCH_BLUEPRINT.get());
        stack.set(BFComponents.RESEARCH_BLUEPRINT.get(), new ResearchBlueprint(BlueprintForge.id("no_such_tier"), base.target(), base.item(), base.symbol(),
                base.spriteTier(), false, java.util.Optional.empty(), ResearchChain.GENERATOR_VERSION));
        be.inventory().insertItem(TerminalSlots.BLUEPRINT, stack, false);
        helper.assertTrue(be.chain().isEmpty(), "no chain for a tier that is not loaded");
        helper.assertTrue((be.data.get(TerminalBlockEntity.DATA_FLAGS) & TerminalBlockEntity.FLAG_UNKNOWN_TIER) != 0, "the screen is told the tier is unknown");
        be.inventory().insertItem(TerminalSlots.ITEM, pickaxe(), false);
        helper.assertTrue(be.createWorkpiece() != null, "no workpiece for an unknown tier");
        helper.assertFalse(be.inventory().getStackInSlot(TerminalSlots.ITEM).isEmpty(), "and the target is kept");
        helper.succeed();
    }
}
