package com.blueprintforge.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * An item a blueprint of one tier can make, one file in {@code data/<ns>/research_target/}. The item is also the
 * starting item of a run, so there is deliberately no separate "starting material" field. Each tier has its own material
 * (wood or leather, gold, iron, diamond, netherite), so a type has one target per tier.
 */
public record ResearchTarget(ResourceLocation item, ResearchType type, ResourceLocation tier) {
    public static final Codec<ResearchTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(ResearchTarget::item),
            ResearchType.CODEC.fieldOf("type").forGetter(ResearchTarget::type),
            ResourceLocation.CODEC.fieldOf("tier").forGetter(ResearchTarget::tier)
    ).apply(i, ResearchTarget::new));
}
