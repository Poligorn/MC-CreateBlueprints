package com.blueprintforge.registry;

import java.util.TreeMap;
import java.util.function.Supplier;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.item.ResearchBlueprintItem;
import com.blueprintforge.research.ResearchRegistry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The terminal and one original blueprint per loaded tier and target, so a modpack author sees their datapack rather
 * than a hardcoded list. Copies are not offered here (0.2); /give makes them. The client knows the research data only
 * when it shares a JVM with the server: sync is 0.3.
 */
public final class BFCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlueprintForge.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.blueprintforge"))
            .icon(() -> new ItemStack(BFItems.TERMINAL.get()))
            .displayItems((parameters, output) -> {
                output.accept(BFItems.TERMINAL.get());
                new TreeMap<>(ResearchRegistry.tiers()).forEach((tierId, tier) ->
                        new TreeMap<>(ResearchRegistry.targets()).forEach((targetId, target) ->
                                output.accept(ResearchBlueprintItem.create(tierId, tier, targetId, target, false))));
            })
            .build());

    private BFCreativeTabs() {
    }
}
