package com.blueprintforge.research;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What one attempt costs besides the target item: how many hand-fed deployer steps a chain of this tier may contain
 * and which resources they may carry. The pool is data for the chain generator; spending it is a later task.
 */
public record AttemptCost(IntRange deployers, List<ResourceEntry> pool) {
    public static final Codec<AttemptCost> CODEC = RecordCodecBuilder.<AttemptCost>create(i -> i.group(
            IntRange.CODEC.fieldOf("deployers").forGetter(AttemptCost::deployers),
            ResourceEntry.CODEC.listOf().optionalFieldOf("pool", List.of()).forGetter(AttemptCost::pool)
    ).apply(i, AttemptCost::new)).validate(cost -> cost.deployers.max() > 0 && cost.pool.isEmpty()
            ? DataResult.error(() -> "deployers.max is " + cost.deployers.max() + " but the resource pool is empty")
            : DataResult.success(cost));
}
