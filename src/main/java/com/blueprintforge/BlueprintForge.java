package com.blueprintforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blueprintforge.compat.createapi.CreateSpike;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFCreativeTabs;
import com.blueprintforge.registry.BFItems;
import com.blueprintforge.registry.BFMenus;
import com.blueprintforge.research.ResearchDataLoader;
import com.blueprintforge.terminal.TerminalBlockEntity;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@Mod(BlueprintForge.MOD_ID)
public final class BlueprintForge {
    public static final String MOD_ID = "blueprintforge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public BlueprintForge(IEventBus modBus) {
        BFComponents.COMPONENTS.register(modBus);
        BFBlocks.BLOCKS.register(modBus);
        BFBlocks.BLOCK_ENTITIES.register(modBus);
        BFItems.ITEMS.register(modBus);
        BFMenus.MENUS.register(modBus);
        BFCreativeTabs.TABS.register(modBus);
        modBus.addListener(BlueprintForge::registerCapabilities);

        NeoForge.EVENT_BUS.addListener(BlueprintForge::addReloadListeners);
        CreateSpike.register(modBus, NeoForge.EVENT_BUS);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ResearchDataLoader(event.getRegistryAccess()));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BFBlocks.TERMINAL_ENTITY.get(), TerminalBlockEntity::handlerFor);
    }
}
