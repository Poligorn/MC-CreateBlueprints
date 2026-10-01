package com.blueprintforge.client;

import com.blueprintforge.machine.BlueprintArchiveMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** One paper slot. The Archive stores a document and does not research or print. */
public class BlueprintArchiveScreen extends AbstractContainerScreen<BlueprintArchiveMenu> {
    private static final int METAL = 0xFF3E4046;
    private static final int BRASS = 0xFFC99E48;
    private static final int BRASS_DARK = 0xFF8C682C;
    private static final int PAPER = 0xFFE7DEC4;
    private static final int SLOT = 0xFF16161A;
    private static final int TEXT = 0xFFE7DEC4;

    public BlueprintArchiveScreen(BlueprintArchiveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
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
        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF28292E);
            graphics.fill(sx, sy, sx + 16, sy + 16, SLOT);
        }
        int dx = x + BlueprintArchiveMenu.SLOT_X;
        int dy = y + BlueprintArchiveMenu.SLOT_Y;
        graphics.fill(dx - 3, dy - 3, dx + 19, dy + 19, BRASS);
        graphics.fill(dx - 2, dy - 2, dx + 18, dy + 18, PAPER);
        graphics.fill(dx, dy, dx + 16, dy + 16, SLOT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        graphics.drawString(font, Component.translatable("gui.blueprintforge.archive.storage"), 8, 20, TEXT, false);
    }
}
