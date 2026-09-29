package com.blueprintforge.event;

import java.util.List;
import java.util.UUID;

import com.blueprintforge.BFConfig;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.SourceDefinition;
import com.blueprintforge.data.SourceRegistry;
import com.blueprintforge.item.BlueprintItem;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * {@code loot_injection} sources. Adds entries on top of an existing table and never replaces it.
 * Loot tables load before datapack reload listeners, so injection happens when the table rolls, reading
 * the current sources and {@code rarity.multiplier}.
 */
public class LootInjectionHandler extends LootModifier {
    public static final MapCodec<LootInjectionHandler> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).apply(i, LootInjectionHandler::new));

    public LootInjectionHandler(LootItemCondition[] conditions) {
        super(conditions);
    }

    /** {@code chance} scaled by {@code rarity.multiplier} and clamped to 0..1, as a {@code random_chance} condition. */
    public static float effectiveChance(float chance, double multiplier) {
        return (float) Mth.clamp(chance * multiplier, 0.0, 1.0);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        List<SourceRegistry.Injection> injections = SourceRegistry.injectionsFor(context.getQueriedLootTableId());
        if (injections.isEmpty()) {
            return generatedLoot;
        }
        double multiplier = BFConfig.rarityMultiplier();
        for (SourceRegistry.Injection injection : injections) {
            if (!conditionsMatch(injection.conditions(), context)) {
                continue;
            }
            BlueprintRegistry.get(injection.blueprint()).ifPresent(definition -> {
                float chance = effectiveChance(injection.chance(), multiplier);
                for (int entry = 0; entry < injection.addEntries(); entry++) {
                    if (context.getRandom().nextFloat() < chance) {
                        generatedLoot.add(BlueprintItem.createInstance(injection.blueprint(), definition, UUID.randomUUID()));
                    }
                }
            });
        }
        return generatedLoot;
    }

    private static boolean conditionsMatch(SourceDefinition.Conditions conditions, LootContext context) {
        if (!conditions.modpackTagPresent()) {
            return false;
        }
        ServerLevel level = context.getLevel();
        if (!conditions.dimensions().isEmpty() && !conditions.dimensions().contains(level.dimension().location())) {
            return false;
        }
        if (!conditions.difficulty().isEmpty() && !conditions.difficulty().contains(level.getDifficulty())) {
            return false;
        }
        if (!conditions.requiresPosition()) {
            return true;
        }
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) {
            return false;
        }
        if (conditions.minY().isPresent() && origin.y < conditions.minY().get()) {
            return false;
        }
        if (conditions.maxY().isPresent() && origin.y > conditions.maxY().get()) {
            return false;
        }
        if (conditions.biomes().isEmpty()) {
            return true;
        }
        Holder<Biome> biome = level.getBiome(BlockPos.containing(origin));
        return conditions.biomes().stream().anyMatch(filter -> filter.matches(biome));
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
