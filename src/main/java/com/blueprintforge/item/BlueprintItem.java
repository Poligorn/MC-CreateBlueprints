package com.blueprintforge.item;

import java.util.Optional;
import java.util.UUID;

import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFItems;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The single blueprint item. Class, tier and target come from the {@code blueprint} component; a stack
 * without it is a blank that produces nothing.
 */
public class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) {
        super(properties);
    }

    public static Optional<BlueprintData> data(ItemStack stack) {
        return Optional.ofNullable(stack.get(BFComponents.BLUEPRINT.get()));
    }

    /** A fresh instance of a definition: new UUID, ME/TE at the range minimum, runs by class. */
    public static ItemStack createInstance(ResourceLocation definitionId, BlueprintDefinition definition, UUID instanceId) {
        int copyRuns = definition.copy().map(BlueprintDefinition.CopyRules::defaultRuns).orElse(1);
        BlueprintData data = new BlueprintData(
                instanceId,
                definitionId,
                definition.clazz(),
                definition.tier(),
                definition.target(),
                definition.clazz().initialRuns(copyRuns),
                definition.materialEfficiencyOrFixed().min(),
                definition.timeEfficiencyOrFixed().min(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        ItemStack stack = new ItemStack(BFItems.BLUEPRINT.get());
        stack.set(BFComponents.BLUEPRINT.get(), data);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return data(stack).map(BlueprintItem::displayName).orElseGet(() -> Component.translatable(getDescriptionId(stack)));
    }

    public static Component displayName(BlueprintData data) {
        MutableComponent name = BlueprintRegistry.get(data.definitionId())
                .map(def -> def.display().name().copy())
                .orElseGet(() -> Component.translatable("item.blueprintforge.blueprint.unknown"));
        TierRegistry.get(data.tierId()).map(TierDefinition::color).ifPresent(name::withColor);
        return name;
    }
}
