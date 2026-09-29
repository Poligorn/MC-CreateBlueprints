package com.blueprintforge.data;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Marks an item produced from a blueprint, stored as the {@code blueprintforge:forged} data component.
 * Lets the enchanting policy tell a forged T2 sword from a vanilla one with the same item id.
 */
public record ForgedItemData(ResourceLocation blueprintId, ResourceLocation tierId, List<OutputModifier> modifiers) {
    public static final Codec<ForgedItemData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("blueprint").forGetter(ForgedItemData::blueprintId),
            ResourceLocation.CODEC.fieldOf("tier").forGetter(ForgedItemData::tierId),
            OutputModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(ForgedItemData::modifiers)
    ).apply(i, ForgedItemData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgedItemData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
