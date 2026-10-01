package com.blueprintforge.machine;

import com.blueprintforge.registry.BFBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The kinetic station. A shaft enters through the roof. The front is open so the press is visible.
 * It does not sit on a belt and it does not rewrite items that pass nearby.
 */
public class ProjectBureauBlock extends KineticBlock implements IBE<ProjectBureauBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 2, 16, 16),
            Block.box(14, 0, 0, 16, 16, 16),
            Block.box(2, 0, 14, 14, 16, 16),
            Block.box(2, 14, 0, 14, 16, 14),
            Block.box(2, 0, 0, 14, 2, 14));

    public ProjectBureauBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> rotate(SHAPE, 180);
            case WEST -> rotate(SHAPE, 90);
            case EAST -> rotate(SHAPE, 270);
            default -> SHAPE;
        };
    }

    private static VoxelShape rotate(VoxelShape shape, int degrees) {
        VoxelShape rotated = Shapes.empty();
        for (var box : shape.toAabbs()) {
            double x0 = box.minX;
            double z0 = box.minZ;
            double x1 = box.maxX;
            double z1 = box.maxZ;
            for (int step = 0; step < degrees; step += 90) {
                double nx0 = z0;
                double nz0 = 1.0 - x1;
                double nx1 = z1;
                double nz1 = 1.0 - x0;
                x0 = nx0;
                z0 = nz0;
                x1 = nx1;
                z1 = nz1;
            }
            double minX = Math.min(x0, x1) * 16;
            double maxX = Math.max(x0, x1) * 16;
            double minZ = Math.min(z0, z1) * 16;
            double maxZ = Math.max(z0, z1) * 16;
            rotated = Shapes.or(rotated, Block.box(minX, box.minY * 16, minZ, maxX, box.maxY * 16, maxZ));
        }
        return rotated;
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
        withBlockEntityDo(level, pos, bureau -> {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(bureau, pos);
            }
        });
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public Class<ProjectBureauBlockEntity> getBlockEntityClass() {
        return ProjectBureauBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ProjectBureauBlockEntity> getBlockEntityType() {
        return BFBlocks.PROJECT_BUREAU_ENTITY.get();
    }
}
