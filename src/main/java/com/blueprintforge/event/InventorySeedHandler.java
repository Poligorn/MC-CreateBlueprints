package com.blueprintforge.event;

import java.util.List;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.research.SeedAssigner;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Gives every blueprint in a player's inventory its seed, checked once a second on the server. */
@EventBusSubscriber(modid = BlueprintForge.MOD_ID)
public final class InventorySeedHandler {
    private static final int INTERVAL_TICKS = 20;

    private InventorySeedHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % INTERVAL_TICKS != 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        List<ItemStack> stacks = new java.util.ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            stacks.add(inventory.getItem(slot));
        }
        SeedAssigner.assignAll(stacks, player.getRandom());
    }
}
