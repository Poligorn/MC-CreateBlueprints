package com.blueprintforge.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** One candidate resource of a tier's deployer pool: an item and how many of it a single step may ask for. */
public record ResourceEntry(ResourceLocation item, IntRange count) {
    public static final Codec<ResourceEntry> CODEC = RecordCodecBuilder.<ResourceEntry>create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(ResourceEntry::item),
            IntRange.CODEC.fieldOf("count").forGetter(ResourceEntry::count)
    ).apply(i, ResourceEntry::new)).validate(entry -> entry.count.min() < 1
            ? DataResult.error(() -> "count.min must be at least 1")
            : DataResult.success(entry));
}
