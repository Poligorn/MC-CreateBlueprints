package com.blueprintforge.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.ForgedItemData;
import com.blueprintforge.data.OutputModifier;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.registry.BFComponents;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Writes a blueprint's output onto a base item: forged mark, attribute modifiers the item already holds,
 * and durability. No weapon-specific branch. Does not set a custom name: only an ancient blueprint may
 * name its output, and that roll is not applied in this phase. Ancient roll multipliers are not applied.
 */
public final class BlueprintOutputApplicator {
    private BlueprintOutputApplicator() {
    }

    public static ItemStack apply(ItemStack input, ResourceLocation blueprintId, BlueprintDefinition definition, TierDefinition tier,
                                   RegistryAccess registries) {
        ItemStack result = input.copy();
        int produced = Math.min(definition.output().count(), result.getMaxStackSize());
        if (produced != definition.output().count()) {
            BlueprintForge.LOGGER.debug("Output count {} does not fit on {}, clamped to {}",
                    definition.output().count(), BuiltInRegistries.ITEM.getKey(result.getItem()), produced);
        }
        result.setCount(produced);

        List<OutputModifier> applied = new ArrayList<>();
        ItemAttributeModifiers modifiers = attributeModifiers(result);
        int index = 0;
        for (OutputModifier modifier : definition.output().attributes()) {
            Optional<Holder.Reference<Attribute>> attribute = resolveAttribute(registries, modifier.attribute());
            if (attribute.isEmpty()) {
                BlueprintForge.LOGGER.debug("Skipping unknown attribute {} on blueprint {}", modifier.attribute(), blueprintId);
                index++;
                continue;
            }
            if (!holds(modifiers, attribute.get())) {
                BlueprintForge.LOGGER.debug("Skipping attribute {} on {}: the item does not hold it",
                        attribute.get().getKey().location(), BuiltInRegistries.ITEM.getKey(result.getItem()));
                index++;
                continue;
            }
            EquipmentSlotGroup slot = modifier.slot().orElse(slotAlreadyUsed(modifiers, attribute.get()));
            ResourceLocation modifierId = BlueprintForge.id("forged/" + attribute.get().getKey().location().getPath() + "/" + index);
            modifiers = modifiers.withModifierAdded(attribute.get(),
                    new AttributeModifier(modifierId, modifier.value(), modifier.mode().operation()), slot);
            applied.add(modifier);
            index++;
        }
        result.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);

        if (result.getMaxDamage() > 0) {
            double scaled = result.getMaxDamage() * tier.globalDurabilityMultiplier() * definition.output().durabilityMultiplier();
            result.set(DataComponents.MAX_DAMAGE, Math.max(1, (int) Math.round(scaled)));
        }

        result.set(BFComponents.FORGED.get(), new ForgedItemData(blueprintId, definition.tier(), List.copyOf(applied)));
        return result;
    }

    /**
     * A fresh tool keeps its attributes on the item prototype. Prefer those over an empty override so a craft
     * adds to the sword instead of replacing it with nothing.
     */
    private static ItemAttributeModifiers attributeModifiers(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (!modifiers.modifiers().isEmpty()) {
            return modifiers;
        }
        ItemAttributeModifiers prototype = stack.getItem().components().get(DataComponents.ATTRIBUTE_MODIFIERS);
        return prototype != null ? prototype : modifiers;
    }

    /**
     * 1.21.1 still registers attack damage as {@code minecraft:generic.attack_damage}. A datapack may also write
     * the shorter id used by later versions. Whichever of the two exists in this registry is the one applied.
     */
    private static Optional<Holder.Reference<Attribute>> resolveAttribute(RegistryAccess registries, ResourceLocation id) {
        var attributes = registries.lookupOrThrow(Registries.ATTRIBUTE);
        Optional<Holder.Reference<Attribute>> found = attributes.get(ResourceKey.create(Registries.ATTRIBUTE, id));
        if (found.isEmpty() && "minecraft".equals(id.getNamespace())) {
            String path = id.getPath();
            ResourceLocation alias = path.startsWith("generic.")
                    ? ResourceLocation.withDefaultNamespace(path.substring("generic.".length()))
                    : ResourceLocation.withDefaultNamespace("generic." + path);
            found = attributes.get(ResourceKey.create(Registries.ATTRIBUTE, alias));
            if (found.isPresent()) {
                BlueprintForge.LOGGER.debug("Attribute {} is not in this registry; output uses {}", id, alias);
            }
        }
        return found;
    }

    private static boolean holds(ItemAttributeModifiers modifiers, Holder<Attribute> attribute) {
        return modifiers.modifiers().stream().anyMatch(entry -> entry.attribute().equals(attribute)
                || entry.attribute().value() == attribute.value());
    }

    private static EquipmentSlotGroup slotAlreadyUsed(ItemAttributeModifiers modifiers, Holder<Attribute> attribute) {
        return modifiers.modifiers().stream()
                .filter(entry -> entry.attribute().equals(attribute))
                .map(ItemAttributeModifiers.Entry::slot)
                .findFirst()
                .orElse(EquipmentSlotGroup.ANY);
    }
}
