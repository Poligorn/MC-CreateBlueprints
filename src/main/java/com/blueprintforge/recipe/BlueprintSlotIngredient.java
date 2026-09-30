package com.blueprintforge.recipe;

import java.util.stream.Stream;

import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.registry.BFItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/**
 * The blueprint slot of a Create recipe. {@code accept_tag} selects which documents match.
 * {@code consume_on_use} spends a run of a copy or an ancient document; an original is never consumed.
 */
public record BlueprintSlotIngredient(TagKey<net.minecraft.world.item.Item> acceptTag, boolean consumeOnUse) implements ICustomIngredient {
    public static final MapCodec<BlueprintSlotIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TagKey.codec(BuiltInRegistries.ITEM.key()).fieldOf("accept_tag").forGetter(BlueprintSlotIngredient::acceptTag),
            Codec.STRING.optionalFieldOf("mode", "consume_on_use").forGetter(slot -> slot.consumeOnUse() ? "consume_on_use" : "keep")
    ).apply(i, (tag, mode) -> new BlueprintSlotIngredient(tag, !"keep".equals(mode))));

    public static final IngredientType<BlueprintSlotIngredient> TYPE = new IngredientType<>(CODEC);

    @Override
    public boolean test(ItemStack stack) {
        BlueprintData data = BlueprintItem.data(stack).orElse(null);
        if (data == null || data.isUnissued() || !matches(stack, data)) {
            return false;
        }
        if (data.clazz() == BlueprintClass.FRAGMENT) {
            return false;
        }
        if (data.clazz() == BlueprintClass.ORIGINAL) {
            return true;
        }
        return data.runsRemaining() > 0;
    }

    private boolean matches(ItemStack stack, com.blueprintforge.data.BlueprintData data) {
        return stack.is(acceptTag) || data.tierId().equals(acceptTag.location()) || data.definitionId().equals(acceptTag.location());
    }

    @Override
    public Stream<ItemStack> getItems() {
        return com.blueprintforge.data.BlueprintRegistry.all().entrySet().stream()
                .map(entry -> BlueprintItem.createInstance(entry.getKey(), entry.getValue(), java.util.UUID.nameUUIDFromBytes(entry.getKey().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .filter(this::test);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE;
    }

    public static boolean isSlot(Ingredient ingredient) {
        return ingredient.getCustomIngredient() instanceof BlueprintSlotIngredient;
    }

    /** A display stack so a recipe JSON can name the slot before datapacks have loaded. */
    public static ItemStack marker() {
        return new ItemStack(BFItems.BLUEPRINT.get());
    }
}
