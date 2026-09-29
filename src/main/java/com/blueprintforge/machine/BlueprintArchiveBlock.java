package com.blueprintforge.machine;

import com.blueprintforge.registry.BFBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Archive: the only processing block of the mod. Built like a Create belt tunnel: it stands on a
 * straight horizontal belt segment, the belt runs through it along {@link #AXIS}, and the shaft comes in
 * through the roof. Its contents live in the block entity, not in player data.
 */
public class BlueprintArchiveBlock extends KineticBlock implements IBE<BlueprintArchiveBlockEntity> {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    // Same footprint as a Create tunnel: reaches 5 px down over the belt, channel open along the belt axis.
    private static final VoxelShape SHAPE_X = Shapes.join(Block.box(0, -5, 0, 16, 16, 16), Block.box(0, -5, 2, 16, 10, 14), BooleanOp.ONLY_FIRST);
    private static final VoxelShape SHAPE_Z = Shapes.join(Block.box(0, -5, 0, 16, 16, 16), Block.box(2, -5, 0, 14, 10, 16), BooleanOp.ONLY_FIRST);

    public BlueprintArchiveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(AXIS));
    }

    /** Axis of the straight horizontal belt segment below, or {@code null} if there is none. */
    public static Direction.Axis beltAxisBelow(LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!AllBlocks.BELT.has(below) || below.getValue(BeltBlock.SLOPE) != BeltSlope.HORIZONTAL) {
            return null;
        }
        return below.getValue(BeltBlock.HORIZONTAL_FACING).getAxis();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = beltAxisBelow(context.getLevel(), context.getClickedPos());
        return axis == null ? null : defaultBlockState().setValue(AXIS, axis);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return beltAxisBelow(level, pos) == state.getValue(AXIS);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    /** The axis follows the belt, so the wrench does not turn the Archive; sneak-wrenching still picks it up. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.UP;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        withBlockEntityDo(level, pos, archive -> {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(archive, pos);
            }
        });
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public Class<BlueprintArchiveBlockEntity> getBlockEntityClass() {
        return BlueprintArchiveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BlueprintArchiveBlockEntity> getBlockEntityType() {
        return BFBlocks.BLUEPRINT_ARCHIVE_ENTITY.get();
    }
}
