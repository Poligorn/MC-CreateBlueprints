package com.blueprintforge.machine;

import com.blueprintforge.registry.BFBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Storage for one blueprint. It is not a kinetic machine and it does not sit on a belt.
 * The document stays in the block entity after the player leaves, and breaking the block drops it.
 */
public class BlueprintArchiveBlock extends BaseEntityBlock {
    public static final MapCodec<BlueprintArchiveBlock> CODEC = simpleCodec(BlueprintArchiveBlock::new);

    public BlueprintArchiveBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<BlueprintArchiveBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlueprintArchiveBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BlueprintArchiveBlockEntity archive) {
            archive.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof BlueprintArchiveBlockEntity archive && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(archive, pos);
        }
        return InteractionResult.CONSUME;
    }
}
