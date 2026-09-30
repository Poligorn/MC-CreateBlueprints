package com.blueprintforge.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.recipe.BlueprintOutputApplicator;
import com.blueprintforge.recipe.BlueprintSlotIngredient;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

@Mixin(ShapedRecipe.class)
public class MechanicalCraftingMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), cancellable = true)
    private void blueprintforge$stamp(CraftingInput input, HolderLookup.Provider registries, CallbackInfoReturnable<ItemStack> cir) {
        if (!((Object) this instanceof MechanicalCraftingRecipe recipe)) {
            return;
        }
        ItemStack document = ItemStack.EMPTY;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!BlueprintSlotIngredient.isSlot(ingredient)) {
                continue;
            }
            for (int i = 0; i < input.size(); i++) {
                if (ingredient.test(input.getItem(i))) {
                    document = input.getItem(i);
                }
            }
        }
        BlueprintData data = BlueprintItem.data(document).orElse(null);
        BlueprintDefinition definition = data == null ? null : BlueprintRegistry.get(data.definitionId()).orElse(null);
        TierDefinition tier = data == null ? null : TierRegistry.get(data.tierId()).orElse(null);
        ItemStack result = cir.getReturnValue();
        if (data == null || definition == null || tier == null || result.isEmpty() || !(registries instanceof RegistryAccess access)) {
            return;
        }
        cir.setReturnValue(BlueprintOutputApplicator.apply(result.copy(), data.definitionId(), definition, tier, access));
    }
}
