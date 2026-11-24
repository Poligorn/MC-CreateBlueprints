package com.blueprintforge.terminal;

import com.blueprintforge.compat.createapi.SpikeLine;
import com.blueprintforge.item.ResearchBlueprintItem;
import com.blueprintforge.registry.BFComponents;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** The four slots of a terminal and what each accepts; shared by the block entity and the client-side menu. */
public class TerminalSlots extends ItemStackHandler {
    public static final int BLUEPRINT = 0;
    public static final int TARGET = 1;
    public static final int OUTPUT = 2;
    public static final int RETURN = 3;
    public static final int COUNT = 4;

    public TerminalSlots() {
        super(COUNT);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case BLUEPRINT -> ResearchBlueprintItem.data(stack).isPresent();
            // The terminal takes only the item of the blueprint it holds; the check lives in the block entity, which knows the blueprint.
            case TARGET -> !stack.isEmpty();
            case RETURN -> SpikeLine.isWorkpiece(stack) && stack.has(BFComponents.RESEARCH_RUN.get());
            default -> false;
        };
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }
}
