/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.MEGuiTooltipTextField;
import appeng.container.implementations.ContainerPatternUpload;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternUpload;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.IPatternUploadHost;

/**
 * Where a pattern goes. One row per thing in the network that holds patterns, drawn from a list the server
 * pushes and refreshes while the screen is open.
 *
 * <p>Rows that would run this pattern are listed first and drawn plainly. The rest are still listed, dimmed,
 * and cannot be clicked - a pattern filed where nothing will run it is a fault that shows up hours later, at
 * the crafting terminal, as a recipe the network says it cannot make. They stay on the list rather than
 * vanishing from it so the screen can say why, which is what its tooltip is for.</p>
 *
 * @see ContainerPatternUpload
 */
public class GuiPatternUpload extends AEBaseGui {

    private static final int WIDTH = 211;
    private static final int HEADER_HEIGHT = 24;
    private static final int TAB_WIDTH = 22;
    private static final int ROW_HEIGHT = 19;
    private static final int PLATE_HEIGHT = 18;
    private static final int FOOTER_HEIGHT = 7;
    private static final int VISIBLE_ROWS = 8;

    private static final int SEARCH_LEFT = 100;
    private static final int SEARCH_TOP = 4;
    private static final int SEARCH_WIDTH = 81;
    private static final int SEARCH_HEIGHT = 12;

    private static final int SCROLL_LEFT = WIDTH - 20;
    private static final int ROW_LEFT = 8;
    private static final int ROW_WIDTH = SCROLL_LEFT - ROW_LEFT - 2;

    private static final int TEXT_COLOR = 0x404040;
    private static final int DIM_TEXT_COLOR = 0x808080;
    private static final int DIM_ROW_COLOR = 0x30000000;
    private static final int HOVER_ROW_COLOR = 0x30FFFFFF;

    private final List<Row> rows = new ArrayList<>();
    private final List<Row> shown = new ArrayList<>();
    private final GuiScrollbar scrollbar = new GuiScrollbar();

    private MEGuiTooltipTextField searchField;
    private GuiTabButton backButton;
    private GuiBridge back;

    public GuiPatternUpload(final InventoryPlayer inventoryPlayer, final IPatternUploadHost host) {
        super(new ContainerPatternUpload(inventoryPlayer, host));
        this.xSize = WIDTH;
        this.ySize = HEADER_HEIGHT + VISIBLE_ROWS * ROW_HEIGHT + FOOTER_HEIGHT;
        this.setScrollBar(this.scrollbar);
    }

    @Override
    public void initGui() {
        super.initGui();

        final ContainerPatternUpload container = (ContainerPatternUpload) this.inventorySlots;
        final ItemStack icon = container.getSubMenuHost().getItemStackRepresentation();
        this.back = container.getSubMenuHost().getGuiBridge();

        if (this.back != null && !icon.isEmpty()) {
            this.buttonList.add(this.backButton = new GuiTabButton(this.guiLeft + WIDTH - TAB_WIDTH, this.guiTop,
                    icon, icon.getDisplayName(), this.itemRender));
        }

        final String query = this.searchField == null ? "" : this.searchField.getText();
        this.searchField = new MEGuiTooltipTextField(SEARCH_WIDTH, SEARCH_HEIGHT,
                ButtonToolTips.PatternTargetSearch.getLocal()) {
            @Override
            public void onTextChange(final String old) {
                refilter();
            }
        };
        this.searchField.x = this.guiLeft + SEARCH_LEFT;
        this.searchField.y = this.guiTop + SEARCH_TOP;
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setMaxStringLength(25);
        this.searchField.setTextColor(0xFFFFFF);
        this.searchField.setText(query, true);
        this.searchField.setCursorPositionEnd();

        this.scrollbar.setLeft(SCROLL_LEFT).setTop(HEADER_HEIGHT).setWidth(12)
                .setHeight(VISIBLE_ROWS * ROW_HEIGHT - 2);
        this.refilter();
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.drawTooltip(this.searchField, mouseX, mouseY);

        final Row row = this.rowUnder(mouseX - this.guiLeft, mouseY - this.guiTop);
        if (row == null) {
            return;
        }

        final List<String> lines = new ArrayList<>();
        lines.add(row.name());
        lines.add(GuiText.PatternTargetFreeSlots.getLocal(row.free, row.slots));

        if (!row.fits) {
            lines.add(GuiText.PatternTargetUnsuitable.getLocal());
        } else if (row.free == 0) {
            lines.add(GuiText.PatternTargetFull.getLocal());
        }

        this.drawTooltip(mouseX, mouseY, lines);
    }

    /**
     * The server sends the whole list whenever anything on it changes, so the screen never merges - it
     * replaces what it has and filters again.
     */
    public void postUpdate(final NBTTagCompound data) {
        this.rows.clear();

        final int count = data.getInteger("rows");
        for (int i = 0; i < count; i++) {
            final NBTTagCompound tag = data.getCompoundTag(Integer.toString(i));
            this.rows.add(new Row(tag.getLong("id"), tag.getString("un"), tag.getInteger("free"),
                    tag.getInteger("slots"), tag.getBoolean("fits"),
                    tag.hasKey("icon") ? new ItemStack(tag.getCompoundTag("icon")) : ItemStack.EMPTY));
        }

        // Suitable first, then by name, so a list that grows while the screen is open does not reshuffle.
        this.rows.sort(Comparator.<Row, Boolean>comparing(row -> !row.fits).thenComparing(row -> row.name()));
        this.refilter();
    }

    private void refilter() {
        final String query = this.searchField == null ? "" : this.searchField.getText().toLowerCase().trim();

        this.shown.clear();
        for (final Row row : this.rows) {
            if (query.isEmpty() || row.name().toLowerCase().contains(query)) {
                this.shown.add(row);
            }
        }

        if (this.searchField != null) {
            this.searchField.setMatched(query.isEmpty() || !this.shown.isEmpty());
        }

        this.scrollbar.setRange(0, Math.max(0, this.shown.size() - VISIBLE_ROWS), 1);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.drawPanel(offsetX, offsetY, WIDTH, this.ySize);
        drawWell(offsetX + SEARCH_LEFT, offsetY + SEARCH_TOP, SEARCH_WIDTH, SEARCH_HEIGHT);
        drawWell(offsetX + SCROLL_LEFT - 1, offsetY + HEADER_HEIGHT - 1, 14, VISIBLE_ROWS * ROW_HEIGHT);
        this.searchField.drawTextBox();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(GuiText.SendPatternTo.getLocal(), 8, 6, TEXT_COLOR);

        final int first = this.scrollbar.getCurrentScroll();
        for (int i = 0; i < VISIBLE_ROWS && first + i < this.shown.size(); i++) {
            this.drawRow(this.shown.get(first + i), HEADER_HEIGHT + i * ROW_HEIGHT, mouseX, mouseY);
        }

    }

    private void drawRow(final Row row, final int top, final int mouseX, final int mouseY) {
        // Each row is a plate of its own: a raised edge along the top and left, a shadow along the bottom
        // and right. Without it eight names sit on one flat sheet and read as a single block of text.
        drawRect(ROW_LEFT, top, ROW_LEFT + ROW_WIDTH, top + 1, PANEL_LIGHT_COLOR);
        drawRect(ROW_LEFT, top, ROW_LEFT + 1, top + PLATE_HEIGHT, PANEL_LIGHT_COLOR);
        drawRect(ROW_LEFT, top + PLATE_HEIGHT - 1, ROW_LEFT + ROW_WIDTH, top + PLATE_HEIGHT, PANEL_SHADOW_COLOR);
        drawRect(ROW_LEFT + ROW_WIDTH - 1, top, ROW_LEFT + ROW_WIDTH, top + PLATE_HEIGHT, PANEL_SHADOW_COLOR);

        if (!row.usable()) {
            drawRect(ROW_LEFT + 1, top + 1, ROW_LEFT + ROW_WIDTH - 1, top + PLATE_HEIGHT - 1, DIM_ROW_COLOR);
        } else if (this.rowUnder(mouseX, mouseY) == row) {
            drawRect(ROW_LEFT + 1, top + 1, ROW_LEFT + ROW_WIDTH - 1, top + PLATE_HEIGHT - 1, HOVER_ROW_COLOR);
        }

        if (!row.icon.isEmpty()) {
            // The plate above was painted with drawRect, which leaves its colour set on whatever is textured
            // next - and the next thing is an item model, which would come out tinted grey.
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.drawItem(ROW_LEFT + 2, top + 1, row.icon);
        }

        // Nothing stands beside this one worth drawing, so the name takes the space rather than leaving a
        // hole where a picture would have been.
        final int nameLeft = row.icon.isEmpty() ? ROW_LEFT + 4 : ROW_LEFT + 22;

        final int color = row.usable() ? TEXT_COLOR : DIM_TEXT_COLOR;
        final String free = row.free + "/" + row.slots;
        final int freeWidth = this.fontRenderer.getStringWidth(free);

        this.fontRenderer.drawString(
                this.trim(row.name(), ROW_LEFT + ROW_WIDTH - nameLeft - freeWidth - 6), nameLeft, top + 5, color);
        this.fontRenderer.drawString(free, ROW_LEFT + ROW_WIDTH - freeWidth - 4, top + 5, color);
    }

    private String trim(final String name, final int width) {
        return this.fontRenderer.getStringWidth(name) <= width
                ? name
                : this.fontRenderer.trimStringToWidth(name, width - 6) + "...";
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        this.searchField.mouseClicked(xCoord, yCoord, btn);

        final Row row = this.rowUnder(xCoord - this.guiLeft, yCoord - this.guiTop);
        if (row != null && row.usable()) {
            NetworkHandler.instance().sendToServer(PacketPatternUpload.to(row.id));
            return;
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    private Row rowUnder(final int x, final int y) {
        if (x < ROW_LEFT || x >= ROW_LEFT + ROW_WIDTH || y < HEADER_HEIGHT) {
            return null;
        }

        final int index = this.scrollbar.getCurrentScroll() + (y - HEADER_HEIGHT) / ROW_HEIGHT;
        return index >= 0 && index < this.shown.size() && (y - HEADER_HEIGHT) / ROW_HEIGHT < VISIBLE_ROWS
                ? this.shown.get(index)
                : null;
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (this.searchField.isFocused() && this.searchField.textboxKeyTyped(character, key)) {
            this.refilter();
            return;
        }

        super.keyTyped(character, key);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.backButton) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.back));
        }
    }

    /** One line, as the client has it. */
    private static final class Row {

        private final long id;
        private final String unlocalizedName;
        private final int free;
        private final int slots;
        private final boolean fits;
        private final ItemStack icon;

        /** Whether this row is worth a click: it would run the pattern, and it has somewhere to put it. */
        private boolean usable() {
            return this.fits && this.free > 0;
        }

        private Row(final long id, final String unlocalizedName, final int free, final int slots,
                final boolean fits, final ItemStack icon) {
            this.id = id;
            this.unlocalizedName = unlocalizedName;
            this.free = free;
            this.slots = slots;
            this.fits = fits;
            this.icon = icon;
        }

        /**
         * A machine names itself with a translation key most of the time, and with a finished name when it
         * insists on formatting its own - the same two cases the Pattern Access Terminal handles.
         */
        private String name() {
            final String withSuffix = I18n.format(this.unlocalizedName + ".name");
            return withSuffix.equals(this.unlocalizedName + ".name") ? I18n.format(this.unlocalizedName)
                    : withSuffix;
        }
    }
}
