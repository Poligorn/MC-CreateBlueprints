package com.blueprintforge.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.compat.createapi.SpikeLine;
import com.blueprintforge.item.ResearchBlueprintItem;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFItems;
import com.blueprintforge.research.AttemptCost;
import com.blueprintforge.research.ChainStep;
import com.blueprintforge.research.ResearchBlueprint;
import com.blueprintforge.research.ResearchChain;
import com.blueprintforge.research.ResearchRegistry;
import com.blueprintforge.research.ResearchTier;
import com.blueprintforge.terminal.LineScanner;
import com.blueprintforge.terminal.TerminalBlock;
import com.blueprintforge.terminal.TerminalBlockEntity;
import com.blueprintforge.terminal.TerminalMenu;
import com.blueprintforge.terminal.TerminalSlots;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The two-block terminal as a block (placement, both halves, breaking, saving) and its menu, on the server. */
@GameTestHolder(BlueprintForge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerminalBlockGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos TERMINAL = new BlockPos(2, 1, 2);
    private static final ResourceLocation T3 = BlueprintForge.id("t3");
    private static final ResourceLocation PICKAXE = BlueprintForge.id("pickaxe_t3");

    private TerminalBlockGameTests() {
    }

    /**
     * A real server player that never "logs in": the vanilla mock goes through the login event, and Create's handler for it
     * cannot send its packets to the mock connection. Packets sent to this one are dropped.
     */
    private static ServerPlayer serverPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "terminal-test");
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) {
            }
        };
        player.setPos(helper.absoluteVec(new Vec3(0.5, 1, 0.5)));
        return player;
    }

    /** Places the terminal item on the floor under {@code TERMINAL} the way a player does. */
    private static InteractionResult place(GameTestHelper helper, ServerPlayer player, Direction look) {
        // The empty template has no floor of its own; without one the click would replace the air and place a block lower.
        helper.setBlock(TERMINAL.below(), Blocks.STONE);
        BlockPos floor = helper.absolutePos(TERMINAL.below());
        player.setYRot(look.toYRot());
        BlockHitResult hit = new BlockHitResult(floor.getCenter().add(0, 0.5, 0), Direction.UP, floor, false);
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(BFItems.TERMINAL.get()), hit);
        return ((BlockItem) BFItems.TERMINAL.get()).place(context);
    }

    private static ItemStack blueprint() {
        ResearchTier tier = ResearchRegistry.tier(T3).orElseThrow();
        return ResearchBlueprintItem.create(T3, tier, PICKAXE, ResearchRegistry.target(PICKAXE).orElseThrow(), false);
    }

    private static TerminalBlockEntity placedTerminal(GameTestHelper helper) {
        helper.setBlock(TERMINAL, BFBlocks.TERMINAL.get().defaultBlockState());
        helper.setBlock(TERMINAL.above(), BFBlocks.TERMINAL.get().defaultBlockState().setValue(TerminalBlock.HALF, DoubleBlockHalf.UPPER));
        return helper.getBlockEntity(TERMINAL);
    }

    @GameTest(template = EMPTY)
    public static void placingTheTerminalItemMakesTwoHalvesFacingThePlayer(GameTestHelper helper) {
        ServerPlayer player = serverPlayer(helper);
        helper.assertTrue(place(helper, player, Direction.SOUTH).consumesAction(), "the placement succeeds with free space above");
        BlockState lower = helper.getBlockState(TERMINAL);
        BlockState upper = helper.getBlockState(TERMINAL.above());
        helper.assertTrue(lower.is(BFBlocks.TERMINAL.get()) && lower.getValue(TerminalBlock.HALF) == DoubleBlockHalf.LOWER, "lower half below");
        helper.assertTrue(upper.is(BFBlocks.TERMINAL.get()) && upper.getValue(TerminalBlock.HALF) == DoubleBlockHalf.UPPER, "upper half above");
        helper.assertTrue(lower.getValue(TerminalBlock.FACING) == Direction.NORTH && upper.getValue(TerminalBlock.FACING) == Direction.NORTH,
                "a player looking south gets a terminal facing north, got " + lower.getValue(TerminalBlock.FACING));
        helper.assertTrue(helper.getBlockEntity(TERMINAL) instanceof TerminalBlockEntity, "the lower half has the block entity");
        helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(TERMINAL.above())) == null, "the upper half has none");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void placingTheTerminalIsRefusedWhenTheSpaceAboveIsTaken(GameTestHelper helper) {
        helper.setBlock(TERMINAL.above(), Blocks.STONE);
        ServerPlayer player = serverPlayer(helper);
        helper.assertFalse(place(helper, player, Direction.SOUTH).consumesAction(), "the placement is refused");
        helper.assertTrue(helper.getBlockState(TERMINAL).isAir(), "no lower half is left behind");
        helper.assertTrue(helper.getBlockState(TERMINAL.above()).is(Blocks.STONE), "the obstacle is untouched");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theMenuOpensFromTheLowerAndFromTheUpperHalf(GameTestHelper helper) {
        TerminalBlockEntity be = placedTerminal(helper);
        be.inventory().insertItem(TerminalSlots.BLUEPRINT, blueprint(), false);
        for (BlockPos half : List.of(TERMINAL, TERMINAL.above())) {
            ServerPlayer player = serverPlayer(helper);
            helper.useBlock(half, player);
            helper.assertTrue(player.containerMenu instanceof TerminalMenu, "the menu opens from " + half);
            TerminalMenu menu = (TerminalMenu) player.containerMenu;
            // The menu works on the one inventory of the lower half, whichever half was used.
            helper.assertTrue(menu.getSlot(TerminalSlots.BLUEPRINT).getItem().is(BFItems.RESEARCH_BLUEPRINT.get()), "the same blueprint is seen from " + half);
            helper.assertTrue(menu.data(TerminalBlockEntity.DATA_STEPS) == be.data.get(TerminalBlockEntity.DATA_STEPS), "the same counters");
            player.closeContainer();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theMenuHasThreeTerminalSlotsAndTheOutputTakesNothing(GameTestHelper helper) {
        TerminalBlockEntity be = placedTerminal(helper);
        ServerPlayer player = serverPlayer(helper);
        TerminalMenu menu = new TerminalMenu(1, player.getInventory(), be);
        helper.assertTrue(menu.slots.size() == TerminalSlots.COUNT + 36, "three terminal slots and the player inventory, got " + menu.slots.size());
        helper.assertTrue(TerminalSlots.COUNT == 3, "blueprint, item, output");
        helper.assertTrue(menu.getSlot(TerminalSlots.BLUEPRINT).mayPlace(blueprint()), "the blueprint slot takes a blueprint");
        helper.assertFalse(menu.getSlot(TerminalSlots.BLUEPRINT).mayPlace(new ItemStack(Items.IRON_PICKAXE)), "and nothing else");
        helper.assertFalse(menu.getSlot(TerminalSlots.ITEM).mayPlace(new ItemStack(Items.IRON_PICKAXE)), "the item slot waits for the target of a blueprint");
        be.inventory().insertItem(TerminalSlots.BLUEPRINT, blueprint(), false);
        helper.assertTrue(menu.getSlot(TerminalSlots.ITEM).mayPlace(new ItemStack(Items.IRON_PICKAXE)), "now it takes the target");
        helper.assertFalse(menu.getSlot(TerminalSlots.ITEM).mayPlace(new ItemStack(Items.STONE)), "but not another item");
        helper.assertFalse(menu.getSlot(TerminalSlots.OUTPUT).mayPlace(new ItemStack(Items.IRON_PICKAXE)), "the output is take-only");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theButtonMakesAWorkpieceThatGoesBackIntoTheItemSlotAndIsChecked(GameTestHelper helper) {
        TerminalBlockEntity be = placedTerminal(helper);
        ServerPlayer player = serverPlayer(helper);
        TerminalMenu menu = new TerminalMenu(1, player.getInventory(), be);
        helper.assertTrue(menu.clickMenuButton(player, TerminalMenu.BUTTON_ACTION), "the button is handled");
        helper.assertTrue(menu.getSlot(TerminalSlots.OUTPUT).getItem().isEmpty(), "nothing happens without a blueprint");

        be.inventory().insertItem(TerminalSlots.BLUEPRINT, blueprint(), false);
        be.inventory().insertItem(TerminalSlots.ITEM, new ItemStack(Items.IRON_PICKAXE), false);
        menu.clickMenuButton(player, TerminalMenu.BUTTON_ACTION);
        helper.assertTrue(menu.getSlot(TerminalSlots.ITEM).getItem().isEmpty(), "the target is spent");
        ItemStack workpiece = menu.getSlot(TerminalSlots.OUTPUT).getItem().copy();
        helper.assertTrue(SpikeLine.isWorkpiece(workpiece), "the output holds the workpiece");
        menu.getSlot(TerminalSlots.OUTPUT).set(ItemStack.EMPTY);

        // The line brings the workpiece back to the item slot, there is no fourth slot for it.
        for (ChainStep step : be.steps()) {
            workpiece = SpikeLine.withOperation(workpiece, step.key());
        }
        helper.assertTrue(menu.getSlot(TerminalSlots.ITEM).mayPlace(workpiece), "the item slot takes the returned workpiece");
        menu.getSlot(TerminalSlots.ITEM).set(workpiece);
        menu.clickMenuButton(player, TerminalMenu.BUTTON_ACTION);
        helper.assertTrue(be.data.get(TerminalBlockEntity.DATA_PERCENT) == 100, "the exact run is 100%");
        helper.assertTrue(menu.getSlot(TerminalSlots.ITEM).getItem().isEmpty(), "the workpiece is taken");
        helper.assertTrue(menu.getSlot(TerminalSlots.OUTPUT).getItem().is(Items.IRON_PICKAXE), "and the item is the result");

        // The result blocks the button until it is taken.
        be.inventory().insertItem(TerminalSlots.ITEM, new ItemStack(Items.IRON_PICKAXE), false);
        menu.clickMenuButton(player, TerminalMenu.BUTTON_ACTION);
        helper.assertFalse(menu.getSlot(TerminalSlots.ITEM).getItem().isEmpty(), "a busy output keeps the target");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theMenuReportsDeployersAndThePriceOfAnAttemptForAV1Chain(GameTestHelper helper) {
        TerminalBlockEntity be = placedTerminal(helper);
        ResearchTier tier = ResearchRegistry.tier(T3).orElseThrow();
        ItemStack stack = blueprint();
        ResearchBlueprint base = stack.get(BFComponents.RESEARCH_BLUEPRINT.get());
        stack.set(BFComponents.RESEARCH_BLUEPRINT.get(), base.withSeed(42L));
        be.inventory().insertItem(TerminalSlots.BLUEPRINT, stack, false);
        List<ChainStep> chain = ResearchChain.steps(42L, tier, ResearchChain.GENERATOR_VERSION);
        ServerPlayer player = serverPlayer(helper);
        TerminalMenu menu = new TerminalMenu(1, player.getInventory(), be);

        int deployers = (int) chain.stream().filter(ChainStep::isDeploy).count();
        helper.assertTrue(deployers >= tier.attemptCost().deployers().min(), "a T3 chain carries deployers, got " + deployers);
        helper.assertTrue(menu.data(TerminalBlockEntity.DATA_DEPLOY) == deployers, "the menu counts deployers");
        helper.assertTrue(menu.data(TerminalBlockEntity.DATA_STEPS) == chain.size(), "and the steps");
        int sum = 0;
        for (var total : AttemptCost.totals(chain).entrySet()) {
            sum += total.getValue();
        }
        int shown = 0;
        for (int i = 0; i < TerminalBlockEntity.MAX_COST_ITEMS; i++) {
            int count = menu.data(TerminalBlockEntity.DATA_COST + 2 * i + 1);
            shown += count;
            if (count > 0) {
                var key = BuiltInRegistries.ITEM.getKey(BuiltInRegistries.ITEM.byId(menu.data(TerminalBlockEntity.DATA_COST + 2 * i)));
                helper.assertTrue(AttemptCost.totals(chain).get(key) == count, "the price of " + key + " is the total over all steps");
            }
        }
        helper.assertTrue(shown == sum && sum > 0, "every resource of the deployer steps is in the price, got " + shown + " of " + sum);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theLineScannerReportsAMissingDeployerAndSeesOnePlaced(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(TERMINAL);
        helper.assertTrue(LineScanner.missing(helper.getLevel(), origin, false, false, true, 3) == LineScanner.DEPLOYER, "an empty area lacks the deployer");
        helper.assertTrue(LineScanner.missing(helper.getLevel(), origin, false, false, false, 3) == 0, "an unneeded deployer is not reported");
        helper.setBlock(TERMINAL.offset(1, 1, 1), AllBlocks.DEPLOYER.getDefaultState());
        helper.assertTrue(LineScanner.missing(helper.getLevel(), origin, false, false, true, 3) == 0, "a deployer nearby is enough");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theInventoryIsKeptWhenTheBlockEntityIsSavedAndLoadedAgain(GameTestHelper helper) {
        TerminalBlockEntity be = placedTerminal(helper);
        be.inventory().insertItem(TerminalSlots.BLUEPRINT, blueprint(), false);
        ResearchBlueprint seeded = be.inventory().getStackInSlot(TerminalSlots.BLUEPRINT).get(BFComponents.RESEARCH_BLUEPRINT.get());
        be.inventory().insertItem(TerminalSlots.ITEM, new ItemStack(Items.IRON_PICKAXE), false);
        helper.assertTrue(seeded.seed().isPresent(), "the seed is given on insertion");

        CompoundTag saved = be.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockState state = helper.getBlockState(TERMINAL);
        BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(TERMINAL), state, saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof TerminalBlockEntity, "the saved tag loads as a terminal");
        TerminalBlockEntity copy = (TerminalBlockEntity) loaded;
        ResearchBlueprint back = copy.inventory().getStackInSlot(TerminalSlots.BLUEPRINT).get(BFComponents.RESEARCH_BLUEPRINT.get());
        helper.assertTrue(back != null && back.seed().equals(seeded.seed()), "the blueprint keeps its seed");
        helper.assertTrue(copy.inventory().getStackInSlot(TerminalSlots.ITEM).is(Items.IRON_PICKAXE), "the item stays");
        helper.assertTrue(copy.data.get(TerminalBlockEntity.DATA_STEPS) == be.data.get(TerminalBlockEntity.DATA_STEPS), "the counters are rebuilt from the blueprint");
        helper.assertTrue(copy.data.get(TerminalBlockEntity.DATA_DEPLOY) == be.data.get(TerminalBlockEntity.DATA_DEPLOY), "including the deployers");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void breakingEitherHalfInSurvivalDropsTheTerminalAndEverythingInside(GameTestHelper helper) {
        for (BlockPos half : List.of(TERMINAL, TERMINAL.above())) {
            TerminalBlockEntity be = placedTerminal(helper);
            be.inventory().insertItem(TerminalSlots.BLUEPRINT, blueprint(), false);
            be.inventory().insertItem(TerminalSlots.ITEM, new ItemStack(Items.IRON_PICKAXE), false);
            ServerPlayer player = serverPlayer(helper);
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            player.gameMode.destroyBlock(helper.absolutePos(half));
            helper.assertTrue(helper.getBlockState(TERMINAL).isAir() && helper.getBlockState(TERMINAL.above()).isAir(), "both halves are gone after breaking " + half);
            List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, TERMINAL, 3.0);
            helper.assertTrue(count(drops, BFItems.TERMINAL.get()) == 1, "one terminal item from " + half + ", got " + count(drops, BFItems.TERMINAL.get()));
            helper.assertTrue(count(drops, BFItems.RESEARCH_BLUEPRINT.get()) == 1, "the blueprint once");
            helper.assertTrue(count(drops, Items.IRON_PICKAXE) == 1, "the item once");
            drops.forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    /**
     * Creative mode drops no terminal item, as with a door. The contents are not lost, they still drop: the player's
     * blueprint and target are worth more than the block, and vanishing them silently would be a trap.
     */
    @GameTest(template = EMPTY)
    public static void breakingTheTerminalInCreativeDropsNoBlockButKeepsTheContents(GameTestHelper helper) {
        for (BlockPos half : List.of(TERMINAL, TERMINAL.above())) {
            TerminalBlockEntity be = placedTerminal(helper);
            be.inventory().insertItem(TerminalSlots.BLUEPRINT, blueprint(), false);
            ServerPlayer player = serverPlayer(helper);
            player.setGameMode(GameType.CREATIVE);
            player.gameMode.destroyBlock(helper.absolutePos(half));
            helper.assertTrue(helper.getBlockState(TERMINAL).isAir() && helper.getBlockState(TERMINAL.above()).isAir(), "both halves are gone after breaking " + half);
            List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, TERMINAL, 3.0);
            helper.assertTrue(count(drops, BFItems.TERMINAL.get()) == 0, "no terminal item in creative from " + half + " (creative=" + player.isCreative() + "), got " + count(drops, BFItems.TERMINAL.get()));
            helper.assertTrue(count(drops, BFItems.RESEARCH_BLUEPRINT.get()) == 1, "the blueprint drops once from " + half);
            drops.forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    private static long count(List<ItemEntity> drops, net.minecraft.world.item.Item item) {
        return drops.stream().filter(e -> e.getItem().is(item)).count();
    }
}
