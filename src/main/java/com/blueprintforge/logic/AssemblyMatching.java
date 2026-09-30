package com.blueprintforge.logic;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.blueprintforge.data.AssemblyRecipe;
import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.item.BlueprintItem;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Picks the assembly recipe the Archive's material slots currently satisfy. The smallest id wins a tie. */
public final class AssemblyMatching {
    private AssemblyMatching() {
    }

    public static Optional<ResourceLocation> match(Map<ResourceLocation, AssemblyRecipe> recipes, List<ItemStack> materials) {
        return recipes.entrySet().stream()
                .filter(entry -> satisfies(entry.getValue(), materials))
                .map(Map.Entry::getKey)
                .min(Comparator.naturalOrder());
    }

    public static boolean satisfies(AssemblyRecipe recipe, List<ItemStack> materials) {
        if (countFragments(recipe, materials) < recipe.fragmentsRequired()) {
            return false;
        }
        return ResearchPayment.covers(recipe.extraIngredients(), ResearchPayment.tally(materials));
    }

    public static int countFragments(AssemblyRecipe recipe, List<ItemStack> materials) {
        int count = 0;
        for (ItemStack stack : materials) {
            BlueprintData data = BlueprintItem.data(stack).orElse(null);
            if (data != null && data.clazz() == BlueprintClass.FRAGMENT && recipe.fragmentBlueprint().equals(data.definitionId())) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
