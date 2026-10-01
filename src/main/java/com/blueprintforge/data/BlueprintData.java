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
 * @param ownerUuid     who the instance was issued to; provenance only, never an access check
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
        Optional<UUID> ownerUuid,
        Optional<String> ownerName,
        Optional<CompoundTag> roll
) {
    /** Instance id of a creative-tab template: not an instance yet, it gets a real UUID when a player takes it. */
    public static final UUID UNISSUED = new UUID(0L, 0L);

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
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(BlueprintData::ownerUuid),
            Codec.STRING.optionalFieldOf("owner_name").forGetter(BlueprintData::ownerName),
            CompoundTag.CODEC.optionalFieldOf("roll").forGetter(BlueprintData::roll)
    ).apply(i, BlueprintData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public boolean isOriginal() {
        return clazz == BlueprintClass.ORIGINAL;
    }

    public boolean isUnissued() {
        return UNISSUED.equals(instanceId);
    }

    /**
     * Same instance after a finished research step. The first researcher is kept; a later step does not overwrite them.
     * Originals stay originals.
     */
    public BlueprintData withResearch(int materialEfficiency, int timeEfficiency, Optional<UUID> researcher, Optional<String> researcherName) {
        Optional<UUID> who = researcherUuid.isPresent() ? researcherUuid : researcher;
        Optional<String> name = researcherUuid.isPresent() ? this.researcherName : researcherName;
        return new BlueprintData(instanceId, definitionId, clazz, tierId, target, runsRemaining, materialEfficiency,
                timeEfficiency, who, name, copierUuid, copierName, ownerUuid, ownerName, roll);
    }

    /** Same instance with a new run count. Originals stay at {@code -1}; this does not change class. */
    public BlueprintData withRuns(int runsRemaining) {
        return new BlueprintData(instanceId, definitionId, clazz, tierId, target, runsRemaining, materialEfficiency,
                timeEfficiency, researcherUuid, researcherName, copierUuid, copierName, ownerUuid, ownerName, roll);
    }

    /**
     * A printed copy of this original. New instance, penalized ME/TE, the original's owner, and the player
     * who pressed Print as the copier. The original's researcher is kept so the tooltip can name both.
     */
    public BlueprintData printedCopy(UUID newInstanceId, int runs, int materialEfficiency, int timeEfficiency,
                                     Optional<UUID> copier, Optional<String> copierName) {
        return new BlueprintData(newInstanceId, definitionId, BlueprintClass.COPY, tierId, target, runs,
                materialEfficiency, timeEfficiency, researcherUuid, researcherName, copier, copierName,
                ownerUuid, ownerName, roll);
    }

    /** A real instance made from a template: fresh UUID, owner recorded, everything else unchanged. */
    public BlueprintData issuedTo(UUID newInstanceId, Optional<UUID> owner, Optional<String> ownerDisplayName) {
        return new BlueprintData(newInstanceId, definitionId, clazz, tierId, target, runsRemaining, materialEfficiency,
                timeEfficiency, researcherUuid, researcherName, copierUuid, copierName, owner, ownerDisplayName, roll);
    }
}
