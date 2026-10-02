package com.blueprintforge.registry;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.item.BlueprintItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BFItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BlueprintForge.MOD_ID);

    public static final DeferredItem<BlueprintItem> BLUEPRINT = ITEMS.register("blueprint",
            () -> new BlueprintItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> BLUEPRINT_ARCHIVE = ITEMS.registerSimpleBlockItem(BFBlocks.BLUEPRINT_ARCHIVE);
    public static final DeferredItem<BlockItem> PROJECT_BUREAU = ITEMS.registerSimpleBlockItem(BFBlocks.PROJECT_BUREAU);
    public static final DeferredItem<BlockItem> BLUEPRINT_DOCK = ITEMS.registerSimpleBlockItem(BFBlocks.BLUEPRINT_DOCK);
    public static final DeferredItem<Item> BLUEPRINT_STAMP = ITEMS.register("blueprint_stamp",
            () -> new Item(new Item.Properties().stacksTo(1).durability(250)));
    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_BLADE = ITEMS.register("incomplete_blade",
            () -> new SequencedAssemblyItem(new Item.Properties()));

    private BFItems() {
    }
}
