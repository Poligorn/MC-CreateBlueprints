package com.blueprintforge.machine;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.registry.BFBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/** One issued blueprint, kept by the block. No shaft, no materials, no output. */
public class BlueprintArchiveBlockEntity extends BlockEntity implements MenuProvider, DocumentHolder {
    private static final String DOCUMENT_TAG = "Document";

    private final ItemStackHandler document = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return ProjectBureauBlockEntity.acceptsDocument(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                syncOccupancy();
            }
        }
    };

    private UUID heldInstance;
    private boolean adjusting;
    private boolean occupancyReady;

    public BlueprintArchiveBlockEntity(BlockPos pos, BlockState state) {
        super(BFBlocks.BLUEPRINT_ARCHIVE_ENTITY.get(), pos, state);
    }

    public ItemStackHandler getDocumentSlot() {
        return document;
    }

    public ItemStack getDocument() {
        return document.getStackInSlot(0);
    }

    @Override
    public boolean holdsInstance(UUID instanceId) {
        return BlueprintItem.data(getDocument()).map(data -> instanceId.equals(data.instanceId())).orElse(false);
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        release();
        ItemStack stack = document.getStackInSlot(0);
        if (!stack.isEmpty()) {
            document.setStackInSlot(0, ItemStack.EMPTY);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel && !occupancyReady) {
            occupancyReady = true;
            syncOccupancy();
        }
    }

    private void release() {
        if (level instanceof ServerLevel server && heldInstance != null) {
            ArchiveOccupancy.get(server).release(heldInstance, server, worldPosition);
            heldInstance = null;
        }
    }

    private void syncOccupancy() {
        if (!(level instanceof ServerLevel server) || adjusting) {
            return;
        }
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        UUID now = data == null || data.isUnissued() ? null : data.instanceId();
        if (heldInstance != null && !heldInstance.equals(now)) {
            ArchiveOccupancy.get(server).release(heldInstance, server, worldPosition);
            heldInstance = null;
        }
        if (now == null || now.equals(heldInstance)) {
            return;
        }
        if (!ArchiveOccupancy.get(server).claim(server, now, worldPosition)) {
            adjusting = true;
            ItemStack rejected = getDocument().copy();
            document.setStackInSlot(0, ItemStack.EMPTY);
            adjusting = false;
            heldInstance = null;
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), rejected);
            BlueprintForge.LOGGER.warn("Blueprint {} is already held by another block; dropped the duplicate at {}", now, worldPosition);
            return;
        }
        heldInstance = now;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(DOCUMENT_TAG, document.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(DOCUMENT_TAG)) {
            document.deserializeNBT(registries, tag.getCompound(DOCUMENT_TAG));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.blueprintforge.blueprint_archive");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BlueprintArchiveMenu(containerId, inventory, this);
    }
}
