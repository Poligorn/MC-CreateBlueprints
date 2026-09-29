package com.blueprintforge.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** One frame from {@code tier/tiers.json}. */
public record TierDefinition(
        Component display,
        int color,
        boolean requiresBlueprint,
        double globalDurabilityMultiplier,
        int fittingSlots
) {
    public static final Codec<Integer> HEX_COLOR = Codec.STRING.comapFlatMap(TierDefinition::parseColor,
            rgb -> String.format("#%06X", rgb & 0xFFFFFF));

    public static final Codec<TierDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("display").forGetter(TierDefinition::display),
            HEX_COLOR.fieldOf("color").forGetter(TierDefinition::color),
            Codec.BOOL.fieldOf("requires_blueprint").forGetter(TierDefinition::requiresBlueprint),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("global_durability_multiplier").forGetter(TierDefinition::globalDurabilityMultiplier),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("fitting_slots").forGetter(TierDefinition::fittingSlots)
    ).apply(i, TierDefinition::new));

    private static DataResult<Integer> parseColor(String value) {
        if (value.length() != 7 || value.charAt(0) != '#') {
            return DataResult.error(() -> "Color must be #RRGGBB, got '" + value + "'");
        }
        try {
            return DataResult.success(Integer.parseInt(value.substring(1), 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Color must be #RRGGBB, got '" + value + "'");
        }
    }
}
