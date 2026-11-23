package com.blueprintforge.gametest;

import java.util.ArrayList;
import java.util.List;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.compat.createapi.SpikeFanBlastingType;
import com.blueprintforge.compat.createapi.SpikeLine;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Task 1 spike (Trello Agmz84v6): can a mod learn that a Create press or a bulk-blasting fan acted on a belt item,
 * and rewrite that item, without registering a Create recipe? Scenes are built with real Create blocks; nothing is
 * simulated by calling the hooks directly.
 */
@GameTestHolder(BlueprintForge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreateHookSpikeGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos BELT_START = new BlockPos(0, 1, 2);
    private static final BlockPos BELT_END = new BlockPos(4, 1, 2);
    private static final BlockPos PRESS = new BlockPos(2, 3, 2);

    private CreateHookSpikeGameTests() {
    }

    private static ItemStack workpiece() {
        return SpikeLine.workpiece(new ItemStack(Items.IRON_SWORD));
    }

    /** Five belt blocks along X. With {@code driveBelt} a creative motor runs it. */
    private static void belt(GameTestHelper helper, boolean driveBelt) {
        BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        helper.setBlock(BELT_START, shaft);
        helper.setBlock(BELT_END, shaft);
        BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(BELT_START), helper.absolutePos(BELT_END));
        if (driveBelt) {
            helper.setBlock(BELT_START.north(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        }
    }

    /** Puts a stack on the belt on segment {@code segment}, counted from the start. Needs a driven belt for the direction. */
    private static void putOnBelt(GameTestHelper helper, ItemStack stack, float segment) {
        BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
        helper.assertTrue(controller != null, "belt controller missing");
        TransportedItemStack item = new TransportedItemStack(stack);
        float direction = Math.signum(controller.getDirectionAwareBeltMovementSpeed());
        item.beltPosition = direction >= 0 ? segment : controller.beltLength - segment;
        controller.getInventory().addItem(item);
    }

    /** Everything that currently exists as an item in the scene: on the belt or already dropped. */
    private static List<ItemStack> allItems(GameTestHelper helper) {
        List<ItemStack> result = new ArrayList<>();
        BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
        if (controller != null) {
            controller.getInventory().getTransportedItems().forEach(t -> result.add(t.stack));
        }
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(PRESS)).inflate(8))
                .forEach(e -> result.add(e.getItem()));
        return result;
    }

    /** Scene state for failure messages, so a red test says what the machines were doing. */
    private static String describe(GameTestHelper helper) {
        StringBuilder out = new StringBuilder();
        if (helper.getLevel().getBlockEntity(helper.absolutePos(PRESS)) instanceof MechanicalPressBlockEntity press) {
            out.append("press speed=").append(press.getSpeed()).append(" running=").append(press.getPressingBehaviour().running)
                    .append(" specifics=").append(press.getPressingBehaviour().specifics.getClass().getSimpleName());
        }
        if (helper.getLevel().getBlockEntity(helper.absolutePos(FAN)) instanceof EncasedFanBlockEntity fan) {
            out.append(" fan speed=").append(fan.getSpeed()).append(" flow=").append(fan.getAirFlowDirection());
        }
        BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
        if (controller != null) {
            out.append(" belt speed=").append(controller.getSpeed()).append(" onBelt=");
            controller.getInventory().getTransportedItems().forEach(t -> out.append(t.stack).append('@').append(t.beltPosition)
                    .append(" processedBy=").append(t.processedBy == null ? null : t.processedBy.getClass().getSimpleName())
                    .append(" t=").append(t.processingTime).append("; "));
        }
        return out.toString();
    }

    private static List<ItemStack> items(GameTestHelper helper, Item item) {
        return allItems(helper).stream().filter(s -> s.is(item)).toList();
    }

    private static void placePress(GameTestHelper helper, BlockPos pos, boolean powered) {
        BlockState press = AllBlocks.MECHANICAL_PRESS.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        helper.setBlock(pos, press);
        if (powered) {
            // Motor beside the press, on whichever horizontal axis Create says the press turns about.
            Direction.Axis axis = AllBlocks.MECHANICAL_PRESS.get().getRotationAxis(press);
            Direction toPress = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            helper.setBlock(pos.relative(toPress.getOpposite()),
                    AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, toPress));
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 900)
    public static void pressStampsAWorkpieceOnTheBeltWithoutARecipe(GameTestHelper helper) {
        belt(helper, true);
        placePress(helper, PRESS, true);
        helper.runAfterDelay(20, () -> putOnBelt(helper, workpiece(), 0.5F));
        helper.succeedWhen(() -> {
            List<ItemStack> swords = items(helper, Items.IRON_SWORD);
            helper.assertTrue(swords.size() == 1, "exactly one workpiece must exist, got " + swords.size());
            helper.assertTrue(SpikeLine.operationsOf(swords.getFirst()).equals(List.of(SpikeLine.PRESS)),
                    "workpiece must record exactly one press, got " + SpikeLine.operationsOf(swords.getFirst()) + " | " + describe(helper));
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 900)
    public static void pressDoesNotStampTwiceWhileTheWorkpieceLeaves(GameTestHelper helper) {
        belt(helper, true);
        placePress(helper, PRESS, true);
        helper.runAfterDelay(20, () -> putOnBelt(helper, workpiece(), 0.5F));
        // Long after the item has passed the press, the log must still hold one entry.
        helper.runAfterDelay(700, () -> {
            List<ItemStack> swords = items(helper, Items.IRON_SWORD);
            helper.assertTrue(swords.size() == 1, "one workpiece expected, got " + swords.size());
            helper.assertTrue(SpikeLine.operationsOf(swords.getFirst()).equals(List.of(SpikeLine.PRESS)),
                    "press log after waiting: " + SpikeLine.operationsOf(swords.getFirst()));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 900)
    public static void pressStillMakesPlatesFromPlainIngots(GameTestHelper helper) {
        belt(helper, true);
        placePress(helper, PRESS, true);
        helper.runAfterDelay(20, () -> putOnBelt(helper, new ItemStack(Items.IRON_INGOT), 0.5F));
        helper.succeedWhen(() -> helper.assertTrue(!items(helper, AllItems.IRON_SHEET.get()).isEmpty(),
                "Create's own pressing recipe must still work; items: " + allItems(helper) + " | " + describe(helper)));
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void unpoweredPressLeavesAWorkpieceAlone(GameTestHelper helper) {
        belt(helper, true);
        placePress(helper, PRESS, false);
        helper.runAfterDelay(20, () -> putOnBelt(helper, workpiece(), 0.5F));
        helper.runAfterDelay(300, () -> {
            List<ItemStack> swords = items(helper, Items.IRON_SWORD);
            helper.assertTrue(swords.size() == 1, "workpiece must not be consumed, got " + swords.size());
            helper.assertTrue(SpikeLine.operationsOf(swords.getFirst()).isEmpty(),
                    "an unpowered press must not stamp: " + SpikeLine.operationsOf(swords.getFirst()));
            helper.succeed();
        });
    }

    // --- Fan scene: fan at the back of a lava block, belt in front of the lava. -------------------------------------

    private static final BlockPos FAN = new BlockPos(2, 1, 4);

    /** A fan at the given x with {@code frontBlock} in front of it (lava, water, fire...), blowing across the belt. */
    private static void fanBehind(GameTestHelper helper, int x, BlockState frontBlock) {
        BlockPos fan = new BlockPos(x, 1, 4);
        helper.setBlock(fan, AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        helper.setBlock(fan.north(), frontBlock);
        helper.setBlock(fan.south(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
    }

    @GameTest(template = EMPTY, timeoutTicks = 1200)
    public static void fanBehindLavaStampsAWorkpieceOnTheBeltWithoutARecipe(GameTestHelper helper) {
        // The belt is not driven: bulk processing takes seconds, so the item has to dwell in the airflow.
        belt(helper, false);
        fanBehind(helper, 2, Blocks.LAVA.defaultBlockState());
        helper.runAfterDelay(20, () -> putOnBelt(helper, workpiece(), 2.5F));
        helper.succeedWhen(() -> {
            List<ItemStack> swords = items(helper, Items.IRON_SWORD);
            helper.assertTrue(swords.size() == 1, "exactly one workpiece must exist, got " + swords.size());
            helper.assertTrue(!SpikeLine.operationsOf(swords.getFirst()).isEmpty(),
                    "fan must have stamped the workpiece, log is empty | " + describe(helper));
            helper.assertTrue(SpikeLine.operationsOf(swords.getFirst()).equals(List.of(SpikeLine.BLAST)),
                    "log after blasting: " + SpikeLine.operationsOf(swords.getFirst()));
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 2000)
    public static void workpieceLeftInTheLavaAirflowIsStampedOnlyOnce(GameTestHelper helper) {
        belt(helper, false);
        fanBehind(helper, 2, Blocks.LAVA.defaultBlockState());
        helper.assertTrue(FanProcessingType.getAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 3))) instanceof SpikeFanBlastingType,
                "lava in front of a fan must resolve to the spike's blasting type");
        helper.runAfterDelay(20, () -> putOnBelt(helper, workpiece(), 2.5F));
        // The belt is stopped, so the item stays in the airflow for the whole wait: several bulk-processing cycles.
        helper.runAfterDelay(1500, () -> {
            List<ItemStack> swords = items(helper, Items.IRON_SWORD);
            helper.assertTrue(swords.size() == 1, "one workpiece expected, got " + swords.size());
            helper.assertTrue(SpikeLine.operationsOf(swords.getFirst()).equals(List.of(SpikeLine.BLAST)),
                    "log after dwelling in the flow: " + SpikeLine.operationsOf(swords.getFirst()) + " | " + describe(helper));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 1200)
    public static void fanBehindLavaStillBlastsPlainItems(GameTestHelper helper) {
        belt(helper, false);
        fanBehind(helper, 2, Blocks.LAVA.defaultBlockState());
        helper.runAfterDelay(20, () -> putOnBelt(helper, new ItemStack(Items.RAW_IRON), 2.5F));
        helper.succeedWhen(() -> helper.assertTrue(!items(helper, Items.IRON_INGOT).isEmpty(),
                "Create's own bulk blasting must still work; items: " + allItems(helper) + " | " + describe(helper)));
    }

    @GameTest(template = EMPTY, timeoutTicks = 1200)
    public static void fansBehindWaterOrFireLeaveAWorkpieceAlone(GameTestHelper helper) {
        belt(helper, false);
        // Water splashes, a campfire smokes: both are Create fans, neither is the blasting the workpiece waits for.
        fanBehind(helper, 1, Blocks.WATER.defaultBlockState());
        fanBehind(helper, 3, Blocks.CAMPFIRE.defaultBlockState());
        // Control: Create really resolves these two fans to its own types, not to the spike's blasting type.
        helper.assertTrue(FanProcessingType.getAt(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 3))) == AllFanProcessingTypes.SPLASHING,
                "water in front of a fan must resolve to splashing");
        helper.assertTrue(FanProcessingType.getAt(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))) == AllFanProcessingTypes.SMOKING,
                "a campfire in front of a fan must resolve to smoking");
        helper.runAfterDelay(20, () -> {
            putOnBelt(helper, workpiece(), 1.5F);
            putOnBelt(helper, workpiece(), 3.5F);
        });
        helper.runAfterDelay(600, () -> {
            List<ItemStack> swords = items(helper, Items.IRON_SWORD);
            helper.assertTrue(swords.size() == 2, "both workpieces must survive, got " + swords.size() + " | " + describe(helper));
            helper.assertTrue(swords.stream().allMatch(s -> SpikeLine.operationsOf(s).isEmpty()),
                    "wrong-kind fans must not stamp: " + swords.stream().map(SpikeLine::operationsOf).toList());
            helper.succeed();
        });
    }

    /**
     * Press and lava fan on one slow belt. The expected log is worked out from where the belt actually starts the item,
     * so the same scene proves both orders when the stations are swapped.
     */
    private static void orderScene(GameTestHelper helper, boolean pressAtX1) {
        belt(helper, true);
        // 1 rpm: the item dwells in each station for longer than a bulk-processing cycle.
        ((CreativeMotorBlockEntity) helper.getBlockEntity(BELT_START.north())).generatedSpeed.setValue(1);
        int pressX = pressAtX1 ? 1 : 3;
        int fanX = pressAtX1 ? 3 : 1;
        placePress(helper, new BlockPos(pressX, 3, 2), true);
        fanBehind(helper, fanX, Blocks.LAVA.defaultBlockState());
        helper.runAfterDelay(20, () -> {
            BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
            float start = Math.signum(controller.getDirectionAwareBeltMovementSpeed()) >= 0 ? 0.5F : controller.beltLength - 0.5F;
            double startX = BeltHelper.getVectorForOffset(controller, start).x;
            boolean pressFirst = Math.abs(helper.absolutePos(new BlockPos(pressX, 1, 2)).getX() + 0.5 - startX)
                    < Math.abs(helper.absolutePos(new BlockPos(fanX, 1, 2)).getX() + 0.5 - startX);
            List<String> expected = pressFirst ? List.of(SpikeLine.PRESS, SpikeLine.BLAST) : List.of(SpikeLine.BLAST, SpikeLine.PRESS);
            putOnBelt(helper, workpiece(), 0.5F);
            helper.succeedWhen(() -> {
                List<ItemStack> swords = items(helper, Items.IRON_SWORD);
                helper.assertTrue(swords.size() == 1, "one workpiece expected, got " + swords.size() + " | " + describe(helper));
                helper.assertTrue(SpikeLine.operationsOf(swords.getFirst()).equals(expected),
                        "expected " + expected + " but log is " + SpikeLine.operationsOf(swords.getFirst()) + " | " + describe(helper));
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 6000)
    public static void workpieceLogsOperationsInTheOrderTheMachinesFire(GameTestHelper helper) {
        orderScene(helper, true);
    }

    @GameTest(template = EMPTY, timeoutTicks = 6000)
    public static void workpieceLogsOperationsInTheOrderTheMachinesFireWhenStationsAreSwapped(GameTestHelper helper) {
        orderScene(helper, false);
    }

    /**
     * KNOWN LIMITATION of the no-mixin press route. The wrapper is installed from block-update and chunk-load events;
     * a press placed with flags that skip neighbour updates (structure placement, some commands) is not wrapped until
     * its chunk loads again. This test records the behaviour so the spike document and the code cannot drift apart.
     */
    @GameTest(template = EMPTY)
    public static void pressPlacedWithoutNeighbourUpdatesIsNotWrappedYet(GameTestHelper helper) {
        BlockState press = AllBlocks.MECHANICAL_PRESS.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        helper.getLevel().setBlock(helper.absolutePos(PRESS), press, 2);
        MechanicalPressBlockEntity be = helper.getBlockEntity(PRESS);
        helper.assertTrue(!be.getPressingBehaviour().specifics.getClass().getSimpleName().equals("Wrapper"),
                "if this now passes the wrapper, the limitation is gone: update docs/spike-create-hooks.md");
        // A later block update next to it does install the wrapper.
        helper.setBlock(PRESS.east(), Blocks.STONE);
        helper.assertTrue(be.getPressingBehaviour().specifics.getClass().getSimpleName().equals("Wrapper"),
                "a neighbour update must install the wrapper");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theModRegistersNoRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> own = level.getServer().getRecipeManager().getRecipes().stream()
                .filter(h -> h.id().getNamespace().equals(BlueprintForge.MOD_ID))
                .map(h -> h.id().toString()).toList();
        helper.assertTrue(own.isEmpty(), "the mod must not register any recipe, found " + own);
        helper.succeed();
    }
}
