package com.blueprintforge.logic;

import com.blueprintforge.registry.BFComponents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Which belt stacks the Archive may rewrite. A miss passes through: the belt is not a trash slot.
 */
public final class ItemRemake {
    private ItemRemake() {
    }

    /**
     * One undamaged, unenchanted, not-yet-forged item of {@code target}. A larger stack is left alone so a
     * remake cannot eat or duplicate neighbours. An enchanted or damaged base is left alone so the table
     * ban and a free repair cannot be bypassed by sending the sword through the tunnel first.
     */
    public static boolean eligible(ItemStack stack, ResourceLocation target) {
        if (stack.isEmpty() || stack.getCount() != 1 || stack.isDamaged() || stack.isEnchanted()) {
            return false;
        }
        if (stack.has(BFComponents.FORGED.get())) {
            return false;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(target);
    }
}
