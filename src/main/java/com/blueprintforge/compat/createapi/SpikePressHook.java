package com.blueprintforge.compat.createapi;

import java.util.List;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.kinetics.press.PressingBehaviour.PressingBehaviourSpecifics;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Press hook without a mixin. {@link PressingBehaviour#specifics} is a public, non-final field that holds a public
 * interface, so a wrapper can stand in front of the press block entity's own implementation. Workpieces are handled
 * by the wrapper, everything else is passed through unchanged.
 *
 * The weak point is installation: Create gives no "press block entity created" callback, so the wrapper is put on
 * presses when their chunk loads and when a block update touches them. See docs/spike-create-hooks.md.
 */
public final class SpikePressHook {
    private SpikePressHook() {
    }

    static void register(IEventBus gameBus) {
        gameBus.addListener(ChunkEvent.Load.class, SpikePressHook::onChunkLoad);
        gameBus.addListener(BlockEvent.NeighborNotifyEvent.class, SpikePressHook::onBlockUpdate);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
            install(be);
        }
    }

    private static void onBlockUpdate(BlockEvent.NeighborNotifyEvent event) {
        LevelAccessor level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        install(level.getBlockEntity(event.getPos()));
        for (Direction side : event.getNotifiedSides()) {
            install(level.getBlockEntity(event.getPos().relative(side)));
        }
    }

    /** Puts the wrapper on a press block entity if it is not there yet. Returns true if the wrapper is in place. */
    public static boolean install(BlockEntity be) {
        if (!(be instanceof MechanicalPressBlockEntity press)) {
            return false;
        }
        PressingBehaviour behaviour = press.getPressingBehaviour();
        if (behaviour == null) {
            return false;
        }
        if (!(behaviour.specifics instanceof Wrapper)) {
            behaviour.specifics = new Wrapper(behaviour.specifics);
        }
        return true;
    }

    private record Wrapper(PressingBehaviourSpecifics inner) implements PressingBehaviourSpecifics {
        @Override
        public boolean tryProcessOnBelt(TransportedItemStack input, List<ItemStack> outputList, boolean simulate) {
            if (!SpikeLine.isWorkpiece(input.stack)) {
                return inner.tryProcessOnBelt(input, outputList, simulate);
            }
            if (!simulate && outputList != null) {
                outputList.add(SpikeLine.withOperation(input.stack, SpikeLine.PRESS));
            }
            return true;
        }

        @Override
        public boolean tryProcessInBasin(boolean simulate) {
            return inner.tryProcessInBasin(simulate);
        }

        @Override
        public boolean tryProcessInWorld(ItemEntity itemEntity, boolean simulate) {
            return inner.tryProcessInWorld(itemEntity, simulate);
        }

        @Override
        public boolean canProcessInBulk() {
            return inner.canProcessInBulk();
        }

        @Override
        public void onPressingCompleted() {
            inner.onPressingCompleted();
        }

        @Override
        public int getParticleAmount() {
            return inner.getParticleAmount();
        }

        @Override
        public float getKineticSpeed() {
            return inner.getKineticSpeed();
        }
    }
}
