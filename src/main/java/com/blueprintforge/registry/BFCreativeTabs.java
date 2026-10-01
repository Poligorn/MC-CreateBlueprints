package com.blueprintforge.registry;

import java.util.function.Supplier;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.item.BlueprintItem;

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
                output.accept(BFItems.PROJECT_BUREAU.get());
                BlueprintRegistry.all().forEach((id, definition) ->
                        output.accept(BlueprintItem.createInstance(id, definition, BlueprintData.UNISSUED)));
            })
            .build());

    private BFCreativeTabs() {
    }
}
