package com.blueprintforge.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.blueprintforge.recipe.BasinProduction;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;

import net.minecraft.world.item.crafting.Recipe;

@Mixin(BasinRecipe.class)
public class BasinRecipeMixin {
    @Inject(method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void blueprintforge$apply(BasinBlockEntity basin, Recipe<?> recipe, CallbackInfoReturnable<Boolean> cir) {
        if (BasinProduction.handles(recipe)) {
            cir.setReturnValue(BasinProduction.apply(basin, recipe));
        }
    }
}
