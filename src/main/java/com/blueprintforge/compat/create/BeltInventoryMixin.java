package com.blueprintforge.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;

/**
 * Create's belt asks for {@link BeltProcessingBehaviour} two blocks above the belt, the gap a press hangs
 * over. The Archive is a tunnel one block above, so that lookup never sees it. There is no public callback
 * for a block standing on the belt itself.
 */
@Mixin(value = BeltInventory.class, remap = false)
public class BeltInventoryMixin {
    @Shadow
    BeltBlockEntity belt;

    @Inject(method = "getBeltProcessingAtSegment", at = @At("RETURN"), cancellable = true)
    private void blueprintforge$archiveOnTheBelt(int segment, CallbackInfoReturnable<BeltProcessingBehaviour> cir) {
        if (cir.getReturnValue() != null || belt.getLevel() == null) {
            return;
        }
        BlockPos tunnel = BeltHelper.getPositionForOffset(belt, segment).above();
        BeltProcessingBehaviour behaviour = BlockEntityBehaviour.get(belt.getLevel(), tunnel, BeltProcessingBehaviour.TYPE);
        if (behaviour != null) {
            cir.setReturnValue(behaviour);
        }
    }
}
