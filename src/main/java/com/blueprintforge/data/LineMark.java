package com.blueprintforge.data;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Snapshot written onto a sequenced-assembly item at the stamp step.
 * Later machines copy it; the last step reads it. Create's own advance drops custom components,
 * so the line mixin puts this mark back on every intermediate stack.
 */
public record LineMark(
        ResourceLocation lineId,
        ResourceLocation blueprintId,
        UUID instanceId,
        int materialEfficiency,
        int flux,
        int potency
) {
    public static final Codec<LineMark> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("line").forGetter(LineMark::lineId),
            ResourceLocation.CODEC.fieldOf("blueprint").forGetter(LineMark::blueprintId),
            UUIDUtil.CODEC.fieldOf("instance").forGetter(LineMark::instanceId),
            Codec.INT.optionalFieldOf("me", 0).forGetter(LineMark::materialEfficiency),
            Codec.INT.optionalFieldOf("flux", 0).forGetter(LineMark::flux),
            Codec.INT.optionalFieldOf("potency", 0).forGetter(LineMark::potency)
    ).apply(i, LineMark::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LineMark> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
