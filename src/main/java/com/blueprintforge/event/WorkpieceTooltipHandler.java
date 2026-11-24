package com.blueprintforge.event;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.compat.createapi.SpikeLine;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.research.ResearchRun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Marks a workpiece and says how many steps it has recorded. Only the number: the order stays hidden. */
@EventBusSubscriber(modid = BlueprintForge.MOD_ID, value = Dist.CLIENT)
public final class WorkpieceTooltipHandler {
    private WorkpieceTooltipHandler() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResearchRun run = stack.get(BFComponents.RESEARCH_RUN.get());
        if (run == null || !SpikeLine.isWorkpiece(stack)) {
            return;
        }
        int recorded = SpikeLine.operationsOf(stack).size();
        // "N of M" says how many steps the machines wrote down, never which: the order stays hidden.
        Component line = run.steps() > 0
                ? Component.translatable("tooltip.blueprintforge.workpiece.of", recorded, run.steps())
                : Component.translatable("tooltip.blueprintforge.workpiece", recorded);
        event.getToolTip().add(Math.min(1, event.getToolTip().size()), line.copy().withStyle(recorded == run.steps() ? ChatFormatting.GREEN : ChatFormatting.GOLD));
    }
}
