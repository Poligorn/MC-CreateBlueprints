package com.blueprintforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blueprintforge.client.BlueprintForgeClient;
import com.blueprintforge.data.BlueprintDataLoader;
import com.blueprintforge.data.BlueprintSyncPayload;
import com.blueprintforge.command.BFCommands;
import com.blueprintforge.event.CreativeIssueHandler;
import com.blueprintforge.event.FirstBlueprintHandler;
import com.blueprintforge.event.WorldDropHandler;
import com.blueprintforge.event.EnchantingHandler;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFCreativeTabs;
import com.blueprintforge.registry.BFItems;
import com.blueprintforge.registry.BFLootModifiers;
import com.blueprintforge.registry.BFMenus;
import com.blueprintforge.registry.BFRecipeTypes;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@Mod(BlueprintForge.MOD_ID)
public final class BlueprintForge {
    public static final String MOD_ID = "blueprintforge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public BlueprintForge(IEventBus modBus, ModContainer container) {
        BFComponents.COMPONENTS.register(modBus);
        BFBlocks.BLOCKS.register(modBus);
        BFBlocks.BLOCK_ENTITIES.register(modBus);
        BFItems.ITEMS.register(modBus);
        BFMenus.MENUS.register(modBus);
        BFCreativeTabs.TABS.register(modBus);
        BFLootModifiers.SERIALIZERS.register(modBus);
        BFRecipeTypes.INGREDIENT_TYPES.register(modBus);

        container.registerConfig(ModConfig.Type.COMMON, BFConfig.SPEC, BFConfig.FILE_NAME);
        modBus.addListener(ModConfigEvent.Loading.class, BFConfig::onConfigLoad);
        modBus.addListener(ModConfigEvent.Reloading.class, BFConfig::onConfigLoad);
        modBus.addListener(BlueprintForge::registerPayloads);

        NeoForge.EVENT_BUS.addListener(BlueprintForge::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(BlueprintForge::syncDefinitions);
        EnchantingHandler.register(NeoForge.EVENT_BUS);
        CreativeIssueHandler.register(NeoForge.EVENT_BUS);
        WorldDropHandler.register(NeoForge.EVENT_BUS);
        FirstBlueprintHandler.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) -> BFCommands.register(event));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BlueprintDataLoader(event.getRegistryAccess()));
    }

    private static void syncDefinitions(OnDatapackSyncEvent event) {
        BlueprintSyncPayload payload = BlueprintSyncPayload.current();
        event.getRelevantPlayers()
                .filter(player -> player.connection.hasChannel(BlueprintSyncPayload.TYPE))
                .forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(BlueprintSyncPayload.TYPE, BlueprintSyncPayload.STREAM_CODEC, BlueprintForge::onDefinitionsSynced);
    }

    private static void onDefinitionsSynced(BlueprintSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // An integrated server already holds these definitions in the same JVM.
            if (ServerLifecycleHooks.getCurrentServer() == null) {
                BlueprintDataLoader.acceptSynced(payload.tiers(), payload.blueprints(), payload.research(),
                        payload.researchForBlueprint(), payload.assemblies());
            }
            if (FMLEnvironment.dist.isClient()) {
                BlueprintForgeClient.onDefinitionsSynced();
            }
        });
    }
}
