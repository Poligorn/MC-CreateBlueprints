package com.blueprintforge.registry;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.item.ResearchBlueprintItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BFItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BlueprintForge.MOD_ID);

    public static final DeferredItem<BlueprintItem> BLUEPRINT = ITEMS.register("blueprint",
            () -> new BlueprintItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> BLUEPRINT_ARCHIVE = ITEMS.registerSimpleBlockItem(BFBlocks.BLUEPRINT_ARCHIVE);

    public static final DeferredItem<ResearchBlueprintItem> RESEARCH_BLUEPRINT = ITEMS.register("research_blueprint",
            () -> new ResearchBlueprintItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> TERMINAL = ITEMS.registerSimpleBlockItem(BFBlocks.TERMINAL);

    /** Placeholder for the junk of a failed run (task 10, open question 3). It borrows a vanilla texture: there is no official art. */
    public static final DeferredItem<Item> SCRAP = ITEMS.registerSimpleItem("scrap");

    private BFItems() {
    }
}
