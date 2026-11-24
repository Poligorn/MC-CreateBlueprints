package com.blueprintforge.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * The terminal and every blueprint the datapack allows: an original and a copy for each target (a target belongs to one
 * tier), grouped by type and ordered by tier. The seed is given when a stack reaches an inventory. The client knows the research data only when
 * it shares a JVM with the server: sync is 0.3.
 */
public final class BFCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlueprintForge.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.blueprintforge"))
            .icon(() -> new ItemStack(BFItems.TERMINAL.get()))
            .displayItems((parameters, output) -> {
                output.accept(BFItems.TERMINAL.get());
                List<ResearchBlueprintItem.Entry> entries = new ArrayList<>();
                // A target belongs to one tier, so each target gives one original and one copy.
                ResearchRegistry.targets().forEach((targetId, target) -> ResearchRegistry.tier(target.tier()).ifPresent(tier ->
                        entries.add(new ResearchBlueprintItem.Entry(target.tier(), tier, targetId, target))));
                entries.sort(Comparator.comparingInt((ResearchBlueprintItem.Entry e) -> e.target().type().ordinal())
                        .thenComparingInt(e -> e.tier().spriteTier())
                        .thenComparing(e -> e.tierId())
                        .thenComparing(e -> e.targetId()));
                for (ResearchBlueprintItem.Entry entry : entries) {
                    output.accept(ResearchBlueprintItem.create(entry.tierId(), entry.tier(), entry.targetId(), entry.target(), false));
                    output.accept(ResearchBlueprintItem.create(entry.tierId(), entry.tier(), entry.targetId(), entry.target(), true));
                }
            })
            .build());

    private BFCreativeTabs() {
    }
}
