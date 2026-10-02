package com.blueprintforge.data;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
 * @param flux          scrap reduction in percent, 0–80 in the reference pack
 * @param potency       researched enchant tier, 0–3 in the reference pack
 * @param foundGameTime world game time when the instance was issued, {@code 0} when unknown
 * @param ownerUuid     who the instance was issued to; provenance only, never an access check
 * @param roll          opaque ancient roll payload; the schema accepts it now, interpretation arrives with decoding
 */
public record BlueprintData(
        UUID instanceId,
        ResourceLocation definitionId,
        BlueprintClass clazz,
        ResourceLocation tierId,
        Optional<ResourceLocation> target,
        int runsRemaining,
        int materialEfficiency,
        int flux,
        int potency,
        long foundGameTime,
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

    /**
     * Provenance fields stay flat in JSON. {@code RecordCodecBuilder.group} accepts at most 16 fields,
     * so these eight ride along as one inlined map codec.
     */
    private record Provenance(
            long foundGameTime,
            Optional<UUID> researcherUuid,
            Optional<String> researcherName,
            Optional<UUID> copierUuid,
            Optional<String> copierName,
            Optional<UUID> ownerUuid,
            Optional<String> ownerName,
            Optional<CompoundTag> roll
    ) {
        static final MapCodec<Provenance> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.LONG.optionalFieldOf("found_game_time", 0L).forGetter(Provenance::foundGameTime),
                UUIDUtil.CODEC.optionalFieldOf("researcher").forGetter(Provenance::researcherUuid),
                Codec.STRING.optionalFieldOf("researcher_name").forGetter(Provenance::researcherName),
                UUIDUtil.CODEC.optionalFieldOf("copier").forGetter(Provenance::copierUuid),
                Codec.STRING.optionalFieldOf("copier_name").forGetter(Provenance::copierName),
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Provenance::ownerUuid),
                Codec.STRING.optionalFieldOf("owner_name").forGetter(Provenance::ownerName),
                CompoundTag.CODEC.optionalFieldOf("roll").forGetter(Provenance::roll)
        ).apply(i, Provenance::new));
    }

    public static final Codec<BlueprintData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("instance_id").forGetter(BlueprintData::instanceId),
            ResourceLocation.CODEC.fieldOf("definition").forGetter(BlueprintData::definitionId),
            BlueprintClass.CODEC.fieldOf("class").forGetter(BlueprintData::clazz),
            ResourceLocation.CODEC.fieldOf("tier").forGetter(BlueprintData::tierId),
            ResourceLocation.CODEC.optionalFieldOf("target").forGetter(BlueprintData::target),
            Codec.INT.fieldOf("runs").forGetter(BlueprintData::runsRemaining),
            Codec.INT.optionalFieldOf("me", 0).forGetter(BlueprintData::materialEfficiency),
            Codec.INT.optionalFieldOf("flux", 0).forGetter(BlueprintData::flux),
            Codec.INT.optionalFieldOf("potency", 0).forGetter(BlueprintData::potency),
            Provenance.CODEC.forGetter(BlueprintData::provenance)
    ).apply(i, (instanceId, definitionId, clazz, tierId, target, runs, me, flux, potency, provenance) ->
            new BlueprintData(instanceId, definitionId, clazz, tierId, target, runs, me, flux, potency,
                    provenance.foundGameTime(), provenance.researcherUuid(), provenance.researcherName(),
                    provenance.copierUuid(), provenance.copierName(), provenance.ownerUuid(), provenance.ownerName(),
                    provenance.roll())));

    private Provenance provenance() {
        return new Provenance(foundGameTime, researcherUuid, researcherName, copierUuid, copierName, ownerUuid, ownerName, roll);
    }

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
    public BlueprintData withResearch(int materialEfficiency, int flux, int potency, Optional<UUID> researcher, Optional<String> researcherName) {
        Optional<UUID> who = researcherUuid.isPresent() ? researcherUuid : researcher;
        Optional<String> name = researcherUuid.isPresent() ? this.researcherName : researcherName;
        return new BlueprintData(instanceId, definitionId, clazz, tierId, target, runsRemaining, materialEfficiency,
                flux, potency, foundGameTime, who, name, copierUuid, copierName, ownerUuid, ownerName, roll);
    }

    /** Same instance with a new run count. Originals stay at {@code -1}; this does not change class. */
    public BlueprintData withRuns(int runsRemaining) {
        return new BlueprintData(instanceId, definitionId, clazz, tierId, target, runsRemaining, materialEfficiency,
                flux, potency, foundGameTime, researcherUuid, researcherName, copierUuid, copierName, ownerUuid, ownerName, roll);
    }

    /**
     * A printed copy of this original. New instance, penalized ME and Flux, potency copied in full,
     * the original's owner, and the player who pressed Print as the copier.
     */
    public BlueprintData printedCopy(UUID newInstanceId, int runs, int materialEfficiency, int flux, int potency,
                                     Optional<UUID> copier, Optional<String> copierName) {
        return new BlueprintData(newInstanceId, definitionId, BlueprintClass.COPY, tierId, target, runs,
                materialEfficiency, flux, potency, foundGameTime, researcherUuid, researcherName, copier, copierName,
                ownerUuid, ownerName, roll);
    }

    /** A real instance made from a template: fresh UUID, owner recorded, found time stamped, everything else unchanged. */
    public BlueprintData issuedTo(UUID newInstanceId, Optional<UUID> owner, Optional<String> ownerDisplayName, long foundGameTime) {
        return new BlueprintData(newInstanceId, definitionId, clazz, tierId, target, runsRemaining, materialEfficiency,
                flux, potency, foundGameTime, researcherUuid, researcherName, copierUuid, copierName, owner, ownerDisplayName, roll);
    }
}
