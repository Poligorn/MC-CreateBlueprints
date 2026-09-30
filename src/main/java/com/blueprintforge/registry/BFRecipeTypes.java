package com.blueprintforge.registry;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.recipe.BlueprintSlotIngredient;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class BFRecipeTypes {
    public static final DeferredRegister<net.neoforged.neoforge.common.crafting.IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.INGREDIENT_TYPES, BlueprintForge.MOD_ID);

    static {
        INGREDIENT_TYPES.register("blueprint_slot", () -> BlueprintSlotIngredient.TYPE);
    }

    private BFRecipeTypes() {
    }
}
