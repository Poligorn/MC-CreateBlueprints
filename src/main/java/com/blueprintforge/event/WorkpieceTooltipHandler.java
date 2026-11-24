package com.blueprintforge.event;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.compat.createapi.SpikeLine;
import com.blueprintforge.registry.BFComponents;

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
        if (stack.has(BFComponents.RESEARCH_RUN.get()) && SpikeLine.isWorkpiece(stack)) {
            event.getToolTip().add(Math.min(1, event.getToolTip().size()),
                    Component.translatable("tooltip.blueprintforge.workpiece", SpikeLine.operationsOf(stack).size()).withStyle(ChatFormatting.GOLD));
        }
    }
}
