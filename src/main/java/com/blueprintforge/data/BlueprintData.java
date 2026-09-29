package com.blueprintforge.data;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * State of one blueprint instance, stored as the {@code blueprintforge:blueprint} data component.
 *
 * @param runsRemaining {@code -1} for originals, which are never consumed
 * @param roll          opaque ancient roll payload; the schema accepts it now, interpretation arrives with the roller
 */
public record BlueprintData(
        UUID instanceId,
        ResourceLocation definitionId,
        BlueprintClass clazz,
        ResourceLocation tierId,
        Optional<ResourceLocation> target,
        int runsRemaining,
        int materialEfficiency,
        int timeEfficiency,
        Optional<UUID> researcherUuid,
        Optional<String> researcherName,
        Optional<UUID> copierUuid,
        Optional<String> copierName,
        Optional<CompoundTag> roll
) {
    public static final Codec<BlueprintData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("instance_id").forGetter(BlueprintData::instanceId),
            ResourceLocation.CODEC.fieldOf("definition").forGetter(BlueprintData::definitionId),
            BlueprintClass.CODEC.fieldOf("class").forGetter(BlueprintData::clazz),
            ResourceLocation.CODEC.fieldOf("tier").forGetter(BlueprintData::tierId),
            ResourceLocation.CODEC.optionalFieldOf("target").forGetter(BlueprintData::target),
            Codec.INT.fieldOf("runs").forGetter(BlueprintData::runsRemaining),
            Codec.INT.optionalFieldOf("me", 0).forGetter(BlueprintData::materialEfficiency),
            Codec.INT.optionalFieldOf("te", 0).forGetter(BlueprintData::timeEfficiency),
            UUIDUtil.CODEC.optionalFieldOf("researcher").forGetter(BlueprintData::researcherUuid),
            Codec.STRING.optionalFieldOf("researcher_name").forGetter(BlueprintData::researcherName),
            UUIDUtil.CODEC.optionalFieldOf("copier").forGetter(BlueprintData::copierUuid),
            Codec.STRING.optionalFieldOf("copier_name").forGetter(BlueprintData::copierName),
            CompoundTag.CODEC.optionalFieldOf("roll").forGetter(BlueprintData::roll)
    ).apply(i, BlueprintData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public boolean isOriginal() {
        return clazz == BlueprintClass.ORIGINAL;
    }
}
