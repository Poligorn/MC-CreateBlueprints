package com.blueprintforge.terminal;

import com.blueprintforge.compat.createapi.SpikeLine;
import com.blueprintforge.item.ResearchBlueprintItem;
import com.blueprintforge.registry.BFComponents;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The three slots of a terminal and what each accepts; shared by the block entity and the client-side menu.
 * The item slot takes the target item and, later, the workpiece that comes back from the line.
 */
public class TerminalSlots extends ItemStackHandler {
    public static final int BLUEPRINT = 0;
    public static final int ITEM = 1;
    public static final int OUTPUT = 2;
    public static final int COUNT = 3;

    public TerminalSlots() {
        super(COUNT);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case BLUEPRINT -> ResearchBlueprintItem.data(stack).isPresent();
            // The block entity narrows this to the target item of the blueprint it holds; it knows the blueprint.
            case ITEM -> !stack.isEmpty() && (!SpikeLine.isWorkpiece(stack) || stack.has(BFComponents.RESEARCH_RUN.get()));
            default -> false;
        };
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }
}
