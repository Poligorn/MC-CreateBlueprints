package com.blueprintforge.item;

import java.util.List;
import java.util.Optional;

import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFItems;
import com.blueprintforge.research.ResearchBlueprint;
import com.blueprintforge.research.ResearchTarget;
import com.blueprintforge.research.ResearchTier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A blueprint of the November concept: one target and one tier, with an official icon picked from the component.
 * Separate from the older {@link BlueprintItem} of the Archive prototype, which stays as it is.
 */
public class ResearchBlueprintItem extends Item {
    public ResearchBlueprintItem(Properties properties) {
        super(properties);
    }

    public static Optional<ResearchBlueprint> data(ItemStack stack) {
        return Optional.ofNullable(stack.get(BFComponents.RESEARCH_BLUEPRINT.get()));
    }

    public static ItemStack create(ResourceLocation tierId, ResearchTier tier, ResourceLocation targetId, ResearchTarget target, boolean copy) {
        ItemStack stack = new ItemStack(BFItems.RESEARCH_BLUEPRINT.get());
        stack.set(BFComponents.RESEARCH_BLUEPRINT.get(), ResearchBlueprint.of(tierId, tier, targetId, target, copy));
        return stack;
    }

    /** Translation key of a tier name, derived from its id so the client can name it without knowing the datapack. */
    public static Component tierName(ResourceLocation tierId) {
        return Component.translatable("research.tier." + tierId.getNamespace() + "." + tierId.getPath());
    }

    public static Component targetName(ResourceLocation targetId) {
        return BuiltInRegistries.ITEM.getOptional(targetId)
                .<Component>map(item -> Component.translatable(item.getDescriptionId()))
                .orElseGet(() -> Component.literal(targetId.toString()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        data(stack).ifPresentOrElse(blueprint -> {
            tooltip.add(Component.translatable("tooltip.blueprintforge.research.target", targetName(blueprint.item())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.blueprintforge.research.tier", tierName(blueprint.tier())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(blueprint.copy() ? "tooltip.blueprintforge.research.copy" : "tooltip.blueprintforge.research.original")
                    .withStyle(blueprint.copy() ? ChatFormatting.DARK_GRAY : ChatFormatting.GOLD));
        }, () -> tooltip.add(Component.translatable("tooltip.blueprintforge.research.blank").withStyle(ChatFormatting.DARK_GRAY)));
    }
}
