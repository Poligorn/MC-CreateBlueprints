package com.blueprintforge.client;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.item.ResearchBlueprintItem;
import com.blueprintforge.registry.BFItems;
import com.blueprintforge.registry.BFMenus;

import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = BlueprintForge.MOD_ID, dist = Dist.CLIENT)
public final class BlueprintForgeClient {
    /** Item model predicate of the research blueprint: 0 without a component, else type, copy flag and sprite tier. */
    public static final String VARIANT_PROPERTY = "variant";

    public BlueprintForgeClient(IEventBus modBus) {
        modBus.addListener(BlueprintForgeClient::clientSetup);
        modBus.addListener(BlueprintForgeClient::registerScreens);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(BFItems.RESEARCH_BLUEPRINT.get(), BlueprintForge.id(VARIANT_PROPERTY),
                (stack, level, entity, seed) -> ResearchBlueprintItem.data(stack).map(data -> (float) data.variant()).orElse(0.0F)));
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BFMenus.TERMINAL.get(), TerminalScreen::new);
    }
}
