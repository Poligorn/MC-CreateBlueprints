package com.blueprintforge.loot;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * One file of {@code data/<ns>/blueprint_source/}: where blueprints may appear and how often.
 *
 * <ul>
 *   <li>{@code type}: only {@code "chest"} in 0.1. {@code "mob_drop"} is refused on purpose, blueprints never drop from mobs.</li>
 *   <li>{@code tables}: loot table ids of chests; entity tables ({@code entities/...}) are refused.</li>
 *   <li>{@code chance}: 0..1, per roll of the table, before {@code rarity.multiplier} is applied.</li>
 *   <li>{@code tier_weights}: tier id to weight; the tier is picked by weight, the target then uniformly among the
 *       targets of that tier. Tiers above T3 are never dropped, whatever is listed here.</li>
 * </ul>
 * Unknown fields (for example {@code _comment}) are ignored.
 */
public record BlueprintSource(Type type, List<ResourceLocation> tables, float chance, Map<ResourceLocation, Integer> tierWeights) {
    public enum Type {
        CHEST("chest");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static DataResult<Type> parse(String id) {
            if (id.equals("mob_drop")) {
                return DataResult.error(() -> "type 'mob_drop' is not supported: blueprints do not drop from mobs");
            }
            for (Type type : values()) {
                if (type.id.equals(id)) {
                    return DataResult.success(type);
                }
            }
            return DataResult.error(() -> "unknown source type '" + id + "', expected 'chest'");
        }

        static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(Type::parse, Type::id);
    }

    private static final Codec<List<ResourceLocation>> TABLES = ResourceLocation.CODEC.listOf().flatXmap(
            tables -> {
                if (tables.isEmpty()) {
                    return DataResult.error(() -> "tables is empty");
                }
                for (ResourceLocation table : tables) {
                    if (table.getPath().startsWith("entities/")) {
                        return DataResult.error(() -> "table " + table + " is a mob loot table, blueprints do not drop from mobs");
                    }
                }
                return DataResult.success(tables);
            }, DataResult::success);

    private static final Codec<Map<ResourceLocation, Integer>> WEIGHTS = Codec.unboundedMap(ResourceLocation.CODEC, Codec.intRange(1, 1000)).flatXmap(
            weights -> weights.isEmpty() ? DataResult.error(() -> "tier_weights is empty") : DataResult.success(weights),
            DataResult::success);

    public static final Codec<BlueprintSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            Type.CODEC.fieldOf("type").forGetter(BlueprintSource::type),
            TABLES.fieldOf("tables").forGetter(BlueprintSource::tables),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(BlueprintSource::chance),
            WEIGHTS.fieldOf("tier_weights").forGetter(BlueprintSource::tierWeights)
    ).apply(i, BlueprintSource::new));
}
