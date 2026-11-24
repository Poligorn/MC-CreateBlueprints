package com.blueprintforge.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** What a workpiece remembers about the blueprint it was issued from: enough to rebuild the chain and score the run. */
public record ResearchRun(ResourceLocation tier, ResourceLocation target, ResourceLocation item, long seed, int generatorVersion) {
    public static final Codec<ResearchRun> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("tier").forGetter(ResearchRun::tier),
            ResourceLocation.CODEC.fieldOf("target").forGetter(ResearchRun::target),
            ResourceLocation.CODEC.fieldOf("item").forGetter(ResearchRun::item),
            Codec.LONG.fieldOf("seed").forGetter(ResearchRun::seed),
            Codec.INT.fieldOf("generator_version").forGetter(ResearchRun::generatorVersion)
    ).apply(i, ResearchRun::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchRun> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchRun::tier,
            ResourceLocation.STREAM_CODEC, ResearchRun::target,
            ResourceLocation.STREAM_CODEC, ResearchRun::item,
            ByteBufCodecs.VAR_LONG, ResearchRun::seed,
            ByteBufCodecs.VAR_INT, ResearchRun::generatorVersion,
            ResearchRun::new);
}
