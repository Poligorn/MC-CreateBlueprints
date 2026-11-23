package com.blueprintforge.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** An inclusive integer range from datapack JSON, {@code {"min": 1, "max": 3}}. */
public record IntRange(int min, int max) {
    public static final Codec<IntRange> CODEC = RecordCodecBuilder.<IntRange>create(i -> i.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("min").forGetter(IntRange::min),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("max").forGetter(IntRange::max)
    ).apply(i, IntRange::new)).validate(range -> range.min > range.max
            ? DataResult.error(() -> "min " + range.min + " is greater than max " + range.max)
            : DataResult.success(range));
}
