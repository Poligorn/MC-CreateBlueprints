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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Terminal screen in the "Create computer" style of mockup v2, drawn in code: andesite body, brass trim, a dark amber
 * screen. Only what the canon allows is shown: operation counts without order, what the line lacks, the last run and the
 * rule that only 100% yields the item. No step order, no starting ingot, no copy tabs. The gauge, nixie lamps and gears
 * of the prototype are left out (open question 36).
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final int ANDESITE = 0xFF86877F;
    private static final int ANDESITE_DARK = 0xFF5F6059;
    private static final int ANDESITE_LIGHT = 0xFFA3A49B;
    private static final int BRASS = 0xFFC99E48;
    private static final int BRASS_DARK = 0xFF8C682C;
    private static final int BRASS_LIGHT = 0xFFE6C070;
    private static final int CRT = 0xFF0B120F;
    private static final int CRT_LINE = 0x22000000;
    private static final int SLOT = 0xFF16161A;
    private static final int AMBER = 0xFFFFB000;
    private static final int AMBER_DIM = 0xFF9A6A00;
    private static final int GREEN = 0xFF6FD16B;
    private static final int RED = 0xFFE05A4A;
    private static final int PLATE_TEXT = 0xFF2E2210;

    /** Official icon colours of the five tiers, taken from the mid tone of the icons. */
    private static final int[] TIER_COLORS = {0xFF4A90E6, 0xFF6878EC, 0xFF9068EA, 0xFFC258E2, 0xFFF8CC38};

    private static final int CRT_X = 30;
    private static final int CRT_Y = 22;
    private static final int CRT_W = 140;
    private static final int CRT_H = 108;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 240;
        titleLabelX = 8;
        titleLabelY = 7;
        inventoryLabelY = 151;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.blueprintforge.terminal.create"), button -> press(TerminalMenu.BUTTON_CREATE))
                .bounds(leftPos + CRT_X, topPos + 134, 68, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.blueprintforge.terminal.create.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.blueprintforge.terminal.check"), button -> press(TerminalMenu.BUTTON_CHECK))
                .bounds(leftPos + CRT_X + 72, topPos + 134, 68, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.blueprintforge.terminal.check.tooltip"))).build());
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
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
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Body: brass frame around andesite.
        graphics.fill(x, y, x + imageWidth, y + imageHeight, BRASS_DARK);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, BRASS);
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, ANDESITE);
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + 4, ANDESITE_LIGHT);
        graphics.fill(x + 3, y + 156, x + imageWidth - 3, y + 157, ANDESITE_DARK);

        // Title plate with the lamps to its right.
        graphics.fill(x + 5, y + 5, x + 118, y + 19, BRASS_DARK);
        graphics.fill(x + 6, y + 6, x + 117, y + 18, BRASS_LIGHT);
        graphics.fill(x + 7, y + 7, x + 116, y + 17, BRASS);
        boolean hasBlueprint = (menu.data(TerminalBlockEntity.DATA_FLAGS) & TerminalBlockEntity.FLAG_BLUEPRINT) != 0;
        boolean lacksMachines = hasBlueprint && menu.data(TerminalBlockEntity.DATA_MISSING) != 0;
        lamp(graphics, x + 142, y + 9, GREEN, true);
        lamp(graphics, x + 152, y + 9, 0xFFFFC040, hasBlueprint);
        lamp(graphics, x + 162, y + 9, RED, lacksMachines);

        // Screen: brass frame, dark glass, faint scan lines.
        graphics.fill(x + CRT_X - 2, y + CRT_Y - 2, x + CRT_X + CRT_W + 2, y + CRT_Y + CRT_H + 2, BRASS_DARK);
        graphics.fill(x + CRT_X - 1, y + CRT_Y - 1, x + CRT_X + CRT_W + 1, y + CRT_Y + CRT_H + 1, BRASS);
        graphics.fill(x + CRT_X, y + CRT_Y, x + CRT_X + CRT_W, y + CRT_Y + CRT_H, CRT);
        for (int line = 1; line < CRT_H; line += 2) {
            graphics.fill(x + CRT_X, y + CRT_Y + line, x + CRT_X + CRT_W, y + CRT_Y + line + 1, CRT_LINE);
        }

        // Slots, the blueprint slot with brass studs like the prototype.
        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, ANDESITE_DARK);
            graphics.fill(sx, sy, sx + 16, sy + 16, SLOT);
        }
        int bx = x + TerminalMenu.SLOT_X[0];
        int by = y + TerminalMenu.SLOT_Y[0];
        for (int[] corner : new int[][] {{-3, -3}, {17, -3}, {-3, 17}, {17, 17}}) {
            graphics.fill(bx + corner[0], by + corner[1], bx + corner[0] + 2, by + corner[1] + 2, BRASS_LIGHT);
        }
    }

    private static void lamp(GuiGraphics graphics, int x, int y, int color, boolean lit) {
        graphics.fill(x - 1, y - 1, x + 7, y + 7, ANDESITE_DARK);
        graphics.fill(x, y, x + 6, y + 6, lit ? color : 0xFF1C1C1C);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX + 2, titleLabelY, PLATE_TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF2E2E2A, false);

        int x = CRT_X + 4;
        int y = CRT_Y + 4;
        int step = 9;
        int flags = menu.data(TerminalBlockEntity.DATA_FLAGS);
        ResearchBlueprint shown = ResearchBlueprintItem.data(menu.getSlot(TerminalSlots.BLUEPRINT).getItem()).orElse(null);

        if ((flags & TerminalBlockEntity.FLAG_BLUEPRINT) == 0 || shown == null) {
            graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.empty"), x, y, AMBER_DIM, false);
            y += step;
        } else {
            ResearchBlueprint blueprint = shown;
            int tierColor = TIER_COLORS[Math.max(0, Math.min(4, blueprint.spriteTier() - 1))];
            Component tier = ResearchBlueprintItem.tierName(blueprint.tier());
            graphics.drawString(font, ResearchBlueprintItem.targetName(blueprint.item()), x, y, AMBER, false);
            graphics.drawString(font, tier, CRT_X + CRT_W - 4 - font.width(tier), y, tierColor, false);
            y += step;
            if ((flags & TerminalBlockEntity.FLAG_UNKNOWN_TIER) != 0) {
                graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.unknown_tier"), x, y, RED, false);
                y += step;
            } else {
                graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.steps", menu.data(TerminalBlockEntity.DATA_STEPS)), x, y, AMBER, false);
                y += step;
                graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.op.press", menu.data(TerminalBlockEntity.DATA_PRESS)), x + 6, y, AMBER, false);
                y += step;
                graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.op.blast", menu.data(TerminalBlockEntity.DATA_BLAST)), x + 6, y, AMBER, false);
                y += step;
                if (menu.data(TerminalBlockEntity.DATA_OTHER) > 0) {
                    graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.op.other", menu.data(TerminalBlockEntity.DATA_OTHER)), x + 6, y, AMBER, false);
                    y += step;
                }
                y = drawLine(graphics, x, y, step);
            }
        }

        // The recorded-steps hint, the last run and the warning are laid out from the bottom of the screen up, so a long line
        // above never overlaps them.
        int percent = menu.data(TerminalBlockEntity.DATA_PERCENT);
        List<FormattedCharSequence> warning = font.split(Component.translatable("gui.blueprintforge.terminal.warning"), CRT_W - 8);
        int bottom = CRT_Y + CRT_H - 3 - warning.size() * step + 1;
        for (int i = 0; i < warning.size(); i++) {
            graphics.drawString(font, warning.get(i), x, bottom + i * step, AMBER_DIM, false);
        }
        if (percent >= 0) {
            List<FormattedCharSequence> last = font.split(Component.translatable("gui.blueprintforge.terminal.last", percent,
                    menu.data(TerminalBlockEntity.DATA_MATCHED), menu.data(TerminalBlockEntity.DATA_TOTAL)), CRT_W - 8);
            bottom -= 3 + last.size() * step;
            for (int i = 0; i < last.size(); i++) {
                graphics.drawString(font, last.get(i), x, bottom + i * step, percent >= 100 ? GREEN : AMBER, false);
            }
        } else {
            bottom -= 3 + step;
            graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.last.none"), x, bottom, AMBER_DIM, false);
        }
        // A workpiece waiting in the return slot shows how many steps it has recorded; after a check the count of that run stays.
        ItemStack returned = menu.getSlot(TerminalSlots.RETURN).getItem();
        ResearchRun run = returned.get(BFComponents.RESEARCH_RUN.get());
        int recorded = -1;
        int steps = menu.data(TerminalBlockEntity.DATA_STEPS);
        if (run != null && SpikeLine.isWorkpiece(returned)) {
            recorded = SpikeLine.operationsOf(returned).size();
            steps = run.steps() > 0 ? run.steps() : steps;
        } else if (percent >= 0) {
            recorded = menu.data(TerminalBlockEntity.DATA_LAST_RECORDED);
            steps = menu.data(TerminalBlockEntity.DATA_TOTAL);
        }
        if (recorded >= 0) {
            bottom -= step;
            graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.recorded", recorded, steps), x, bottom,
                    recorded == steps ? GREEN : AMBER, false);
        }
    }

    /** "What the line lacks": the machines the chain needs and the scan around the terminal did not find. */
    private int drawLine(GuiGraphics graphics, int x, int y, int step) {
        int missing = menu.data(TerminalBlockEntity.DATA_MISSING);
        if (missing == 0) {
            graphics.drawString(font, Component.translatable("gui.blueprintforge.terminal.line.ok"), x, y, GREEN, false);
            return y + step;
        }
        List<Component> names = new ArrayList<>();
        if ((missing & LineScanner.PRESS) != 0) {
            names.add(Component.translatable("gui.blueprintforge.terminal.machine.press"));
        }
        if ((missing & LineScanner.BLAST) != 0) {
            names.add(Component.translatable("gui.blueprintforge.terminal.machine.blast"));
        }
        Component joined = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            joined = i == 0 ? names.get(i) : Component.empty().append(joined).append(", ").append(names.get(i));
        }
        List<FormattedCharSequence> lines = font.split(Component.translatable("gui.blueprintforge.terminal.line.missing", joined), CRT_W - 8);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, RED, false);
            y += step;
        }
        return y;
    }
}
