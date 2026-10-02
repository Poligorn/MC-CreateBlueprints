package com.blueprintforge.client;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.registry.BFCreativeTabs;
import com.blueprintforge.registry.BFItems;
import com.blueprintforge.registry.BFMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@Mod(value = BlueprintForge.MOD_ID, dist = Dist.CLIENT)
public final class BlueprintForgeClient {
    public BlueprintForgeClient(IEventBus modBus) {
        modBus.addListener(BlueprintForgeClient::registerScreens);
        modBus.addListener(BlueprintForgeClient::registerItemExtensions);
    }

    private static void registerItemExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new BlueprintIconRenderer();
                }
                return renderer;
            }
        }, BFItems.BLUEPRINT.get());
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BFMenus.BLUEPRINT_ARCHIVE.get(), BlueprintArchiveScreen::new);
        event.register(BFMenus.PROJECT_BUREAU.get(), ProjectBureauScreen::new);
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
