package com.blueprintforge.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.blueprintforge.logic.EfficiencyMath;
import com.blueprintforge.recipe.BasinProduction;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;

/** TE shortens the recipe's own processing time. Shaft speed is still applied by the mixer afterwards. */
@Mixin(MechanicalMixerBlockEntity.class)
public abstract class MixerDurationMixin {
    @Redirect(method = "tick",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/processing/recipe/StandardProcessingRecipe;getProcessingDuration()I"),
            remap = false)
    private int blueprintforge$scaleDuration(StandardProcessingRecipe<?> recipe) {
        int base = recipe.getProcessingDuration();
        BasinBlockEntity basin = ((BasinAccess) (Object) this).blueprintforge$basin().orElse(null);
        if (basin == null || !BasinProduction.handles(recipe)) {
            return base;
        }
        int te = BasinProduction.timeEfficiency(basin.getInputInventory(), recipe.getIngredients());
        return EfficiencyMath.productionTicks(base, te);
    }
}
