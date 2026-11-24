package com.blueprintforge.registry;

import java.util.function.Supplier;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.item.ResearchBlueprintItem;
import com.blueprintforge.research.ResearchRegistry;
import java.util.TreeMap;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One stack per loaded definition, so a modpack author sees their datapack rather than a hardcoded list.
 * The stacks are unissued templates; {@link com.blueprintforge.event.CreativeIssueHandler} gives each taken copy
 * its own UUID and owner.
 */
public final class BFCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlueprintForge.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.blueprintforge"))
            .icon(() -> new ItemStack(BFItems.BLUEPRINT_ARCHIVE.get()))
            .displayItems((parameters, output) -> {
                output.accept(BFItems.BLUEPRINT_ARCHIVE.get());
                output.accept(BFItems.TERMINAL.get());
                // Originals of every loaded tier and target. Copies are not offered here (0.2); /give makes them.
                // The client knows the research data only when it shares a JVM with the server: sync is 0.3.
                new TreeMap<>(ResearchRegistry.tiers()).forEach((tierId, tier) ->
                        new TreeMap<>(ResearchRegistry.targets()).forEach((targetId, target) ->
                                output.accept(ResearchBlueprintItem.create(tierId, tier, targetId, target, false))));
                BlueprintRegistry.all().forEach((id, definition) ->
                        output.accept(BlueprintItem.createInstance(id, definition, BlueprintData.UNISSUED)));
            })
            .build());

    private BFCreativeTabs() {
    }
}
