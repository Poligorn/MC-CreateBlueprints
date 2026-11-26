package com.blueprintforge.registry;

import java.util.function.Supplier;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.loot.BlueprintLootModifier;
import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class BFLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BlueprintForge.MOD_ID);

    public static final Supplier<MapCodec<BlueprintLootModifier>> BLUEPRINT_SOURCES = SERIALIZERS.register("blueprint_sources", () -> BlueprintLootModifier.CODEC);

    private BFLootModifiers() {
    }
}
