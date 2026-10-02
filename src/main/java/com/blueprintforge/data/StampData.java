package com.blueprintforge.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Which blueprint definition a stamp is cut for. A deployer holding another stamp does not start the line. */
public record StampData(ResourceLocation blueprintId) {
    public static final Codec<StampData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("blueprint").forGetter(StampData::blueprintId)
    ).apply(i, StampData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StampData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
