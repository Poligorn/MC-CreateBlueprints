package com.blueprintforge.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFMenus;

/** One slot. The Archive stores a document and does nothing else. */
public class BlueprintArchiveMenu extends AbstractContainerMenu {
    public static final int SLOT_X = 80;
    public static final int SLOT_Y = 35;
    private static final int PLAYER_SLOT = 1;

    private final ContainerLevelAccess access;

    public BlueprintArchiveMenu(int containerId, Inventory inventory, BlueprintArchiveBlockEntity archive) {
        this(containerId, inventory, archive.getDocumentSlot(), archive.getBlockPos());
    }

    public static BlueprintArchiveMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        ItemStackHandler slot = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int index, ItemStack stack) {
                return ProjectBureauBlockEntity.acceptsDocument(stack);
            }
        };
        return new BlueprintArchiveMenu(containerId, inventory, slot, pos);
    }

    private BlueprintArchiveMenu(int containerId, Inventory inventory, ItemStackHandler document, BlockPos pos) {
        super(BFMenus.BLUEPRINT_ARCHIVE.get(), containerId);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        addSlot(new SlotItemHandler(document, 0, SLOT_X, SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < PLAYER_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_SLOT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, BFBlocks.BLUEPRINT_ARCHIVE.get());
    }
}
