package com.blueprintforge.machine;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.logic.ItemRemake;
import com.blueprintforge.logic.RemakeMath;
import com.blueprintforge.recipe.BlueprintOutputApplicator;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Holds one blueprint document on a Create kinetic network. While the network turns, a matching item on the
 * belt under this tunnel is rewritten into the blueprint's forged output. The document stays in the block.
 */
public class BlueprintArchiveBlockEntity extends KineticBlockEntity implements MenuProvider {
    private static final String BLUEPRINT_TAG = "Blueprint";
    /** Sentinel: the replacement is already queued, so a second tick must not spend another run. */
    private static final int FINISHED = Integer.MIN_VALUE;

    private final ItemStackHandler blueprintSlot = new DocumentSlot() {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** Countdown per belt item. Not saved: a reload restarts the hold and does not forge a partial result. */
    private final Map<TransportedItemStack, Job> jobs = new WeakHashMap<>();

    public BlueprintArchiveBlockEntity(BlockPos pos, BlockState state) {
        super(BFBlocks.BLUEPRINT_ARCHIVE_ENTITY.get(), pos, state);
    }

    /**
     * The document slot takes an issued original or a copy that still has runs. Blanks, fragments, ancient
     * blueprints, spent copies and creative templates stay out.
     */
    public static boolean acceptsDocument(ItemStack stack) {
        return BlueprintItem.data(stack)
                .filter(data -> !data.isUnissued())
                .map(data -> data.clazz() == BlueprintClass.ORIGINAL
                        || (data.clazz() == BlueprintClass.COPY && data.runsRemaining() > 0))
                .orElse(false);
    }

    public ItemStackHandler getBlueprintSlot() {
        return blueprintSlot;
    }

    public ItemStack getDocument() {
        return blueprintSlot.getStackInSlot(0);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new BeltProcessingBehaviour(this)
                .whenItemEnters((stack, handler) -> onBeltItem(stack, handler, false))
                .whileItemHeld((stack, handler) -> onBeltItem(stack, handler, true)));
    }

    private ProcessingResult onBeltItem(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, boolean held) {
        if (level == null || level.isClientSide) {
            return ProcessingResult.PASS;
        }
        Offer offer = offerFor(transported.stack).orElse(null);
        if (offer == null) {
            jobs.remove(transported);
            return ProcessingResult.PASS;
        }
        if (!held) {
            if (getSpeed() == 0) {
                return ProcessingResult.PASS;
            }
            int ticks = RemakeMath.processingTicks(Math.abs(getSpeed()), offer.processingTime());
            if (ticks <= 0) {
                return ProcessingResult.PASS;
            }
            jobs.put(transported, new Job(offer.definitionId(), ticks));
            return ProcessingResult.HOLD;
        }

        Job job = jobs.get(transported);
        if (job == null || !job.definitionId().equals(offer.definitionId())) {
            int ticks = RemakeMath.processingTicks(Math.abs(getSpeed()), offer.processingTime());
            job = new Job(offer.definitionId(), ticks > 0 ? ticks : 1);
            jobs.put(transported, job);
        }
        if (job.remaining() == FINISHED) {
            return ProcessingResult.HOLD;
        }
        if (getSpeed() == 0) {
            return ProcessingResult.HOLD;
        }
        int left = job.remaining() - 1;
        if (left > 0) {
            jobs.put(transported, new Job(job.definitionId(), left));
            return ProcessingResult.HOLD;
        }

        ItemStack forged = BlueprintOutputApplicator.apply(transported.stack, offer.definitionId(), offer.definition(), offer.tier(),
                level.registryAccess());
        TransportedItemStack replacement = transported.getSimilar();
        replacement.stack = forged;
        replacement.locked = false;
        handler.handleProcessingOnItem(transported, TransportedResult.convertTo(replacement));
        jobs.put(transported, new Job(job.definitionId(), FINISHED));
        spendCopyRun();
        return ProcessingResult.HOLD;
    }

    private java.util.Optional<Offer> offerFor(ItemStack stack) {
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        if (data == null || data.isUnissued()) {
            return java.util.Optional.empty();
        }
        if (data.clazz() != BlueprintClass.ORIGINAL && data.clazz() != BlueprintClass.COPY) {
            return java.util.Optional.empty();
        }
        if (data.clazz() == BlueprintClass.COPY && data.runsRemaining() <= 0) {
            return java.util.Optional.empty();
        }
        BlueprintDefinition definition = BlueprintRegistry.get(data.definitionId()).orElse(null);
        if (definition == null || definition.remake().isEmpty() || definition.target().isEmpty()) {
            return java.util.Optional.empty();
        }
        if (!ItemRemake.eligible(stack, definition.target().get())) {
            return java.util.Optional.empty();
        }
        TierDefinition tier = TierRegistry.get(definition.tier()).orElse(null);
        if (tier == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Offer(data.definitionId(), definition, tier, definition.remake().get().processingTime()));
    }

    /** Originals are never consumed. A copy loses one run when the forged item is queued, and the last run removes it. */
    private void spendCopyRun() {
        ItemStack document = getDocument();
        BlueprintData data = BlueprintItem.data(document).orElse(null);
        if (data == null || data.clazz() != BlueprintClass.COPY) {
            return;
        }
        int left = data.runsRemaining() - 1;
        if (left <= 0) {
            blueprintSlot.setStackInSlot(0, ItemStack.EMPTY);
        } else {
            ItemStack updated = document.copy();
            updated.set(BFComponents.BLUEPRINT.get(), data.withRuns(left));
            blueprintSlot.setStackInSlot(0, updated);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemStack document = blueprintSlot.getStackInSlot(0);
        if (!document.isEmpty() && level != null) {
            blueprintSlot.setStackInSlot(0, ItemStack.EMPTY);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), document);
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put(BLUEPRINT_TAG, blueprintSlot.serializeNBT(registries));
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        blueprintSlot.deserializeNBT(registries, compound.getCompound(BLUEPRINT_TAG));
        super.read(compound, registries, clientPacket);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.blueprintforge.blueprint_archive");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BlueprintArchiveMenu(containerId, inventory, this);
    }

    /** Single-slot handler shared by the block entity and the client-side menu mirror. */
    public static class DocumentSlot extends ItemStackHandler {
        public DocumentSlot() {
            super(1);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return acceptsDocument(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    }

    private record Job(ResourceLocation definitionId, int remaining) {
    }

    private record Offer(ResourceLocation definitionId, BlueprintDefinition definition, TierDefinition tier, int processingTime) {
    }
}
