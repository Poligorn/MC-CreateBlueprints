package com.blueprintforge.research;

import java.util.TreeMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * What one attempt costs besides the target item: how many hand-fed deployer steps a chain of this tier may contain
 * and which resources they may carry. The pool is data for the chain generator (version 1 and later); the resources are
 * spent by the deployer hook when a deployer step fires on a workpiece, whatever the final result is.
 * TODO(egor): one pool per tier, not per target; nothing is refunded on a failed run; the numbers are a balance draft
 * (task 8, open question 6).
 */
public record AttemptCost(IntRange deployers, List<ResourceEntry> pool) {
    public static final Codec<AttemptCost> CODEC = RecordCodecBuilder.<AttemptCost>create(i -> i.group(
            IntRange.CODEC.fieldOf("deployers").forGetter(AttemptCost::deployers),
            ResourceEntry.CODEC.listOf().optionalFieldOf("pool", List.of()).forGetter(AttemptCost::pool)
    ).apply(i, AttemptCost::new)).validate(cost -> cost.deployers.max() > 0 && cost.pool.isEmpty()
            ? DataResult.error(() -> "deployers.max is " + cost.deployers.max() + " but the resource pool is empty")
            : DataResult.success(cost));

    /** What the deployer steps of a chain cost in total, per item, sorted by item id so that the order of the steps does not leak. */
    public static Map<ResourceLocation, Integer> totals(List<ChainStep> chain) {
        Map<ResourceLocation, Integer> totals = new TreeMap<>();
        for (ChainStep step : chain) {
            if (step.isDeploy()) {
                totals.merge(step.item(), step.count(), Integer::sum);
            }
        }
        return totals;
    }
}
