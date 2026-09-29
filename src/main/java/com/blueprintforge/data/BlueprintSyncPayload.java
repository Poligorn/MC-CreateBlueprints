package com.blueprintforge.data;

import java.util.Map;

import com.blueprintforge.BlueprintForge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sends tiers and blueprint definitions to clients so names, colours and the creative tab match the server. */
public record BlueprintSyncPayload(Map<ResourceLocation, TierDefinition> tiers, Map<ResourceLocation, BlueprintDefinition> blueprints)
        implements CustomPacketPayload {
    public static final Type<BlueprintSyncPayload> TYPE = new Type<>(BlueprintForge.id("definitions"));

    private static final Codec<BlueprintSyncPayload> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(ResourceLocation.CODEC, TierDefinition.CODEC).fieldOf("tiers").forGetter(BlueprintSyncPayload::tiers),
            Codec.unboundedMap(ResourceLocation.CODEC, BlueprintDefinition.CODEC).fieldOf("blueprints").forGetter(BlueprintSyncPayload::blueprints)
    ).apply(i, BlueprintSyncPayload::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintSyncPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);

    public static BlueprintSyncPayload current() {
        return new BlueprintSyncPayload(TierRegistry.all(), BlueprintRegistry.all());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
