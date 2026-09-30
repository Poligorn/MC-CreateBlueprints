package com.blueprintforge.event;

import java.util.UUID;

import com.blueprintforge.BFConfig;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.SourceDefinition;
import com.blueprintforge.data.SourceRegistry;
import com.blueprintforge.item.BlueprintItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

/** Mob and fishing sources. Ordinary mobs are not in the reference pack; fake players are refused by default. */
public final class WorldDropHandler {
    private WorldDropHandler() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(WorldDropHandler::onDrops);
        bus.addListener(WorldDropHandler::onFished);
    }

    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player) || !counts(player)) {
            return;
        }
        ResourceLocation entityId = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE).getKey(event.getEntity().getType());
        int looting = player.getMainHandItem().getEnchantments().getLevel(level.holderOrThrow(Enchantments.LOOTING));
        for (SourceRegistry.MobDropSource source : SourceRegistry.mobDrops()) {
            if (!source.drop().entities().contains(entityId)) {
                continue;
            }
            if (!matches(source.conditions(), level, event.getEntity().blockPosition())) {
                continue;
            }
            float chance = LootInjectionHandler.effectiveChance(source.drop().chance() + source.drop().lootingBonus() * looting,
                    BFConfig.rarityMultiplier());
            if (level.random.nextFloat() < chance) {
                offer(level, event.getEntity(), source.blueprint()).ifPresent(stack ->
                        event.getDrops().add(new ItemEntity(level, event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), stack)));
            }
        }
    }

    private static void onFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level) || !counts(player)) {
            return;
        }
        ItemStack rod = player.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD)
                ? player.getMainHandItem() : player.getOffhandItem();
        int luck = rod.getEnchantments().getLevel(level.holderOrThrow(Enchantments.LUCK_OF_THE_SEA));
        for (SourceRegistry.FishingSource source : SourceRegistry.fishing()) {
            if (!matches(source.conditions(), level, player.blockPosition())) {
                continue;
            }
            float chance = LootInjectionHandler.effectiveChance(source.fishing().chance() + source.fishing().luckScale() * luck,
                    BFConfig.rarityMultiplier());
            if (level.random.nextFloat() < chance) {
                offer(level, player, source.blueprint()).ifPresent(event.getDrops()::add);
            }
        }
    }

    private static boolean counts(Player player) {
        return BFConfig.allowFakePlayers() || !(player instanceof FakePlayer);
    }

    private static java.util.Optional<ItemStack> offer(ServerLevel level, LivingEntity where, ResourceLocation blueprint) {
        return BlueprintRegistry.get(blueprint).map(definition ->
                BlueprintItem.createInstance(blueprint, definition, UUID.randomUUID()));
    }

    private static boolean matches(SourceDefinition.Conditions conditions, ServerLevel level, BlockPos pos) {
        if (!conditions.modpackTagPresent()) {
            return false;
        }
        if (!conditions.dimensions().isEmpty() && !conditions.dimensions().contains(level.dimension().location())) {
            return false;
        }
        if (!conditions.difficulty().isEmpty() && !conditions.difficulty().contains(level.getDifficulty())) {
            return false;
        }
        if (conditions.minY().isPresent() && pos.getY() < conditions.minY().get()) {
            return false;
        }
        if (conditions.maxY().isPresent() && pos.getY() > conditions.maxY().get()) {
            return false;
        }
        if (conditions.biomes().isEmpty()) {
            return true;
        }
        Holder<Biome> biome = level.getBiome(pos);
        return conditions.biomes().stream().anyMatch(filter -> filter.matches(biome));
    }
}
