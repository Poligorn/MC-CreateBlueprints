package com.blueprintforge.event;

import com.blueprintforge.BFConfig;
import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.ForgedItemData;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.logic.EnchantPolicy;
import com.blueprintforge.registry.BFComponents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/**
 * Applies {@link EnchantPolicy} to the enchanting table and the anvil. The table block stays in the world;
 * only the interaction is refused, with an action bar message. The grindstone is left alone.
 */
public final class EnchantingHandler {
    public static final TagKey<Item> ENCHANTING_ALLOWED = ItemTags.create(BlueprintForge.id("enchanting_allowed"));
    public static final TagKey<Item> ENCHANTING_DENIED = ItemTags.create(BlueprintForge.id("enchanting_denied"));

    private static boolean villagerLimitLogged;

    private EnchantingHandler() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(EnchantingHandler::onRightClickBlock);
        bus.addListener(EnchantingHandler::onEnchantmentLevelSet);
        bus.addListener(EnchantingHandler::onAnvilUpdate);
        bus.addListener(EnchantingHandler::onVillagerTrades);
    }

    public static EnchantPolicy.Verdict verdict(ItemStack stack) {
        return EnchantPolicy.evaluate(BFConfig.enchantingMode(), BFConfig.DISABLE_BOOKS.get(), subject(stack));
    }

    public static EnchantPolicy.Subject subject(ItemStack stack) {
        ForgedItemData forged = stack.get(BFComponents.FORGED.get());
        EnchantPolicy.Forged forgedState;
        if (forged == null) {
            forgedState = EnchantPolicy.Forged.NONE;
        } else {
            forgedState = TierRegistry.get(forged.tierId())
                    .map(TierDefinition::requiresBlueprint)
                    .map(requires -> requires ? EnchantPolicy.Forged.BLUEPRINT_TIER : EnchantPolicy.Forged.FREE_TIER)
                    .orElse(EnchantPolicy.Forged.UNKNOWN_TIER);
        }
        return new EnchantPolicy.Subject(forgedState, stack.is(ENCHANTING_ALLOWED), stack.is(ENCHANTING_DENIED),
                stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK));
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof EnchantingTableBlock)) {
            return;
        }
        EnchantPolicy.Mode mode = BFConfig.enchantingMode();
        EnchantPolicy.Verdict verdict;
        if (mode == EnchantPolicy.Mode.FULL) {
            verdict = EnchantPolicy.Verdict.DENY_FULL;
        } else if (mode == EnchantPolicy.Mode.RESTRICTED && !event.getItemStack().isEmpty()) {
            verdict = verdict(event.getItemStack());
        } else {
            return;
        }
        if (!verdict.allowed()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            notify(event.getEntity(), verdict);
        }
    }

    private static void onEnchantmentLevelSet(EnchantmentLevelSetEvent event) {
        if (BFConfig.enchantingMode() == EnchantPolicy.Mode.OFF) {
            return;
        }
        EnchantPolicy.Verdict verdict = verdict(event.getItem());
        if (verdict.allowed()) {
            return;
        }
        event.setEnchantLevel(0);
        if (event.getEnchantRow() == 0 && event.getLevel() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                if (player.containerMenu instanceof EnchantmentMenu menu && menu.getSlot(0).getItem() == event.getItem()) {
                    notify(player, verdict);
                }
            }
        }
    }

    private static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (BFConfig.enchantingMode() == EnchantPolicy.Mode.OFF || !EnchantmentHelper.hasAnyEnchantments(event.getRight())) {
            return;
        }
        EnchantPolicy.Verdict verdict = verdict(event.getLeft());
        if (!verdict.allowed()) {
            event.setCanceled(true);
            notify(event.getPlayer(), verdict);
        }
    }

    private static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LIBRARIAN || !BFConfig.booksDisabled()) {
            return;
        }
        event.getTrades().values().forEach(list -> list.removeIf(listing -> listing instanceof VillagerTrades.EnchantBookForEmeralds));
        if (!villagerLimitLogged) {
            villagerLimitLogged = true;
            BlueprintForge.LOGGER.warn("Enchanted books are disabled: librarians no longer roll book trades, "
                    + "but villagers that already have such offers keep them");
        }
    }

    private static void notify(Player player, EnchantPolicy.Verdict verdict) {
        if (player instanceof ServerPlayer serverPlayer && verdict.messageKey() != null) {
            serverPlayer.displayClientMessage(Component.translatable(verdict.messageKey()), true);
        }
    }
}
