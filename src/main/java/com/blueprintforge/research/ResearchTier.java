package com.blueprintforge.research;

import java.util.HashSet;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * A blueprint tier, one file in {@code data/<ns>/research_tier/}.
 *
 * @param steps           how many steps a chain of this tier has
 * @param operations      ids of the operations a chain may use, for example {@code blueprintforge:press}. The ids are not
 *                        checked against known operations yet: that comes with the chain generator.
 * @param bonusMultiplier strength of the enhancement a successful run gives
 * @param spriteTier      which official icon colour the tier uses, 1 (blue) to 5 (gold); 1 when a file does not say
 */
public record ResearchTier(
        Component display,
        int color,
        IntRange steps,
        List<ResourceLocation> operations,
        double bonusMultiplier,
        AttemptCost attemptCost,
        int spriteTier
) {
    /** {@code "#RRGGBB"} in JSON, a packed RGB int in code. */
    public static final Codec<Integer> HEX_COLOR = Codec.STRING.comapFlatMap(ResearchTier::parseColor,
            rgb -> String.format("#%06X", rgb & 0xFFFFFF));

    public static final Codec<ResearchTier> CODEC = RecordCodecBuilder.<ResearchTier>create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("display").forGetter(ResearchTier::display),
            HEX_COLOR.fieldOf("color").forGetter(ResearchTier::color),
            IntRange.CODEC.fieldOf("steps").forGetter(ResearchTier::steps),
            ResourceLocation.CODEC.listOf().fieldOf("operations").forGetter(ResearchTier::operations),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("bonus_multiplier").forGetter(ResearchTier::bonusMultiplier),
            AttemptCost.CODEC.fieldOf("attempt_cost").forGetter(ResearchTier::attemptCost),
            Codec.intRange(1, 5).optionalFieldOf("sprite_tier", 1).forGetter(ResearchTier::spriteTier)
    ).apply(i, ResearchTier::new)).validate(ResearchTier::validate);

    /** A chain is never longer than this: past 16 steps a tier gets harder through resources, not length. */
    public static final int MAX_STEPS = 16;

    private static DataResult<ResearchTier> validate(ResearchTier tier) {
        if (tier.steps.max() > MAX_STEPS) {
            return DataResult.error(() -> "steps.max " + tier.steps.max() + " is above the limit of " + MAX_STEPS);
        }
        if (tier.steps.min() < 1) {
            return DataResult.error(() -> "steps.min must be at least 1");
        }
        if (tier.operations.isEmpty()) {
            return DataResult.error(() -> "operations must not be empty");
        }
        if (new HashSet<>(tier.operations).size() != tier.operations.size()) {
            return DataResult.error(() -> "operations contains duplicates: " + tier.operations);
        }
        return DataResult.success(tier);
    }

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
