package com.blueprintforge.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.blueprintforge.machine.BlueprintArchiveBlock;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * {@link BeltProcessingBehaviour#isBlocked} treats any solid block in the press gap as "do not process".
 * The Archive's walls are that block: they are the tunnel, not an obstruction. Combined with
 * {@link BeltInventoryMixin}, this is what lets a held belt item reach the Archive.
 */
@Mixin(value = BeltProcessingBehaviour.class, remap = false)
public class BeltProcessingBehaviourMixin {
    @Inject(method = "isBlocked", at = @At("HEAD"), cancellable = true)
    private static void blueprintforge$archiveIsTheTunnel(BlockGetter world, BlockPos processingSpace,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (world.getBlockState(processingSpace.above()).getBlock() instanceof BlueprintArchiveBlock) {
            cir.setReturnValue(false);
        }
    }
}
