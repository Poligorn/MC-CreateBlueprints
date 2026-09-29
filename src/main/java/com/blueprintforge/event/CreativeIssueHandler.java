package com.blueprintforge.event;

import com.blueprintforge.item.BlueprintItem;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Creative-tab blueprints are templates without an instance id. The first time a template shows up on the
 * server, in a player's inventory or thrown out of it, it becomes a real instance owned by that player,
 * so two stacks taken from the tab never share a UUID.
 */
public final class CreativeIssueHandler {
    private CreativeIssueHandler() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(CreativeIssueHandler::onPlayerTick);
        bus.addListener(CreativeIssueHandler::onEntityJoin);
    }

    /** Issues every template in the player's inventory. Returns how many stacks were issued. */
    public static int issueInventory(Player player) {
        Inventory inventory = player.getInventory();
        int issued = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (BlueprintItem.issueTemplate(inventory.getItem(slot), player)) {
                issued++;
            }
        }
        if (issued > 0) {
            inventory.setChanged();
        }
        return issued;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) {
            issueInventory(event.getEntity());
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        Entity thrower = item.getOwner();
        if (BlueprintItem.issueTemplate(item.getItem(), thrower instanceof Player player ? player : null)) {
            item.setItem(item.getItem().copy());
        }
    }
}
