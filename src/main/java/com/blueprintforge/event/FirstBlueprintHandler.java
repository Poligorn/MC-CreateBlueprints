package com.blueprintforge.event;

import com.blueprintforge.item.BlueprintItem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** One saved chat line the first time a blueprint reaches a player's inventory. */
public final class FirstBlueprintHandler {
    private static final String SEEN = "blueprintforge_seen_blueprint";

    private FirstBlueprintHandler() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(FirstBlueprintHandler::onTick);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        if (player.getPersistentData().getBoolean(SEEN)) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (BlueprintItem.data(stack).isPresent()) {
                player.getPersistentData().putBoolean(SEEN, true);
                player.sendSystemMessage(Component.translatable("message.blueprintforge.first_blueprint"));
                return;
            }
        }
    }
}
