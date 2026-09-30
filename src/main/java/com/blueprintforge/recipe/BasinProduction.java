package com.blueprintforge.recipe;

import java.util.ArrayList;
import java.util.List;

import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.logic.CopyRunWarning;
import com.blueprintforge.logic.EfficiencyMath;
import com.blueprintforge.logic.ProductionMath;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Basin recipes that carry a blueprint slot: ME trims item lines, the original comes back, the result is forged. */
public final class BasinProduction {
    private BasinProduction() {
    }

    public static boolean handles(Recipe<?> recipe) {
        if (!(recipe instanceof BasinRecipe basin) || !basin.getFluidIngredients().isEmpty()) {
            return false;
        }
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (BlueprintSlotIngredient.isSlot(ingredient)) {
                return true;
            }
        }
        return false;
    }

    public static boolean apply(BasinBlockEntity basin, Recipe<?> recipe) {
        if (!BasinRecipe.match(basin, recipe)) {
            return false;
        }
        IItemHandler input = basin.getInputInventory();
        ItemStack document = findDocument(input, recipe.getIngredients());
        BlueprintData data = BlueprintItem.data(document).orElse(null);
        if (data == null) {
            return false;
        }
        List<Ingredient> required = discount(recipe.getIngredients(), data.materialEfficiency());
        int[] taken = new int[input.getSlots()];
        ItemStack used = ItemStack.EMPTY;
        for (Ingredient ingredient : required) {
            boolean found = false;
            for (int slot = 0; slot < input.getSlots(); slot++) {
                ItemStack stack = input.getStackInSlot(slot);
                if (stack.getCount() <= taken[slot]) {
                    continue;
                }
                ItemStack probe = input.extractItem(slot, 1, true);
                if (!ingredient.test(probe)) {
                    continue;
                }
                if (BlueprintSlotIngredient.isSlot(ingredient)) {
                    used = probe.copy();
                }
                taken[slot]++;
                found = true;
                break;
            }
            if (!found) {
                return false;
            }
        }
        List<ItemStack> outputs = new ArrayList<>();
        if (recipe instanceof BasinRecipe basinRecipe) {
            outputs.addAll(basinRecipe.rollResults(basin.getLevel().getRandom()));
        }
        BlueprintDefinition definition = BlueprintRegistry.get(data.definitionId()).orElse(null);
        TierDefinition tier = TierRegistry.get(data.tierId()).orElse(null);
        if (definition != null && tier != null && basin.getLevel() != null) {
            List<ItemStack> stamped = new ArrayList<>();
            for (ItemStack result : outputs) {
                stamped.add(result.isEmpty() ? result
                        : BlueprintOutputApplicator.apply(result, data.definitionId(), definition, tier, basin.getLevel().registryAccess()));
            }
            outputs = stamped;
        }
        ProductionMath.remainder(used).ifPresent(outputs::add);
        if (!basin.acceptOutputs(outputs, List.<FluidStack>of(), true)) {
            return false;
        }
        for (int slot = 0; slot < taken.length; slot++) {
            if (taken[slot] > 0) {
                input.extractItem(slot, taken[slot], false);
            }
        }
        basin.acceptOutputs(outputs, List.<FluidStack>of(), false);
        if (basin.getLevel() instanceof ServerLevel server) {
            ProductionMath.remainder(used).ifPresent(left -> {
                BlueprintData after = BlueprintItem.data(left).orElse(null);
                if (after != null) {
                    CopyRunWarning.near(server, basin.getBlockPos(), after.runsRemaining());
                }
            });
            if (ProductionMath.remainder(used).isEmpty()) {
                BlueprintData before = BlueprintItem.data(used).orElse(null);
                if (before != null && before.runsRemaining() == 1) {
                    CopyRunWarning.near(server, basin.getBlockPos(), 1);
                }
            }
        }
        return true;
    }

    public static int timeEfficiency(IItemHandler inventory, NonNullList<Ingredient> ingredients) {
        return BlueprintItem.data(findDocument(inventory, ingredients)).map(BlueprintData::timeEfficiency).orElse(0);
    }

    private static ItemStack findDocument(IItemHandler inventory, List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            if (!BlueprintSlotIngredient.isSlot(ingredient)) {
                continue;
            }
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (ingredient.test(stack)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Keep every blueprint slot. Other lines shrink by ME, and a line of one stays one. */
    static List<Ingredient> discount(List<Ingredient> ingredients, int materialEfficiency) {
        List<Ingredient> materials = new ArrayList<>();
        List<Ingredient> slots = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (BlueprintSlotIngredient.isSlot(ingredient)) {
                slots.add(ingredient);
            } else {
                materials.add(ingredient);
            }
        }
        int keep = EfficiencyMath.consumed(materials.size(), materialEfficiency);
        List<Ingredient> required = new ArrayList<>(materials.subList(0, Math.min(keep, materials.size())));
        required.addAll(slots);
        return required;
    }
}
