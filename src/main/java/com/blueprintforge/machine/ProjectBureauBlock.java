package com.blueprintforge.machine;

import com.blueprintforge.registry.BFBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Blueprint Laboratory. A shaft enters through the roof. Research, copying and fragment assembly
 * advance one datapack tick per game tick only while that shaft is turning.
 */
public class ProjectBureauBlock extends KineticBlock implements IBE<ProjectBureauBlockEntity> {
    public static final MapCodec<ProjectBureauBlock> CODEC = simpleCodec(ProjectBureauBlock::new);

    public ProjectBureauBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ProjectBureauBlock> codec() {
        return CODEC;
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
    public Class<ProjectBureauBlockEntity> getBlockEntityClass() {
        return ProjectBureauBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ProjectBureauBlockEntity> getBlockEntityType() {
        return BFBlocks.PROJECT_BUREAU_ENTITY.get();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            withBlockEntityDo(level, pos, ProjectBureauBlockEntity::dropContents);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ProjectBureauBlockEntity bureau && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(bureau, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public float getParticleTargetRadius() {
        return 0.65F;
    }

    @Override
    public float getParticleInitialRadius() {
        return 0.5F;
    }
}
