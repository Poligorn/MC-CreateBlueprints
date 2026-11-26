package com.blueprintforge.research;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * State of a blueprint item of the November concept. Carries what the client needs to name the target and pick an icon
 * (item id, symbol, sprite tier, copy flag) next to the ids of the tier and target entries; the seed stays empty until the blueprint first goes into a terminal.
 * The seed is synced like the rest, which is a known weakness: a client can read it. See the design review.
 */
public record ResearchBlueprint(
        ResourceLocation tier,
        ResourceLocation target,
        ResourceLocation item,
        ResearchType symbol,
        int spriteTier,
        boolean copy,
        Optional<Long> seed,
        int generatorVersion
) {
    public static final Codec<ResearchBlueprint> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("tier").forGetter(ResearchBlueprint::tier),
            ResourceLocation.CODEC.fieldOf("target").forGetter(ResearchBlueprint::target),
            ResourceLocation.CODEC.fieldOf("item").forGetter(ResearchBlueprint::item),
            ResearchType.CODEC.fieldOf("symbol").forGetter(ResearchBlueprint::symbol),
            Codec.intRange(1, 5).fieldOf("sprite_tier").forGetter(ResearchBlueprint::spriteTier),
            Codec.BOOL.optionalFieldOf("copy", false).forGetter(ResearchBlueprint::copy),
            Codec.LONG.optionalFieldOf("seed").forGetter(ResearchBlueprint::seed),
            // A stored blueprint without the field was made before generator version 1, so its default is 0 and not the current version.
            Codec.INT.optionalFieldOf("generator_version", ResearchChain.VERSION_PRESS_BLAST).forGetter(ResearchBlueprint::generatorVersion)
    ).apply(i, ResearchBlueprint::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchBlueprint> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                ResourceLocation.STREAM_CODEC.encode(buffer, value.tier);
                ResourceLocation.STREAM_CODEC.encode(buffer, value.target);
                ResourceLocation.STREAM_CODEC.encode(buffer, value.item);
                buffer.writeEnum(value.symbol);
                buffer.writeVarInt(value.spriteTier);
                buffer.writeBoolean(value.copy);
                buffer.writeBoolean(value.seed.isPresent());
                value.seed.ifPresent(buffer::writeLong);
                buffer.writeVarInt(value.generatorVersion);
            },
            buffer -> {
                ResourceLocation tier = ResourceLocation.STREAM_CODEC.decode(buffer);
                ResourceLocation target = ResourceLocation.STREAM_CODEC.decode(buffer);
                ResourceLocation item = ResourceLocation.STREAM_CODEC.decode(buffer);
                ResearchType symbol = buffer.readEnum(ResearchType.class);
                int spriteTier = buffer.readVarInt();
                boolean copy = buffer.readBoolean();
                Optional<Long> seed = buffer.readBoolean() ? Optional.of(buffer.readLong()) : Optional.empty();
                return new ResearchBlueprint(tier, target, item, symbol, spriteTier, copy, seed, buffer.readVarInt());
            });

    public static ResearchBlueprint of(ResourceLocation tierId, ResearchTier tier, ResourceLocation targetId, ResearchTarget target, boolean copy) {
        return new ResearchBlueprint(tierId, targetId, target.item(), target.type(), tier.spriteTier(), copy, Optional.empty(), ResearchChain.GENERATOR_VERSION);
    }

    public ResearchBlueprint withSeed(long newSeed) {
        return new ResearchBlueprint(tier, target, item, symbol, spriteTier, copy, Optional.of(newSeed), generatorVersion);
    }

    public ResearchBlueprint withGeneratorVersion(int version) {
        return new ResearchBlueprint(tier, target, item, symbol, spriteTier, copy, seed, version);
    }

    /** Index for the item model predicate: 0 is "no component", then type, copy flag and sprite tier. */
    public int variant() {
        return 1 + symbol.ordinal() * 10 + (copy ? 5 : 0) + (spriteTier - 1);
    }
}
