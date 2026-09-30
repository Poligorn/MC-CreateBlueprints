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

/**
 * Sends tiers, blueprint definitions and research profiles to clients so the Archive screen can show the next
 * step's price. Sources never leave the server.
 */
public record BlueprintSyncPayload(
        Map<ResourceLocation, TierDefinition> tiers,
        Map<ResourceLocation, BlueprintDefinition> blueprints,
        Map<ResourceLocation, ResearchProfile> research,
        Map<ResourceLocation, ResourceLocation> researchForBlueprint,
        Map<ResourceLocation, AssemblyRecipe> assemblies
) implements CustomPacketPayload {
    public static final Type<BlueprintSyncPayload> TYPE = new Type<>(BlueprintForge.id("definitions"));

    private static final Codec<BlueprintSyncPayload> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(ResourceLocation.CODEC, TierDefinition.CODEC).fieldOf("tiers").forGetter(BlueprintSyncPayload::tiers),
            Codec.unboundedMap(ResourceLocation.CODEC, BlueprintDefinition.CODEC).fieldOf("blueprints").forGetter(BlueprintSyncPayload::blueprints),
            Codec.unboundedMap(ResourceLocation.CODEC, ResearchProfile.CODEC).fieldOf("research").forGetter(BlueprintSyncPayload::research),
            Codec.unboundedMap(ResourceLocation.CODEC, ResourceLocation.CODEC).fieldOf("research_for").forGetter(BlueprintSyncPayload::researchForBlueprint),
            Codec.unboundedMap(ResourceLocation.CODEC, AssemblyRecipe.CODEC).fieldOf("assemblies").forGetter(BlueprintSyncPayload::assemblies)
    ).apply(i, BlueprintSyncPayload::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintSyncPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);

    public static BlueprintSyncPayload current() {
        return new BlueprintSyncPayload(TierRegistry.all(), BlueprintRegistry.all(), ResearchRegistry.all(),
                ResearchRegistry.assignments(), AssemblyRegistry.all());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
