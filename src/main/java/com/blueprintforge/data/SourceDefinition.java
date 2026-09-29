package com.blueprintforge.data;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.biome.Biome;

/**
 * A {@code blueprint_source/*.json} file after the loader kept the source types this version understands.
 */
public record SourceDefinition(ResourceLocation blueprint, int weight, Conditions conditions, List<LootInjection> lootInjections) {

    /** First-pass shape: {@code sources[]} stays raw so an unsupported type does not reject the whole file. */
    public record Raw(ResourceLocation blueprint, int weight, Conditions conditions, List<Dynamic<?>> sources) {
        public static final Codec<Raw> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("blueprint").forGetter(Raw::blueprint),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("weight", 1).forGetter(Raw::weight),
                Conditions.CODEC.optionalFieldOf("conditions", Conditions.NONE).forGetter(Raw::conditions),
                Codec.PASSTHROUGH.listOf().fieldOf("sources").forGetter(Raw::sources)
        ).apply(i, Raw::new));
    }

    public record LootInjection(List<ResourceLocation> targetTables, int addEntries, float chance) {
        public static final String TYPE = "loot_injection";

        public static final Codec<LootInjection> CODEC = RecordCodecBuilder.<LootInjection>create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("target_tables").forGetter(LootInjection::targetTables),
                Codec.intRange(1, 64).fieldOf("add_entries").forGetter(LootInjection::addEntries),
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(LootInjection::chance)
        ).apply(i, LootInjection::new)).validate(l -> l.targetTables.isEmpty()
                ? DataResult.error(() -> "target_tables must not be empty")
                : DataResult.success(l));
    }

    /** A biome filter entry: a biome id, or a biome tag written as {@code #namespace:path}. */
    public record BiomeFilter(ResourceLocation id, boolean tag) {
        public static final Codec<BiomeFilter> CODEC = Codec.STRING.comapFlatMap(BiomeFilter::parse,
                f -> f.tag ? "#" + f.id : f.id.toString());

        private static DataResult<BiomeFilter> parse(String value) {
            boolean tag = value.startsWith("#");
            return ResourceLocation.read(tag ? value.substring(1) : value).map(id -> new BiomeFilter(id, tag));
        }

        public boolean matches(Holder<Biome> biome) {
            return tag ? biome.is(TagKey.create(Registries.BIOME, id)) : biome.is(id);
        }
    }

    public record Conditions(
            List<ResourceLocation> dimensions,
            List<BiomeFilter> biomes,
            List<Difficulty> difficulty,
            Optional<Integer> minY,
            Optional<Integer> maxY,
            Optional<ResourceLocation> requiresModpackTag
    ) {
        public static final Conditions NONE = new Conditions(List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), Optional.empty());

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().optionalFieldOf("dimensions", List.of()).forGetter(Conditions::dimensions),
                BiomeFilter.CODEC.listOf().optionalFieldOf("biomes", List.of()).forGetter(Conditions::biomes),
                Difficulty.CODEC.listOf().optionalFieldOf("difficulty", List.of()).forGetter(Conditions::difficulty),
                Codec.INT.optionalFieldOf("min_y").forGetter(Conditions::minY),
                Codec.INT.optionalFieldOf("max_y").forGetter(Conditions::maxY),
                ResourceLocation.CODEC.optionalFieldOf("requires_modpack_tag").forGetter(Conditions::requiresModpackTag)
        ).apply(i, Conditions::new));

        /**
         * The modpack tag is an item tag. A tag that is absent or empty switches the whole pool off without an error,
         * so a pack author enables the branch by shipping that tag.
         */
        public boolean modpackTagPresent() {
            return requiresModpackTag
                    .map(id -> BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).map(set -> set.size() > 0).orElse(false))
                    .orElse(true);
        }

        public boolean requiresPosition() {
            return !biomes.isEmpty() || minY.isPresent() || maxY.isPresent();
        }
    }
}
