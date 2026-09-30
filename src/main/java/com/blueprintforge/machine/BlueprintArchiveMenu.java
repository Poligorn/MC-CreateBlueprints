package com.blueprintforge.machine;

import org.jetbrains.annotations.Nullable;

import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class BlueprintArchiveMenu extends AbstractContainerMenu {
    public static final int DOCUMENT_SLOT_X = 80;
    public static final int DOCUMENT_SLOT_Y = 35;
    public static final int INVENTORY_Y = 108;

    private final ContainerLevelAccess access;
    @Nullable
    private final BlueprintArchiveBlockEntity archive;

    public BlueprintArchiveMenu(int containerId, Inventory inventory, BlueprintArchiveBlockEntity archive) {
        this(containerId, inventory, archive.getBlueprintSlot(), archive.getBlockPos(), archive);
    }

    public static BlueprintArchiveMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        BlueprintArchiveBlockEntity archive = inventory.player.level().getBlockEntity(pos) instanceof BlueprintArchiveBlockEntity be ? be : null;
        return new BlueprintArchiveMenu(containerId, inventory, new BlueprintArchiveBlockEntity.DocumentSlot(), pos, archive);
    }

    private BlueprintArchiveMenu(int containerId, Inventory inventory, IItemHandler documentSlot, BlockPos pos, @Nullable BlueprintArchiveBlockEntity archive) {
        super(BFMenus.BLUEPRINT_ARCHIVE.get(), containerId);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.archive = archive;

        addSlot(new SlotItemHandler(documentSlot, 0, DOCUMENT_SLOT_X, DOCUMENT_SLOT_Y));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
    }

    /** Network speed in RPM as the client block entity sees it; zero when the archive is not turning. */
    public float networkSpeed() {
        return archive == null ? 0 : archive.getSpeed();
    }

    public ItemStack document() {
        return getSlot(0).getItem();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!BlueprintArchiveBlockEntity.acceptsDocument(stack) || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, BFBlocks.BLUEPRINT_ARCHIVE.get());
    }
}
