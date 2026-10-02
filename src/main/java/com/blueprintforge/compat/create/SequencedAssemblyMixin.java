package com.blueprintforge.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.blueprintforge.data.LineMark;
import com.blueprintforge.data.LineRegistry;
import com.blueprintforge.logic.LineContext;
import com.blueprintforge.logic.LineProduction;
import com.blueprintforge.registry.BFComponents;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

@Mixin(SequencedAssemblyRecipe.class)
public class SequencedAssemblyMixin {
    @Inject(method = "advance", at = @At("HEAD"), cancellable = true)
    private void blueprintforge$gate(ResourceLocation id, ItemStack input, RandomSource random, CallbackInfoReturnable<ItemStack> cir) {
        if (!LineRegistry.isLineRecipe(id)) {
            return;
        }
        if (input.get(BFComponents.LINE_MARK.get()) != null) {
            return;
        }
        LineContext.Frame frame = LineContext.frame();
        if (frame == null) {
            cir.setReturnValue(input);
            return;
        }
        if (frame.stamped() && frame.mark() != null) {
            return;
        }
        var mark = LineProduction.tryStamp(frame.level(), frame.deployerPos(), input, frame.stamp());
        if (mark.isEmpty()) {
            cir.setReturnValue(input);
            return;
        }
        LineContext.remember(mark.get());
    }

    @Inject(method = "advance", at = @At("RETURN"), cancellable = true)
    private void blueprintforge$carry(ResourceLocation id, ItemStack input, RandomSource random, CallbackInfoReturnable<ItemStack> cir) {
        if (!LineRegistry.isLineRecipe(id)) {
            return;
        }
        LineMark mark = input.get(BFComponents.LINE_MARK.get());
        if (mark == null && LineContext.frame() != null) {
            mark = LineContext.frame().mark();
        }
        if (mark == null) {
            return;
        }
        ItemStack result = cir.getReturnValue();
        if (result == input) {
            return;
        }
        if (result.get(AllDataComponents.SEQUENCED_ASSEMBLY) == null) {
            LineContext.Frame frame = LineContext.frame();
            if (frame != null) {
                cir.setReturnValue(LineProduction.finish(frame.level(), mark, random.nextFloat()));
            }
            return;
        }
        result.set(BFComponents.LINE_MARK.get(), mark);
    }
}
