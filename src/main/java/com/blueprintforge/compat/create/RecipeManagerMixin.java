package com.blueprintforge.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.blueprintforge.recipe.BlueprintRecipeNormalizer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void blueprintforge$normalizeAll(java.util.Map<ResourceLocation, JsonElement> recipes, ResourceManager manager,
                                              ProfilerFiller profiler, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        for (JsonElement element : recipes.values()) {
            if (element != null && element.isJsonObject()) {
                BlueprintRecipeNormalizer.normalize(element.getAsJsonObject());
            }
        }
    }

    @Inject(method = "fromJson", at = @At("HEAD"))
    private static void blueprintforge$normalize(ResourceLocation id, JsonObject json, HolderLookup.Provider registries,
                                                 CallbackInfoReturnable<RecipeHolder<?>> cir) {
        BlueprintRecipeNormalizer.normalize(json);
    }
}
