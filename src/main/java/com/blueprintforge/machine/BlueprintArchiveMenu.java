package com.blueprintforge.machine;

import org.jetbrains.annotations.Nullable;

import com.blueprintforge.logic.ResearchAxis;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

public class BlueprintArchiveMenu extends AbstractContainerMenu {
    public static final int DOCUMENT_SLOT_X = 80;
    public static final int DOCUMENT_SLOT_Y = 32;
    public static final int MATERIAL_SLOT_Y = 158;
    public static final int OUTPUT_SLOT_X = 152;
    public static final int OUTPUT_SLOT_Y = 32;
    public static final int INVENTORY_Y = 204;
    public static final int OUTPUT_SLOT = 1 + BlueprintArchiveBlockEntity.MATERIAL_SLOTS;
    public static final int PLAYER_SLOT = OUTPUT_SLOT + 1;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    @Nullable
    private final BlueprintArchiveBlockEntity archive;

    public BlueprintArchiveMenu(int containerId, Inventory inventory, BlueprintArchiveBlockEntity archive) {
        this(containerId, inventory, archive.getBlueprintSlot(), archive.getMaterials(), archive.getOutput(), archive.researchData(),
                archive.getBlockPos(), archive);
    }

    public static BlueprintArchiveMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        BlueprintArchiveBlockEntity archive = inventory.player.level().getBlockEntity(pos) instanceof BlueprintArchiveBlockEntity be ? be : null;
        return new BlueprintArchiveMenu(containerId, inventory, new BlueprintArchiveBlockEntity.DocumentSlot(),
                new BlueprintArchiveBlockEntity.MaterialSlot(), new BlueprintArchiveBlockEntity.OutputSlot(),
                new SimpleContainerData(BlueprintArchiveBlockEntity.DATA_COUNT), pos, archive);
    }

    private BlueprintArchiveMenu(int containerId, Inventory inventory, IItemHandler documentSlot, IItemHandler materials,
                                 IItemHandler output, ContainerData data, BlockPos pos, @Nullable BlueprintArchiveBlockEntity archive) {
        super(BFMenus.BLUEPRINT_ARCHIVE.get(), containerId);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.archive = archive;
        this.data = data;

        addSlot(new SlotItemHandler(documentSlot, 0, DOCUMENT_SLOT_X, DOCUMENT_SLOT_Y));
        for (int slot = 0; slot < BlueprintArchiveBlockEntity.MATERIAL_SLOTS; slot++) {
            int column = slot % 8;
            int row = slot / 8;
            addSlot(new SlotItemHandler(materials, slot, 16 + column * 18, MATERIAL_SLOT_Y + row * 18));
        }
        addSlot(new SlotItemHandler(output, 0, OUTPUT_SLOT_X, OUTPUT_SLOT_Y));
        addDataSlots(data);

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

    public int datum(int index) {
        return data.get(index);
    }

    public ItemStack document() {
        return getSlot(0).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (archive == null || archive.getLevel() == null || archive.getLevel().isClientSide) {
            return false;
        }
        if (id == 0) {
            archive.tryStart(ResearchAxis.MATERIAL, player);
            return true;
        }
        if (id == 1) {
            archive.tryStart(ResearchAxis.TIME, player);
            return true;
        }
        if (id == 2) {
            archive.tryCopy(player);
            return true;
        }
        if (id == 3) {
            archive.nudgeRuns(-1);
            return true;
        }
        if (id == 4) {
            archive.nudgeRuns(1);
            return true;
        }
        if (id == 5) {
            archive.tryAssemble(player);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_SLOT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (BlueprintArchiveBlockEntity.acceptsDocument(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 1, OUTPUT_SLOT, false)) {
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
