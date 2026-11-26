package com.blueprintforge.loot;

import com.blueprintforge.BFConfig;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Global loot modifier that adds blueprints on top of existing tables and never replaces anything. Loot tables load
 * before datapack reload listeners, so the sources are read when a table rolls.
 */
public class BlueprintLootModifier extends LootModifier {
    public static final MapCodec<BlueprintLootModifier> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).apply(i, BlueprintLootModifier::new));

    public BlueprintLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // A damage source only exists in the loot of a killed entity: a second guard next to the sources refusing entity tables.
        boolean mobLoot = context.hasParam(LootContextParams.DAMAGE_SOURCE);
        generatedLoot.addAll(BlueprintLoot.roll(context.getQueriedLootTableId(), mobLoot, BFConfig.rarityMultiplier(), context.getRandom()));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
