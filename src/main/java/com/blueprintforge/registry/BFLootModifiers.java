package com.blueprintforge.registry;

import java.util.function.Supplier;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.event.BookLootFilter;
import com.blueprintforge.event.LootInjectionHandler;
import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class BFLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BlueprintForge.MOD_ID);

    public static final Supplier<MapCodec<LootInjectionHandler>> BLUEPRINT_SOURCES = SERIALIZERS.register("blueprint_sources", () -> LootInjectionHandler.CODEC);
    public static final Supplier<MapCodec<BookLootFilter>> BOOK_FILTER = SERIALIZERS.register("book_filter", () -> BookLootFilter.CODEC);

    private BFLootModifiers() {
    }
}
