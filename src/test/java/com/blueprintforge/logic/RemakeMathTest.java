package com.blueprintforge.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.blueprintforge.data.ForgedItemData;
import com.blueprintforge.registry.BFComponents;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class RemakeMathTest {
    private static final ResourceLocation IRON_SWORD = ResourceLocation.withDefaultNamespace("iron_sword");

    @Test
    void processingTicksMatchTheCreateMixerFormula() {
        assertEquals(76, RemakeMath.processingTicks(16, 100));
        assertEquals(33, RemakeMath.processingTicks(32, 50));
        assertEquals(16, RemakeMath.processingTicks(256, 100));
        assertEquals(1, RemakeMath.processingTicks(512, 100));
        assertEquals(0, RemakeMath.processingTicks(0, 100));
        assertEquals(0, RemakeMath.processingTicks(16, 0));
    }

    @Test
    void onlyASinglePlainTargetIsEligible() {
        assertTrue(ItemRemake.eligible(new ItemStack(Items.IRON_SWORD), IRON_SWORD));
        assertFalse(ItemRemake.eligible(new ItemStack(Items.IRON_SWORD, 2), IRON_SWORD));
        assertFalse(ItemRemake.eligible(new ItemStack(Items.IRON_INGOT), IRON_SWORD));

        ItemStack damaged = new ItemStack(Items.IRON_SWORD);
        damaged.setDamageValue(10);
        assertFalse(ItemRemake.eligible(damaged, IRON_SWORD));

        ItemStack forged = new ItemStack(Items.IRON_SWORD);
        forged.set(BFComponents.FORGED.get(), new ForgedItemData(
                ResourceLocation.fromNamespaceAndPath("blueprintforge", "guild_blade"),
                ResourceLocation.fromNamespaceAndPath("blueprintforge", "tier2"),
                List.of()));
        assertFalse(ItemRemake.eligible(forged, IRON_SWORD));
        assertTrue(Optional.of(forged.get(BFComponents.FORGED.get())).isPresent());
    }
}
