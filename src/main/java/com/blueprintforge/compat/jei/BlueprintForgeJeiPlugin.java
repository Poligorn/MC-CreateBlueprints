package com.blueprintforge.compat.jei;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.compat.viewer.ViewerCatalog;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.registry.BFItems;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Optional. Loaded by JEI's annotation scan only. The mod does not reference this class. */
@JeiPlugin
public class BlueprintForgeJeiPlugin implements IModPlugin {
    public static final RecipeType<ViewerCatalog.ResearchStep> RESEARCH =
            RecipeType.create(BlueprintForge.MOD_ID, "research", ViewerCatalog.ResearchStep.class);
    public static final RecipeType<ViewerCatalog.CopyPrint> COPY =
            RecipeType.create(BlueprintForge.MOD_ID, "copy", ViewerCatalog.CopyPrint.class);
    public static final RecipeType<ViewerCatalog.TierPage> TIER =
            RecipeType.create(BlueprintForge.MOD_ID, "tier", ViewerCatalog.TierPage.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(BlueprintForge.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        IDrawable icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BFItems.BLUEPRINT_ARCHIVE.get()));
        registration.addRecipeCategories(new ResearchCategory(icon), new CopyCategory(icon), new TierCategory(icon));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(RESEARCH, ViewerCatalog.researchSteps());
        registration.addRecipes(COPY, ViewerCatalog.copyPrints());
        registration.addRecipes(TIER, ViewerCatalog.tiers());
        List<ItemStack> documents = ViewerCatalog.productionDocuments();
        if (!documents.isEmpty()) {
            registration.addItemStackInfo(documents, ViewerCatalog.slotRule().toArray(Component[]::new));
            List<Component> enchant = ViewerCatalog.enchantNote();
            if (!enchant.isEmpty()) {
                registration.addItemStackInfo(documents, enchant.toArray(Component[]::new));
            }
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(BFItems.BLUEPRINT_ARCHIVE.get()), RESEARCH, COPY, TIER);
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(BFItems.BLUEPRINT.get(), new BlueprintSubtype());
    }

    private static void placeInputs(IRecipeLayoutBuilder builder, ItemStack document, List<ItemStack> cost, ItemStack output) {
        int x = 1;
        int y = 1;
        builder.addInputSlot(x, y).setStandardSlotBackground().addItemStack(document);
        x += 18;
        for (ItemStack stack : cost) {
            if (x > 108) {
                x = 1;
                y += 18;
            }
            builder.addInputSlot(x, y).setStandardSlotBackground().addItemStack(stack);
            x += 18;
        }
        builder.addOutputSlot(140, 1).setOutputSlotBackground().addItemStack(output);
    }

    private static void drawLabel(GuiGraphics graphics, Component label) {
        graphics.drawString(Minecraft.getInstance().font, label, 2, 40, 0x404040, false);
    }

    private static final class ResearchCategory implements IRecipeCategory<ViewerCatalog.ResearchStep> {
        private final IDrawable icon;

        private ResearchCategory(IDrawable icon) {
            this.icon = icon;
        }

        @Override
        public RecipeType<ViewerCatalog.ResearchStep> getRecipeType() {
            return RESEARCH;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gui.blueprintforge.viewer.research");
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public int getWidth() {
            return ViewerCatalog.WIDTH;
        }

        @Override
        public int getHeight() {
            return ViewerCatalog.HEIGHT;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ViewerCatalog.ResearchStep recipe, IFocusGroup focuses) {
            placeInputs(builder, recipe.document(), recipe.cost(), recipe.result());
        }

        @Override
        public void draw(ViewerCatalog.ResearchStep recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                         double mouseX, double mouseY) {
            drawLabel(guiGraphics, recipe.label());
        }
    }

    private static final class CopyCategory implements IRecipeCategory<ViewerCatalog.CopyPrint> {
        private final IDrawable icon;

        private CopyCategory(IDrawable icon) {
            this.icon = icon;
        }

        @Override
        public RecipeType<ViewerCatalog.CopyPrint> getRecipeType() {
            return COPY;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gui.blueprintforge.viewer.copy");
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public int getWidth() {
            return ViewerCatalog.WIDTH;
        }

        @Override
        public int getHeight() {
            return ViewerCatalog.HEIGHT;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ViewerCatalog.CopyPrint recipe, IFocusGroup focuses) {
            placeInputs(builder, recipe.original(), recipe.cost(), recipe.copy());
        }

        @Override
        public void draw(ViewerCatalog.CopyPrint recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                         double mouseX, double mouseY) {
            drawLabel(guiGraphics, recipe.label());
        }
    }

    private static final class TierCategory implements IRecipeCategory<ViewerCatalog.TierPage> {
        private final IDrawable icon;

        private TierCategory(IDrawable icon) {
            this.icon = icon;
        }

        @Override
        public RecipeType<ViewerCatalog.TierPage> getRecipeType() {
            return TIER;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gui.blueprintforge.viewer.tiers");
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public int getWidth() {
            return ViewerCatalog.WIDTH;
        }

        @Override
        public int getHeight() {
            return ViewerCatalog.HEIGHT;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ViewerCatalog.TierPage recipe, IFocusGroup focuses) {
            int x = 1;
            for (ItemStack example : recipe.examples()) {
                builder.addInputSlot(x, 1).setStandardSlotBackground().addItemStack(example);
                x += 18;
            }
        }

        @Override
        public void draw(ViewerCatalog.TierPage recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                         double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            int y = 22;
            guiGraphics.drawString(font, recipe.name(), 2, y, 0xFFFFFF, false);
            y += 12;
            guiGraphics.drawString(font, recipe.requirement(), 2, y, 0x404040, false);
            y += 12;
            guiGraphics.drawString(font, Component.translatable("gui.blueprintforge.viewer.tier_durability", recipe.durability()),
                    2, y, 0x404040, false);
            if (recipe.enchantLine() != null) {
                y += 12;
                guiGraphics.drawString(font, recipe.enchantLine(), 2, y, 0x404040, false);
            }
        }
    }

    /** Keeps two documents of different definitions from collapsing into one ingredient. */
    private static final class BlueprintSubtype implements ISubtypeInterpreter<ItemStack> {
        @Override
        public @Nullable Object getSubtypeData(ItemStack ingredient, UidContext context) {
            BlueprintData data = BlueprintItem.data(ingredient).orElse(null);
            if (data == null) {
                return null;
            }
            return data.definitionId() + "/" + data.clazz().getSerializedName();
        }

        @Override
        public String getLegacyStringSubtypeInfo(ItemStack ingredient, UidContext context) {
            Object data = getSubtypeData(ingredient, context);
            return data == null ? "" : data.toString();
        }
    }
}
