package com.blueprintforge.client;

import java.util.List;

import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.ResearchProfile;
import com.blueprintforge.data.ResearchRegistry;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.logic.ResearchAxis;
import com.blueprintforge.logic.ResearchRefusal;
import com.blueprintforge.machine.BlueprintArchiveBlockEntity;
import com.blueprintforge.machine.BlueprintArchiveMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Archive screen in the Create palette: dark metal, brass trim, paper slot. Shows the network speed,
 * the belt remake, and the next ME and TE research step.
 */
public class BlueprintArchiveScreen extends AbstractContainerScreen<BlueprintArchiveMenu> {
    private static final int METAL = 0xFF3E4046;
    private static final int METAL_DARK = 0xFF28292E;
    private static final int BRASS = 0xFFC99E48;
    private static final int BRASS_DARK = 0xFF8C682C;
    private static final int PAPER = 0xFFE7DEC4;
    private static final int SLOT = 0xFF16161A;
    private static final int TEXT = 0xFFE7DEC4;
    private static final int TEXT_OK = 0xFF8FD18A;
    private static final int TEXT_WARN = 0xFFE0A050;
    private static final int BAR = 0xFF1A1C16;
    private static final int BAR_FILL = 0xFFC99E48;

    private Button materialButton;
    private Button timeButton;

    public BlueprintArchiveScreen(BlueprintArchiveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = BlueprintArchiveMenu.INVENTORY_Y + 82;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        materialButton = addRenderableWidget(Button.builder(Component.translatable("gui.blueprintforge.archive.research_button_me"),
                        button -> click(0))
                .bounds(leftPos + 118, topPos + 76, 52, 16)
                .build());
        timeButton = addRenderableWidget(Button.builder(Component.translatable("gui.blueprintforge.archive.research_button_te"),
                        button -> click(1))
                .bounds(leftPos + 118, topPos + 100, 52, 16)
                .build());
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (materialButton != null) {
            materialButton.active = menu.datum(BlueprintArchiveBlockEntity.DATA_ME_REFUSAL) == ResearchRefusal.OK.ordinal();
        }
        if (timeButton != null) {
            timeButton.active = menu.datum(BlueprintArchiveBlockEntity.DATA_TE_REFUSAL) == ResearchRefusal.OK.ordinal();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, BRASS_DARK);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, BRASS);
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, METAL);
        graphics.fill(x + 3, y + imageHeight - 86, x + imageWidth - 3, y + imageHeight - 85, METAL_DARK);

        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, METAL_DARK);
            graphics.fill(sx, sy, sx + 16, sy + 16, SLOT);
        }
        int dx = x + BlueprintArchiveMenu.DOCUMENT_SLOT_X;
        int dy = y + BlueprintArchiveMenu.DOCUMENT_SLOT_Y;
        graphics.fill(dx - 3, dy - 3, dx + 19, dy + 19, BRASS);
        graphics.fill(dx - 2, dy - 2, dx + 18, dy + 18, PAPER);
        graphics.fill(dx, dy, dx + 16, dy + 16, SLOT);

        int progress = menu.datum(BlueprintArchiveBlockEntity.DATA_PROGRESS);
        int total = menu.datum(BlueprintArchiveBlockEntity.DATA_TOTAL);
        if (total > 0) {
            int barX = x + 8;
            int barY = y + 122;
            int barWidth = 160;
            graphics.fill(barX, barY, barX + barWidth, barY + 6, BAR);
            int filled = Math.min(barWidth, progress * barWidth / total);
            graphics.fill(barX, barY, barX + filled, barY + 6, BAR_FILL);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        float speed = Math.abs(menu.networkSpeed());
        Component status = speed > 0
                ? Component.translatable("gui.blueprintforge.archive.speed", Math.round(speed))
                : Component.translatable("gui.blueprintforge.archive.no_rotation");
        graphics.drawString(font, status, 8, 18, speed > 0 ? TEXT_OK : TEXT_WARN, false);

        ItemStack document = menu.document();
        Component documentLine = document.isEmpty()
                ? Component.translatable("gui.blueprintforge.archive.empty")
                : document.getHoverName();
        graphics.drawString(font, font.split(documentLine, 160).getFirst(), 8, 50, TEXT, false);

        Component remake = remakeLine(document);
        if (remake != null) {
            List<FormattedCharSequence> lines = font.split(remake, imageWidth - 16);
            graphics.drawString(font, lines.getFirst(), 8, 62, TEXT, false);
        }

        drawAxis(graphics, ResearchAxis.MATERIAL, BlueprintArchiveBlockEntity.DATA_ME, BlueprintArchiveBlockEntity.DATA_ME_MAX,
                BlueprintArchiveBlockEntity.DATA_ME_NEXT, BlueprintArchiveBlockEntity.DATA_ME_REFUSAL, 76);
        drawAxis(graphics, ResearchAxis.TIME, BlueprintArchiveBlockEntity.DATA_TE, BlueprintArchiveBlockEntity.DATA_TE_MAX,
                BlueprintArchiveBlockEntity.DATA_TE_NEXT, BlueprintArchiveBlockEntity.DATA_TE_REFUSAL, 100);

        int axis = menu.datum(BlueprintArchiveBlockEntity.DATA_AXIS);
        if (axis != 0) {
            Component progress = Component.translatable("gui.blueprintforge.archive.research_progress",
                    menu.datum(BlueprintArchiveBlockEntity.DATA_PROGRESS), menu.datum(BlueprintArchiveBlockEntity.DATA_TOTAL));
            graphics.drawString(font, progress, 8, 112, TEXT_OK, false);
        }
    }

    private void drawAxis(GuiGraphics graphics, ResearchAxis axis, int valueIndex, int maxIndex, int nextIndex, int refusalIndex, int y) {
        int value = menu.datum(valueIndex);
        int max = menu.datum(maxIndex);
        int next = menu.datum(nextIndex);
        String label = axis == ResearchAxis.MATERIAL ? "gui.blueprintforge.archive.research_me" : "gui.blueprintforge.archive.research_te";
        Component numbers = next < 0
                ? Component.translatable(label, value, max, Component.translatable("gui.blueprintforge.archive.research.at_cap"))
                : Component.translatable(label, value, max, Component.translatable("gui.blueprintforge.archive.research_next", next));
        graphics.drawString(font, font.split(numbers, 108).getFirst(), 8, y, TEXT, false);

        ResearchRefusal refusal = refusal(menu.datum(refusalIndex));
        Component detail = refusal == ResearchRefusal.OK || refusal == ResearchRefusal.BUSY
                ? costLine(axis)
                : Component.translatable(refusal.translationKey());
        List<FormattedCharSequence> lines = font.split(detail, 108);
        if (!lines.isEmpty()) {
            graphics.drawString(font, lines.getFirst(), 8, y + 10, refusal == ResearchRefusal.OK ? TEXT : TEXT_WARN, false);
        }
    }

    private ResearchRefusal refusal(int ordinal) {
        ResearchRefusal[] values = ResearchRefusal.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return ResearchRefusal.NO_PROFILE;
        }
        return values[ordinal];
    }

    private Component costLine(ResearchAxis axis) {
        BlueprintData data = BlueprintItem.data(menu.document()).orElse(null);
        if (data == null) {
            return Component.empty();
        }
        ResearchProfile profile = ResearchRegistry.forBlueprint(data.definitionId()).orElse(null);
        if (profile == null) {
            return Component.empty();
        }
        List<BlueprintDefinition.CostEntry> cost = axis == ResearchAxis.MATERIAL ? profile.meStepCost() : profile.teStepCost();
        if (cost.isEmpty()) {
            return Component.translatable("gui.blueprintforge.archive.research_stress",
                    menu.datum(BlueprintArchiveBlockEntity.DATA_STRESS));
        }
        MutableComponent line = Component.empty();
        boolean any = false;
        for (BlueprintDefinition.CostEntry entry : cost) {
            if (any) {
                line.append(", ");
            }
            any = true;
            Component piece = entry.itemOrFluid().map(
                    item -> Component.translatable("gui.blueprintforge.archive.research_cost", entry.amount(), itemName(item)),
                    fluid -> Component.translatable(ResearchRefusal.FLUID_COST.translationKey()));
            line.append(piece);
        }
        return line;
    }

    private static Component itemName(net.minecraft.resources.ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id)
                .map(item -> Component.translatable(item.getDescriptionId()))
                .orElseGet(() -> Component.translatable("gui.blueprintforge.archive.remake_unknown"));
    }

    /** What the belt will rewrite, including a copy's remaining runs, before the player sends an item through. */
    private static Component remakeLine(ItemStack document) {
        BlueprintData data = BlueprintItem.data(document).orElse(null);
        if (data == null) {
            return null;
        }
        BlueprintDefinition definition = BlueprintRegistry.get(data.definitionId()).orElse(null);
        if (definition == null || definition.remake().isEmpty() || definition.target().isEmpty()) {
            return null;
        }
        Component target = BuiltInRegistries.ITEM.getOptional(definition.target().get())
                .map(item -> Component.translatable(item.getDescriptionId()))
                .orElseGet(() -> Component.translatable("gui.blueprintforge.archive.remake_unknown"));
        if (data.clazz() == BlueprintClass.COPY) {
            return Component.translatable("gui.blueprintforge.archive.remake_copy", target, data.runsRemaining());
        }
        return Component.translatable("gui.blueprintforge.archive.remake", target);
    }
}
