package com.blueprintforge.research;

import java.util.HashSet;
import java.util.List;

import com.blueprintforge.data.TierDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * A blueprint tier of the November concept, one file in {@code data/<ns>/research_tier/}. Separate from the older
 * {@code tier/} schema of the Archive prototype, which is not extended.
 *
 * @param steps           how many steps a chain of this tier has
 * @param operations      ids of the operations a chain may use, for example {@code blueprintforge:press}. The ids are not
 *                        checked against known operations yet: that comes with the chain generator.
 * @param bonusMultiplier strength of the enhancement a successful run gives
 */
public record ResearchTier(
        Component display,
        int color,
        IntRange steps,
        List<ResourceLocation> operations,
        double bonusMultiplier,
        AttemptCost attemptCost
) {
    public static final Codec<ResearchTier> CODEC = RecordCodecBuilder.<ResearchTier>create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("display").forGetter(ResearchTier::display),
            TierDefinition.HEX_COLOR.fieldOf("color").forGetter(ResearchTier::color),
            IntRange.CODEC.fieldOf("steps").forGetter(ResearchTier::steps),
            ResourceLocation.CODEC.listOf().fieldOf("operations").forGetter(ResearchTier::operations),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("bonus_multiplier").forGetter(ResearchTier::bonusMultiplier),
            AttemptCost.CODEC.fieldOf("attempt_cost").forGetter(ResearchTier::attemptCost)
    ).apply(i, ResearchTier::new)).validate(ResearchTier::validate);

    private static DataResult<ResearchTier> validate(ResearchTier tier) {
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
}
