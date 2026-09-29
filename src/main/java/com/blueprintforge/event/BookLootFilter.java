package com.blueprintforge.event;

import com.blueprintforge.BFConfig;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Removes enchanted books from every generated loot when {@code enchanting.mode = full} or {@code disable_books}. */
public class BookLootFilter extends LootModifier {
    public static final MapCodec<BookLootFilter> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).apply(i, BookLootFilter::new));

    public BookLootFilter(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (BFConfig.booksDisabled()) {
            generatedLoot.removeIf(stack -> stack.is(Items.ENCHANTED_BOOK));
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
