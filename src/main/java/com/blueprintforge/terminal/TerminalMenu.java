package com.blueprintforge.terminal;

import org.jetbrains.annotations.Nullable;

import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class TerminalMenu extends AbstractContainerMenu {
    /** The one button of the screen: makes a workpiece from the target, or scores a workpiece that came back. */
    public static final int BUTTON_ACTION = 0;

    // Positions follow mockup v3, whose frame is 385 x 265 pixels.
    public static final int[] SLOT_X = {24, 24, 24};
    public static final int[] SLOT_Y = {37, 71, 105};
    public static final int INVENTORY_X = 47;
    public static final int INVENTORY_Y = 183;
    public static final int HOTBAR_Y = 241;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    @Nullable
    private final TerminalBlockEntity terminal;

    public TerminalMenu(int containerId, Inventory inventory, TerminalBlockEntity terminal) {
        this(containerId, inventory, terminal.inventory(), terminal.data, terminal.getBlockPos(), terminal);
    }

    public static TerminalMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        return new TerminalMenu(containerId, inventory, new TerminalSlots(), new SimpleContainerData(TerminalBlockEntity.DATA_COUNT), pos, null);
    }

    private TerminalMenu(int containerId, Inventory inventory, IItemHandler slots, ContainerData data, BlockPos pos, @Nullable TerminalBlockEntity terminal) {
        super(BFMenus.TERMINAL.get(), containerId);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = data;
        this.terminal = terminal;

        addSlot(new SlotItemHandler(slots, TerminalSlots.BLUEPRINT, SLOT_X[0], SLOT_Y[0]));
        addSlot(new SlotItemHandler(slots, TerminalSlots.ITEM, SLOT_X[1], SLOT_Y[1]));
        addSlot(new SlotItemHandler(slots, TerminalSlots.OUTPUT, SLOT_X[2], SLOT_Y[2]) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    public int data(int index) {
        return data.get(index);
    }

    @Override
    public void broadcastChanges() {
        if (terminal != null) {
            terminal.tickLine();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (terminal == null || id != BUTTON_ACTION) {
            return false;
        }
        Component error = terminal.performAction();
        if (error != null) {
            player.displayClientMessage(error, true);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int terminalSlots = TerminalSlots.COUNT;
        if (index < terminalSlots) {
            if (!moveItemStackTo(stack, terminalSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, terminalSlots, false)) {
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
        return stillValid(access, player, BFBlocks.TERMINAL.get());
    }
}
