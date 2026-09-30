package com.blueprintforge.logic;

import java.util.Optional;

import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.registry.BFComponents;

import net.minecraft.world.item.ItemStack;

/** What a blueprint slot leaves behind after one successful production run. */
public final class ProductionMath {
    private ProductionMath() {
    }

    /** Empty when the stack is consumed (last run of a copy or an ancient). Originals come back unchanged. */
    public static Optional<ItemStack> remainder(ItemStack stack) {
        BlueprintData data = BlueprintItem.data(stack).orElse(null);
        if (data == null || data.isUnissued()) {
            return Optional.empty();
        }
        if (data.clazz() == BlueprintClass.ORIGINAL) {
            return Optional.of(stack.copy());
        }
        if (data.clazz() != BlueprintClass.COPY && data.clazz() != BlueprintClass.ANCIENT) {
            return Optional.empty();
        }
        int left = data.runsRemaining() - 1;
        if (left <= 0) {
            return Optional.empty();
        }
        ItemStack kept = stack.copy();
        kept.set(BFComponents.BLUEPRINT.get(), data.withRuns(left));
        return Optional.of(kept);
    }

    /** Action-bar and tooltip warning once this many runs are left. */
    public static boolean warnRuns(int runsRemaining) {
        return runsRemaining == 5 || runsRemaining == 2 || runsRemaining == 1;
    }
}
