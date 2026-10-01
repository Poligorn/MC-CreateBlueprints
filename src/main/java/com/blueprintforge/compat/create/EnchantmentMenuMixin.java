package com.blueprintforge.compat.create;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.blueprintforge.BFConfig;
import com.blueprintforge.event.EnchantingHandler;
import com.blueprintforge.logic.EnchantPolicy;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * In {@code scaled}, table offers are clamped to the forged tier's enchantment-level cap.
 * Loot and mob equipment do not go through this menu method.
 */
@Mixin(EnchantmentMenu.class)
public class EnchantmentMenuMixin {
    @Inject(method = "getEnchantmentList", at = @At("RETURN"), cancellable = true)
    private void blueprintforge$clampScaledOffers(RegistryAccess registries, ItemStack stack, int slot, int level,
                                                   CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        if (BFConfig.enchantingMode() != EnchantPolicy.Mode.SCALED) {
            return;
        }
        int cap = EnchantPolicy.levelCap(EnchantPolicy.tierNumber(EnchantingHandler.tierIdOf(stack)));
        List<EnchantmentInstance> offers = cir.getReturnValue();
        if (offers == null || offers.isEmpty() || cap <= 0) {
            cir.setReturnValue(List.of());
            return;
        }
        List<EnchantmentInstance> clamped = new ArrayList<>();
        for (EnchantmentInstance offer : offers) {
            int next = EnchantPolicy.clampedOfferLevel(offer.level, offer.enchantment.value().getMinLevel(), cap);
            if (next > 0) {
                clamped.add(new EnchantmentInstance(offer.enchantment, next));
            }
        }
        cir.setReturnValue(clamped);
    }
}
