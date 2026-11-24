package com.blueprintforge.client;

import java.util.ArrayList;
import java.util.List;

import com.blueprintforge.compat.createapi.SpikeLine;
import com.blueprintforge.item.ResearchBlueprintItem;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.research.ResearchBlueprint;
import com.blueprintforge.research.ResearchRun;
import com.blueprintforge.terminal.LineScanner;
import com.blueprintforge.terminal.TerminalBlockEntity;
import com.blueprintforge.terminal.TerminalMenu;
import com.blueprintforge.terminal.TerminalSlots;
import com.simibubi.create.AllBlocks;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Terminal screen of mockup v3, drawn in code: cold steel with teal growth on the frame, a green phosphor screen, a
 * needle gauge and nixie digits. The canon is what it shows: operation counts without order, research of the last run,
 * what the line lacks, and the rule that only 100% gives the item. The copies tab is drawn greyed and does nothing (0.2).
 * No effect or bonus rows: the data for them does not exist yet.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final int OUTLINE = 0xFF20262D;
    private static final int STEEL = 0xFF9CA6B1;
    private static final int STEEL_LIGHT = 0xFFC4CCD6;
    private static final int STEEL_DARK = 0xFF5A6470;
    private static final int STEEL_DEEP = 0xFF343B44;
    private static final int TEAL = 0xFF22D3C0;
    private static final int TEAL_DARK = 0xFF0C4F4E;
    private static final int CRT = 0xFF05110A;
    private static final int CRT_LINE = 0x14000000;
    private static final int GREEN = 0xFF4CFF7A;
    private static final int GREEN_DIM = 0xFF2A9A4A;
    private static final int GREEN_GHOST = 0xFF0F3A1E;
    private static final int AMBER = 0xFFFFC53D;
    private static final int RED = 0xFFFF5A4A;
    private static final int LABEL = 0xFF2A3038;

    /** Official icon colours of the five tiers, taken from the mid tone of the icons. */
    private static final int[] TIER_COLORS = {0xFF4A90E6, 0xFF6878EC, 0xFF9068EA, 0xFFC258E2, 0xFFF8CC38};

    // 5x7 digits of the nixie panel, one row per int, the left pixel being the highest of five bits.
    private static final int[][] GLYPHS = {
            {0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110}, {0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110},
            {0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111}, {0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110},
            {0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010}, {0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110},
            {0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110}, {0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000},
            {0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110}, {0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100}};

    private static final int CRT_X = 62;
    private static final int CRT_Y = 22;
    private static final int CRT_W = 311;
    private static final int CRT_H = 132;
    private static final int DIVIDER_X = 246;

    private Button action;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 385;
        imageHeight = 265;
    }

    @Override
    protected void init() {
        super.init();
        action = addRenderableWidget(new Button(leftPos + 62, topPos + 158, 310, 15, Component.empty(), button -> press(), narration -> narration.get()) {
            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                int fill = !active ? STEEL_DEEP : isHoveredOrFocused() ? STEEL_LIGHT : STEEL;
                graphics.fill(getX(), getY(), getX() + width, getY() + height, OUTLINE);
                graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, fill);
                graphics.fill(getX() + 1, getY() + height - 3, getX() + width - 1, getY() + height - 1, active ? STEEL_DARK : OUTLINE);
                graphics.fill(getX() + 3, getY() + 3, getX() + 5, getY() + 5, TEAL_DARK);
                graphics.fill(getX() + width - 5, getY() + 3, getX() + width - 3, getY() + 5, TEAL_DARK);
                graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 4, active ? LABEL : STEEL_DARK);
            }
        });
    }

    private void press() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TerminalMenu.BUTTON_ACTION);
        }
    }

    private ItemStack itemSlot() {
        return menu.getSlot(TerminalSlots.ITEM).getItem();
    }

    private boolean workpieceInSlot() {
        ItemStack stack = itemSlot();
        return SpikeLine.isWorkpiece(stack) && stack.has(BFComponents.RESEARCH_RUN.get());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ResearchBlueprint blueprint = ResearchBlueprintItem.data(menu.getSlot(TerminalSlots.BLUEPRINT).getItem()).orElse(null);
        boolean outputFree = menu.getSlot(TerminalSlots.OUTPUT).getItem().isEmpty();
        if (workpieceInSlot()) {
            action.setMessage(Component.translatable("gui.blueprintforge.terminal.action.check"));
            action.active = outputFree;
            action.setTooltip(Tooltip.create(Component.translatable("gui.blueprintforge.terminal.action.check.tooltip")));
        } else {
            Component target = blueprint == null ? Component.translatable("gui.blueprintforge.terminal.slot.1")
                    : ResearchBlueprintItem.targetName(blueprint.item());
            action.setMessage(Component.translatable("gui.blueprintforge.terminal.action.make", target));
            action.active = blueprint != null && !itemSlot().isEmpty() && outputFree;
            action.setTooltip(Tooltip.create(Component.translatable("gui.blueprintforge.terminal.action.make.tooltip")));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < TerminalSlots.COUNT) {
            graphics.renderTooltip(font, Component.translatable("gui.blueprintforge.terminal.slot." + hoveredSlot.index), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Everything is drawn in renderBg; the default title and inventory labels are not wanted.
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        drawFrame(graphics, x, y);
        drawTabs(graphics, x, y);
        drawSlotsAndLabels(graphics, x, y);
        drawGauge(graphics, x + 29, y + 145);
        drawPlateAndPanel(graphics, x, y);
        drawScreen(graphics, x, y);
    }

    private static void rect(GuiGraphics graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + h, colour);
    }

    private static void bevel(GuiGraphics graphics, int x, int y, int w, int h, int fill) {
        rect(graphics, x, y, w, h, OUTLINE);
        rect(graphics, x + 1, y + 1, w - 2, h - 2, fill);
        rect(graphics, x + 1, y + 1, w - 2, 1, STEEL_LIGHT);
        rect(graphics, x + 1, y + h - 2, w - 2, 1, STEEL_DARK);
    }

    private void drawFrame(GuiGraphics graphics, int x, int y) {
        rect(graphics, x, y, imageWidth, imageHeight, OUTLINE);
        rect(graphics, x + 2, y + 2, imageWidth - 4, imageHeight - 4, STEEL_DARK);
        rect(graphics, x + 3, y + 3, imageWidth - 6, imageHeight - 6, STEEL);
        // Inventory recess.
        bevel(graphics, x + 44, y + 180, 168, 80, STEEL_DEEP);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            rect(graphics, sx - 1, sy - 1, 18, 18, OUTLINE);
            rect(graphics, sx, sy, 16, 16, 0xFF1A2027);
        }
        // Teal growth on the frame: a fixed scatter, denser in the corners and along the left edge.
        long seed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < 90; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int u = (int) ((seed >>> 33) % 1000);
            int v = (int) ((seed >>> 13) % 1000);
            int px;
            int py;
            switch (i % 4) {
                case 0 -> { px = 3 + u % 6; py = 4 + v * (imageHeight - 8) / 1000; }
                case 1 -> { px = 4 + u * (imageWidth - 8) / 1000; py = imageHeight - 5 - v % 6; }
                case 2 -> { px = imageWidth - 4 - u % 4; py = 4 + v * (imageHeight - 8) / 1000 / 2; }
                default -> { px = 3 + u % 24; py = imageHeight - 4 - v % 24; }
            }
            rect(graphics, x + px, y + py, 1 + (u & 1), 1, (i % 3 == 0) ? TEAL : TEAL_DARK);
        }
    }

    private void drawTabs(GuiGraphics graphics, int x, int y) {
        bevel(graphics, x + 14, y + 5, 124, 13, STEEL_LIGHT);
        graphics.drawString(font, title, x + 20, y + 8, LABEL, false);
        bevel(graphics, x + 144, y + 5, 87, 13, STEEL);
        graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.tab.research"), x + 150, y + 8, LABEL, false);
        bevel(graphics, x + 234, y + 7, 43, 11, STEEL_DEEP);
        graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.tab.copies"), x + 239, y + 9, STEEL_DARK, false);
        boolean hasBlueprint = (menu.data(TerminalBlockEntity.DATA_FLAGS) & TerminalBlockEntity.FLAG_BLUEPRINT) != 0;
        boolean lacks = hasBlueprint && menu.data(TerminalBlockEntity.DATA_MISSING) != 0;
        lamp(graphics, x + 335, y + 9, GREEN, true);
        lamp(graphics, x + 347, y + 9, AMBER, hasBlueprint);
        lamp(graphics, x + 359, y + 9, RED, lacks);
    }

    private static void lamp(GuiGraphics graphics, int x, int y, int colour, boolean lit) {
        rect(graphics, x - 1, y - 1, 8, 8, OUTLINE);
        rect(graphics, x, y, 6, 6, lit ? colour : 0xFF1C2228);
    }

    private void drawSlotsAndLabels(GuiGraphics graphics, int x, int y) {
        String[] keys = {"blueprint", "item", "output"};
        for (int i = 0; i < 3; i++) {
            Component label = Component.translatable("gui.blueprintforge.terminal.label." + keys[i]);
            graphics.drawCenteredString(font, label, x + TerminalMenu.SLOT_X[i] + 8, y + TerminalMenu.SLOT_Y[i] - 12, LABEL);
        }
        int bx = x + TerminalMenu.SLOT_X[0];
        int by = y + TerminalMenu.SLOT_Y[0];
        for (int[] corner : new int[][] {{-3, -3}, {17, -3}, {-3, 17}, {17, 17}}) {
            rect(graphics, bx + corner[0], by + corner[1], 2, 2, TEAL);
        }
        // A ghost of the target in the item slot while it is empty, as the mockup shows it.
        ResearchBlueprint blueprint = ResearchBlueprintItem.data(menu.getSlot(TerminalSlots.BLUEPRINT).getItem()).orElse(null);
        if (blueprint != null && itemSlot().isEmpty()) {
            ItemStack ghost = new ItemStack(BuiltInRegistries.ITEM.get(blueprint.item()));
            graphics.renderItem(ghost, x + TerminalMenu.SLOT_X[1], y + TerminalMenu.SLOT_Y[1]);
            rect(graphics, x + TerminalMenu.SLOT_X[1], y + TerminalMenu.SLOT_Y[1], 16, 16, 0xA0161B21);
        }
    }

    private void drawGauge(GuiGraphics graphics, int cx, int cy) {
        int radius = 17;
        for (int dy = -radius - 2; dy <= radius + 2; dy++) {
            int outer = (int) Math.sqrt((radius + 2) * (radius + 2) - dy * dy);
            rect(graphics, cx - outer, cy + dy, outer * 2, 1, OUTLINE);
        }
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(radius * radius - dy * dy);
            rect(graphics, cx - half, cy + dy, half * 2, 1, CRT);
        }
        for (int i = 0; i <= 10; i++) {
            double angle = Math.toRadians(-225 + i * 27);
            int colour = i >= 9 ? RED : GREEN_DIM;
            for (double r = radius - 4; r < radius - 1; r += 0.5) {
                rect(graphics, cx + (int) Math.round(Math.cos(angle) * r), cy + (int) Math.round(Math.sin(angle) * r), 1, 1, colour);
            }
        }
        int percent = Math.max(0, menu.data(TerminalBlockEntity.DATA_PERCENT));
        double angle = Math.toRadians(-225 + 270 * percent / 100.0);
        for (double r = 0; r < radius - 5; r += 0.5) {
            rect(graphics, cx + (int) Math.round(Math.cos(angle) * r), cy + (int) Math.round(Math.sin(angle) * r), 1, 1, GREEN);
        }
        rect(graphics, cx - 1, cy - 1, 3, 3, STEEL_LIGHT);
        graphics.drawCenteredString(font, Component.translatable("gui.blueprintforge.terminal.gauge"), cx, cy + radius + 6, LABEL);
    }

    private void drawPlateAndPanel(GuiGraphics graphics, int x, int y) {
        // Grille.
        bevel(graphics, x + 220, y + 182, 88, 15, STEEL_DEEP);
        for (int i = 0; i < 4; i++) {
            rect(graphics, x + 223, y + 185 + i * 3, 82, 1, OUTLINE);
        }
        // Plate with an eye and a decorative code; the code is an example from the mockup.
        bevel(graphics, x + 224, y + 202, 81, 46, STEEL_DEEP);
        rect(graphics, x + 258, y + 210, 13, 1, STEEL_LIGHT);
        rect(graphics, x + 256, y + 211, 17, 1, STEEL_LIGHT);
        rect(graphics, x + 257, y + 212, 15, 3, STEEL_LIGHT);
        rect(graphics, x + 262, y + 211, 5, 5, OUTLINE);
        graphics.drawCenteredString(font, "AC-04", x + 264, y + 230, STEEL_LIGHT);
        // Nixie panel: tier and the percent of the last run.
        bevel(graphics, x + 317, y + 186, 56, 72, STEEL_DEEP);
        graphics.drawCenteredString(font, Component.translatable("gui.blueprintforge.terminal.panel.tier"), x + 345, y + 191, STEEL_LIGHT);
        int tier = menu.data(TerminalBlockEntity.DATA_SPRITE_TIER);
        drawNixie(graphics, x + 337, y + 202, tier > 0 ? String.valueOf(tier) : " ", 1);
        graphics.drawCenteredString(font, "%", x + 345, y + 222, STEEL_LIGHT);
        int percent = menu.data(TerminalBlockEntity.DATA_PERCENT);
        drawNixie(graphics, x + 325, y + 234, percent < 0 ? "   " : String.valueOf(percent), 3);
    }

    /** Right-aligned digits in a row of {@code cells} nixie tubes, each lit pixel drawn as a 2x2 square over a ghost of 8. */
    private void drawNixie(GuiGraphics graphics, int x, int y, String text, int cells) {
        for (int cell = 0; cell < cells; cell++) {
            int cellX = x + cell * 14;
            rect(graphics, cellX - 2, y - 2, 14, 18, OUTLINE);
            drawGlyph(graphics, cellX, y, 8, GREEN_GHOST);
            int index = cell - (cells - text.length());
            if (index >= 0 && Character.isDigit(text.charAt(index))) {
                drawGlyph(graphics, cellX, y, text.charAt(index) - '0', GREEN);
            }
        }
    }

    private static void drawGlyph(GuiGraphics graphics, int x, int y, int digit, int colour) {
        int[] rows = GLYPHS[digit];
        for (int row = 0; row < 7; row++) {
            for (int column = 0; column < 5; column++) {
                if ((rows[row] >> (4 - column) & 1) != 0) {
                    rect(graphics, x + column * 2, y + row * 2, 2, 2, colour);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    private void drawScreen(GuiGraphics graphics, int x, int y) {
        bevel(graphics, x + CRT_X - 3, y + CRT_Y - 3, CRT_W + 6, CRT_H + 6, STEEL_LIGHT);
        rect(graphics, x + CRT_X, y + CRT_Y, CRT_W, CRT_H, CRT);
        for (int line = 1; line < CRT_H; line += 2) {
            rect(graphics, x + CRT_X, y + CRT_Y + line, CRT_W, 1, CRT_LINE);
        }
        rect(graphics, x + DIVIDER_X, y + CRT_Y + 4, 1, CRT_H - 8, GREEN_GHOST);

        graphics.enableScissor(x + CRT_X, y + CRT_Y, x + CRT_X + CRT_W, y + CRT_Y + CRT_H);
        ResearchBlueprint blueprint = ResearchBlueprintItem.data(menu.getSlot(TerminalSlots.BLUEPRINT).getItem()).orElse(null);
        if (blueprint == null) {
            graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.empty"), x + CRT_X + 8, y + CRT_Y + 6, GREEN_DIM, false);
            graphics.disableScissor();
            return;
        }
        drawLeftColumn(graphics, x + CRT_X + 6, y + CRT_Y + 4, DIVIDER_X - CRT_X - 12, blueprint);
        drawRightColumn(graphics, x + DIVIDER_X + 6, y + CRT_Y + 4, CRT_X + CRT_W - DIVIDER_X - 10);
        graphics.disableScissor();
    }

    private void drawLeftColumn(GuiGraphics graphics, int x, int y, int width, ResearchBlueprint blueprint) {
        ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(blueprint.item()));
        graphics.renderItem(icon, x, y);
        Component name = ResearchBlueprintItem.targetName(blueprint.item());
        graphics.drawString(font, name, x + 20, y + 1, GREEN, false);
        int tierColour = TIER_COLORS[Math.max(0, Math.min(4, blueprint.spriteTier() - 1))];
        Component tier = ResearchBlueprintItem.tierName(blueprint.tier());
        int badgeWidth = font.width(tier) + 6;
        rect(graphics, x + width - badgeWidth, y, badgeWidth, 10, tierColour);
        rect(graphics, x + width - badgeWidth + 1, y + 1, badgeWidth - 2, 8, CRT);
        graphics.drawString(font, tier, x + width - badgeWidth + 3, y + 1, tierColour, false);
        graphics.drawString(font, Component.translatable(blueprint.copy() ? "gui.blueprintforge.terminal.copy" : "gui.blueprintforge.terminal.original"),
                x + 20, y + 11, blueprint.copy() ? AMBER : GREEN, false);
        int line = y + 26;
        rect(graphics, x, line - 4, width, 1, GREEN_GHOST);
        graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.hidden"), x, line, GREEN_DIM, false);
        line += 16;
        graphics.renderItem(icon, x, line - 3);
        graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.start", name), x + 20, line + 1, GREEN, false);
        line += 18;
        for (FormattedCharSequence part : font.split(Component.translatable("gui.blueprintforge.terminal.start.note"), width)) {
            graphics.drawString(font, part, x, line, GREEN_DIM, false);
            line += 9;
        }
    }

    private void drawRightColumn(GuiGraphics graphics, int x, int y, int width) {
        int flags = menu.data(TerminalBlockEntity.DATA_FLAGS);
        if ((flags & TerminalBlockEntity.FLAG_UNKNOWN_TIER) != 0) {
            graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.unknown_tier"), x, y + 1, RED, false);
            return;
        }
        graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.steps", menu.data(TerminalBlockEntity.DATA_STEPS)), x, y + 1, GREEN, false);
        int line = y + 14;
        line = opRow(graphics, x, line, width, new ItemStack(AllBlocks.MECHANICAL_PRESS.get()), "press", menu.data(TerminalBlockEntity.DATA_PRESS));
        line = opRow(graphics, x, line, width, new ItemStack(AllBlocks.ENCASED_FAN.get()), "blast", menu.data(TerminalBlockEntity.DATA_BLAST));
        if (menu.data(TerminalBlockEntity.DATA_OTHER) > 0) {
            line = opRow(graphics, x, line, width, ItemStack.EMPTY, "other", menu.data(TerminalBlockEntity.DATA_OTHER));
        }
        rect(graphics, x, line + 1, width, 1, GREEN_GHOST);
        line += 5;

        int percent = menu.data(TerminalBlockEntity.DATA_PERCENT);
        Component research = percent < 0 ? Component.translatable("gui.blueprintforge.terminal.research.none")
                : Component.translatable("gui.blueprintforge.terminal.research", percent);
        graphics.drawString(font, research, x, line, percent >= 100 ? GREEN : percent < 0 ? GREEN_DIM : AMBER, false);
        line += 10;
        int total = Math.max(1, menu.data(TerminalBlockEntity.DATA_TOTAL));
        int matched = percent < 0 ? 0 : menu.data(TerminalBlockEntity.DATA_MATCHED);
        int barWidth = width - 26;
        rect(graphics, x, line, barWidth, 6, GREEN_GHOST);
        int segments = Math.min(total, 16);
        for (int i = 0; i < segments; i++) {
            int segmentX = x + 1 + i * (barWidth - 2) / segments;
            int segmentWidth = Math.max(1, (barWidth - 2) / segments - 1);
            if (i * total / segments < matched) {
                rect(graphics, segmentX, line + 1, segmentWidth, 4, percent >= 100 ? GREEN : AMBER);
            }
        }
        graphics.drawString(font, percent < 0 ? "-/" + menu.data(TerminalBlockEntity.DATA_STEPS) : matched + "/" + total, x + barWidth + 3, line - 1, GREEN_DIM, false);
        line += 10;

        // Recorded steps: a workpiece waiting in the item slot shows its count at once, a checked run keeps its count.
        ItemStack workpiece = itemSlot();
        ResearchRun run = workpiece.get(BFComponents.RESEARCH_RUN.get());
        int recorded = -1;
        int steps = menu.data(TerminalBlockEntity.DATA_STEPS);
        if (run != null && SpikeLine.isWorkpiece(workpiece)) {
            recorded = SpikeLine.operationsOf(workpiece).size();
            steps = run.steps() > 0 ? run.steps() : steps;
        } else if (percent >= 0) {
            recorded = menu.data(TerminalBlockEntity.DATA_LAST_RECORDED);
            steps = menu.data(TerminalBlockEntity.DATA_TOTAL);
        }
        if (recorded >= 0) {
            graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.recorded", recorded, steps), x, line,
                    recorded == steps ? GREEN : AMBER, false);
            line += 10;
        }
        Component status = percent >= 100 && !menu.getSlot(TerminalSlots.OUTPUT).getItem().isEmpty()
                ? Component.translatable("gui.blueprintforge.terminal.solved") : Component.translatable("gui.blueprintforge.terminal.warning");
        int statusColour = percent >= 100 ? GREEN : RED;
        for (FormattedCharSequence part : font.split(status, width)) {
            graphics.drawString(font, part, x, line, statusColour, false);
            line += 9;
        }
        rect(graphics, x, line + 1, width, 1, GREEN_GHOST);
        line += 5;
        drawLine(graphics, x, line, width);
    }

    private int opRow(GuiGraphics graphics, int x, int y, int width, ItemStack icon, String key, int count) {
        if (!icon.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(0.5F, 0.5F, 1F);
            graphics.renderItem(icon, 0, 0);
            graphics.pose().popPose();
        }
        graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.ops." + key), x + 11, y, GREEN, false);
        String times = "×" + count;
        graphics.drawString(font, times, x + width - font.width(times), y, GREEN, false);
        return y + 10;
    }

    /** "What the line lacks": the machines the chain needs and the scan around the terminal did not find. */
    private void drawLine(GuiGraphics graphics, int x, int y, int width) {
        int missing = menu.data(TerminalBlockEntity.DATA_MISSING);
        if (missing == 0) {
            graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.line.ok"), x, y, GREEN, false);
            return;
        }
        graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.line.missing"), x, y, AMBER, false);
        List<String> names = new ArrayList<>();
        if ((missing & LineScanner.PRESS) != 0) {
            names.add("press");
        }
        if ((missing & LineScanner.BLAST) != 0) {
            names.add("blast");
        }
        int line = y + 10;
        for (String name : names) {
            graphics.drawString(font, "× ", x, line, RED, false);
            for (FormattedCharSequence part : font.split(Component.translatable("gui.blueprintforge.terminal.machine." + name), width - 10)) {
                graphics.drawString(font, part, x + 9, line, RED, false);
                line += 9;
            }
        }
    }
}
