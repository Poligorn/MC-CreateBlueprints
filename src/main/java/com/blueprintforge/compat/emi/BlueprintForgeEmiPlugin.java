package com.blueprintforge.compat.emi;

import java.util.ArrayList;
import java.util.List;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.compat.viewer.ViewerCatalog;
import com.blueprintforge.registry.BFItems;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Optional. Loaded by EMI's annotation scan only. The mod does not reference this class. */
@EmiEntrypoint
public class BlueprintForgeEmiPlugin implements EmiPlugin {
    private static final EmiStack ARCHIVE = EmiStack.of(BFItems.BLUEPRINT_ARCHIVE.get());
    public static final EmiRecipeCategory RESEARCH = category("research", "gui.blueprintforge.viewer.research");
    public static final EmiRecipeCategory COPY = category("copy", "gui.blueprintforge.viewer.copy");
    public static final EmiRecipeCategory TIER = category("tier", "gui.blueprintforge.viewer.tiers");

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(RESEARCH);
        registry.addCategory(COPY);
        registry.addCategory(TIER);
        registry.addWorkstation(RESEARCH, ARCHIVE);
        registry.addWorkstation(COPY, ARCHIVE);
        registry.addWorkstation(TIER, ARCHIVE);
        for (ViewerCatalog.ResearchStep step : ViewerCatalog.researchSteps()) {
            registry.addRecipe(new ResearchEmiRecipe(step));
        }
        for (ViewerCatalog.CopyPrint print : ViewerCatalog.copyPrints()) {
            registry.addRecipe(new CopyEmiRecipe(print));
        }
        for (ViewerCatalog.TierPage page : ViewerCatalog.tiers()) {
            registry.addRecipe(new TierEmiRecipe(page));
        }
        List<EmiIngredient> documents = new ArrayList<>();
        for (ItemStack stack : ViewerCatalog.productionDocuments()) {
            documents.add(EmiStack.of(stack));
        }
        if (!documents.isEmpty()) {
            registry.addRecipe(new EmiInfoRecipe(documents, ViewerCatalog.slotRule(),
                    ResourceLocation.fromNamespaceAndPath(BlueprintForge.MOD_ID, "slot_rule")));
            List<Component> enchant = ViewerCatalog.enchantNote();
            if (!enchant.isEmpty()) {
                registry.addRecipe(new EmiInfoRecipe(documents, enchant,
                        ResourceLocation.fromNamespaceAndPath(BlueprintForge.MOD_ID, "enchant_table")));
            }
        }
    }

    private static EmiRecipeCategory category(String path, String titleKey) {
        return new EmiRecipeCategory(ResourceLocation.fromNamespaceAndPath(BlueprintForge.MOD_ID, path), ARCHIVE) {
            @Override
            public Component getName() {
                return Component.translatable(titleKey);
            }
        };
    }

    private static void addCost(WidgetHolder widgets, ItemStack document, List<ItemStack> cost, ItemStack output,
                                BasicEmiRecipe recipe, Component label) {
        widgets.addSlot(EmiStack.of(document), 1, 1);
        int x = 19;
        int y = 1;
        for (ItemStack stack : cost) {
            if (x > 108) {
                x = 1;
                y += 18;
            }
            widgets.addSlot(EmiStack.of(stack), x, y);
            x += 18;
        }
        widgets.addSlot(EmiStack.of(output), 140, 1).recipeContext(recipe);
        widgets.addText(label, 2, 40, 0x404040, false);
    }

    private static final class ResearchEmiRecipe extends BasicEmiRecipe {
        private final ViewerCatalog.ResearchStep step;

        private ResearchEmiRecipe(ViewerCatalog.ResearchStep step) {
            super(RESEARCH, step.id(), ViewerCatalog.WIDTH, ViewerCatalog.HEIGHT);
            this.step = step;
            inputs.add(EmiStack.of(step.document()));
            for (ItemStack cost : step.cost()) {
                inputs.add(EmiStack.of(cost));
            }
            outputs.add(EmiStack.of(step.result()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            addCost(widgets, step.document(), step.cost(), step.result(), this, step.label());
        }
    }

    private static final class CopyEmiRecipe extends BasicEmiRecipe {
        private final ViewerCatalog.CopyPrint print;

        private CopyEmiRecipe(ViewerCatalog.CopyPrint print) {
            super(COPY, print.id(), ViewerCatalog.WIDTH, ViewerCatalog.HEIGHT);
            this.print = print;
            inputs.add(EmiStack.of(print.original()));
            for (ItemStack cost : print.cost()) {
                inputs.add(EmiStack.of(cost));
            }
            outputs.add(EmiStack.of(print.copy()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            addCost(widgets, print.original(), print.cost(), print.copy(), this, print.label());
        }
    }

    private static final class TierEmiRecipe extends BasicEmiRecipe {
        private final ViewerCatalog.TierPage page;

        private TierEmiRecipe(ViewerCatalog.TierPage page) {
            super(TIER, page.id(), ViewerCatalog.WIDTH, ViewerCatalog.HEIGHT);
            this.page = page;
            for (ItemStack example : page.examples()) {
                inputs.add(EmiStack.of(example));
            }
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            int x = 1;
            for (ItemStack example : page.examples()) {
                widgets.addSlot(EmiStack.of(example), x, 1);
                x += 18;
            }
            int y = 22;
            widgets.addText(page.name(), 2, y, 0xFFFFFF, false);
            y += 12;
            widgets.addText(page.requirement(), 2, y, 0x404040, false);
            y += 12;
            widgets.addText(Component.translatable("gui.blueprintforge.viewer.tier_durability", page.durability()),
                    2, y, 0x404040, false);
            if (page.enchantLine() != null) {
                y += 12;
                widgets.addText(page.enchantLine(), 2, y, 0x404040, false);
            }
        }
    }
}
