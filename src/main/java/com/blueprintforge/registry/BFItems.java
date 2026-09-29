package com.blueprintforge.registry;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.item.BlueprintItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BFItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BlueprintForge.MOD_ID);

    public static final DeferredItem<BlueprintItem> BLUEPRINT = ITEMS.register("blueprint",
            () -> new BlueprintItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> BLUEPRINT_ARCHIVE = ITEMS.registerSimpleBlockItem(BFBlocks.BLUEPRINT_ARCHIVE);

    private BFItems() {
    }
}
