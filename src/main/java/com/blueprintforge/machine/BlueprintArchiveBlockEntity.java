package com.blueprintforge.machine;

import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.registry.BFBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Holds one blueprint document on a Create kinetic network. The document stays here when the owner logs
 * out and drops into the world when the block is broken.
 */
public class BlueprintArchiveBlockEntity extends KineticBlockEntity implements MenuProvider {
    private static final String BLUEPRINT_TAG = "Blueprint";

    private final ItemStackHandler blueprintSlot = new DocumentSlot() {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public BlueprintArchiveBlockEntity(BlockPos pos, BlockState state) {
        super(BFBlocks.BLUEPRINT_ARCHIVE_ENTITY.get(), pos, state);
    }

    /**
     * The document slot takes an issued original or copy; blanks, fragments, ancient blueprints and
     * creative templates without an instance id stay out.
     */
    public static boolean acceptsDocument(ItemStack stack) {
        return BlueprintItem.data(stack)
                .filter(data -> !data.isUnissued())
                .map(data -> data.clazz() == BlueprintClass.ORIGINAL || data.clazz() == BlueprintClass.COPY)
                .orElse(false);
    }

    public ItemStackHandler getBlueprintSlot() {
        return blueprintSlot;
    }

    public ItemStack getDocument() {
        return blueprintSlot.getStackInSlot(0);
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
}
