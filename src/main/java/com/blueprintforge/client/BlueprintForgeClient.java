package com.blueprintforge.client;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.registry.BFCreativeTabs;
import com.blueprintforge.registry.BFItems;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = BlueprintForge.MOD_ID, dist = Dist.CLIENT)
public final class BlueprintForgeClient {
    /** Item model predicate: 0 blank, 1 original, 2 copy, 3 ancient, 4 fragment. */
    public static final String CLASS_PROPERTY = "class";

    public BlueprintForgeClient(IEventBus modBus) {
        modBus.addListener(BlueprintForgeClient::clientSetup);
        modBus.addListener(BlueprintForgeClient::registerScreens);
        modBus.addListener(BlueprintForgeClient::registerRenderers);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(BFItems.BLUEPRINT.get(), BlueprintForge.id(CLASS_PROPERTY),
                (stack, level, entity, seed) -> BlueprintItem.data(stack).map(data -> data.clazz().ordinal() + 1.0F).orElse(0.0F)));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BFBlocks.BLUEPRINT_ARCHIVE_ENTITY.get(), ArchivePressRenderer::new);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BFMenus.BLUEPRINT_ARCHIVE.get(), BlueprintArchiveScreen::new);
    }

    /** The creative tab lists loaded definitions, so it is rebuilt whenever the server sends new ones. */
    public static void onDefinitionsSynced() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        boolean operatorTab = minecraft.player.canUseGameMasterBlocks() && minecraft.options.operatorItemsTab().get();
        BFCreativeTabs.MAIN.get().buildContents(new CreativeModeTab.ItemDisplayParameters(
                minecraft.player.connection.enabledFeatures(), operatorTab, minecraft.level.registryAccess()));
    }
}
