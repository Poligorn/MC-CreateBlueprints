package com.blueprintforge.terminal;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The blueprint terminal of mockup v3: two blocks high, like a door. The lower half is the cabinet with the block entity,
 * the upper half the desk and the leaning screen. The brass-free port of the output is on the right of the lower half.
 */
public class TerminalBlock extends Block implements EntityBlock {
    public static final MapCodec<TerminalBlock> CODEC = simpleCodec(TerminalBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<TerminalScreenState> SCREEN = EnumProperty.create("screen", TerminalScreenState.class);

    // Shapes of the upper half facing north: the desk slab and the wedge of the housing behind the screen.
    private static final List<double[]> UPPER_BOXES = List.of(new double[] {0, 0, 0, 1, 0.125, 1}, new double[] {0, 0.125, 0.5, 1, 0.95, 1});
    private static final VoxelShape[] UPPER_SHAPES = new VoxelShape[4];

    static {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : UPPER_BOXES) {
                shape = Shapes.or(shape, rotatedBox(box, direction));
            }
            UPPER_SHAPES[direction.get2DDataValue()] = shape;
        }
    }

    public TerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(SCREEN, TerminalScreenState.IDLE));
    }

    /** A box given for the north-facing model, turned clockwise as many quarter turns as the facing is away from north. */
    private static VoxelShape rotatedBox(double[] box, Direction facing) {
        int turns = (facing.get2DDataValue() - Direction.NORTH.get2DDataValue() + 4) % 4;
        double minX = box[0];
        double minZ = box[2];
        double maxX = box[3];
        double maxZ = box[5];
        for (int i = 0; i < turns; i++) {
            double newMinX = 1 - maxZ;
            double newMaxX = 1 - minZ;
            double newMinZ = minX;
            double newMaxZ = maxX;
            minX = newMinX;
            maxX = newMaxX;
            minZ = newMinZ;
            maxZ = newMaxZ;
        }
        return Shapes.box(minX, box[1], minZ, maxX, box[4], maxZ);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, SCREEN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? Shapes.block() : UPPER_SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardsOtherHalf = direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP);
        if (towardsOtherHalf) {
            if (neighborState.is(this) && neighborState.getValue(HALF) != half) {
                return state.setValue(FACING, neighborState.getValue(FACING)).setValue(SCREEN, neighborState.getValue(SCREEN));
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Breaking the upper half breaks the lower one, which holds the drop and the contents; creative mode drops nothing.
        if (!level.isClientSide && !player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            level.destroyBlock(pos.below(), true, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (level.getBlockEntity(lower) instanceof TerminalBlockEntity terminal && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(terminal, lower);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
            terminal.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new TerminalBlockEntity(pos, state) : null;
    }
}
