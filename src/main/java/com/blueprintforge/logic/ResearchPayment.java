package com.blueprintforge.logic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.blueprintforge.data.BlueprintDefinition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Whether the stacks in the Archive's material slots cover one research step. Fluids are not a tank yet. */
public final class ResearchPayment {
    private ResearchPayment() {
    }

    public static boolean hasFluid(List<BlueprintDefinition.CostEntry> cost) {
        return cost.stream().anyMatch(entry -> entry.itemOrFluid().right().isPresent());
    }

    public static Map<ResourceLocation, Integer> tally(List<ItemStack> stacks) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    public static boolean covers(List<BlueprintDefinition.CostEntry> cost, Map<ResourceLocation, Integer> have) {
        if (hasFluid(cost)) {
            return false;
        }
        for (BlueprintDefinition.CostEntry entry : cost) {
            ResourceLocation item = entry.itemOrFluid().left().orElseThrow();
            if (have.getOrDefault(item, 0) < entry.amount()) {
                return false;
            }
        }
        return true;
    }
}
