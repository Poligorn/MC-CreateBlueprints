package com.blueprintforge.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blueprintforge.logic.LineContext;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

@Mixin(BeltDeployerCallbacks.class)
public class DeployerLineMixin {
    @Inject(method = "activate", at = @At("HEAD"))
    private static void blueprintforge$open(TransportedItemStack item, TransportedItemStackHandlerBehaviour handler,
                                            DeployerBlockEntity deployer, Recipe<?> recipe, CallbackInfo ci) {
        if (!(deployer.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stamp = deployer.getPlayer() == null ? ItemStack.EMPTY : deployer.getPlayer().getMainHandItem();
        LineContext.open(level, deployer.getBlockPos(), stamp);
    }

    @Inject(method = "activate", at = @At("RETURN"))
    private static void blueprintforge$close(TransportedItemStack item, TransportedItemStackHandlerBehaviour handler,
                                             DeployerBlockEntity deployer, Recipe<?> recipe, CallbackInfo ci) {
        LineContext.close();
    }
}
