package com.blueprintforge.item;

import java.util.Optional;
import java.util.UUID;

import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.logic.ProductionMath;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFItems;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
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
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        ItemStack stack = new ItemStack(BFItems.BLUEPRINT.get());
        stack.set(BFComponents.BLUEPRINT.get(), data);
        return stack;
    }

    /**
     * Turns a creative-tab template into a real instance owned by {@code owner}. Returns false if the stack
     * is not a template. Owner is provenance only: the instance can still be dropped, traded or stolen.
     */
    public static boolean issueTemplate(ItemStack stack, @Nullable Player owner) {
        BlueprintData data = stack.get(BFComponents.BLUEPRINT.get());
        if (data == null || !data.isUnissued()) {
            return false;
        }
        stack.set(BFComponents.BLUEPRINT.get(), data.issuedTo(UUID.randomUUID(),
                Optional.ofNullable(owner).map(Player::getUUID),
                Optional.ofNullable(owner).map(player -> player.getGameProfile().getName())));
        return true;
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

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return ProductionMath.remainder(stack).isPresent();
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return ProductionMath.remainder(stack).orElse(ItemStack.EMPTY);
    }
}
