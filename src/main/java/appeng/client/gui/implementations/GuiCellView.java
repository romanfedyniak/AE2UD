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
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.IncludeExclude;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.storage.cells.StorageCell;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.KeySearchTarget;
import appeng.client.me.search.RepoSearch;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.api.config.Settings;
import appeng.core.AEClientConfig;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.GuiWirelessUpgradePlate;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.InternalSlotME;
import appeng.client.me.Repo;
import appeng.container.implementations.ContainerCellView;
import appeng.container.me.GridInventoryEntry;
import appeng.core.localization.GuiText;
import appeng.me.storage.BasicCellInventory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/**
 * What is inside one storage cell, without the cell having to go into a drive first. Read-only: a cell's
 * filter is changed at a Cell Workbench and its contents through a network.
 *
 * <p>Summoned over whichever screen the player was already in and puts that one back when it closes, the way
 * {@link GuiPatternView} is, and like it reaches no server: a cell carries its contents in its own NBT, and
 * the stack is the one under the cursor.</p>
 *
 * <p>The grid, the sorting, the scrolling and the search are the ME terminal's own {@link Repo} and its ME
 * slots, handed a list that came out of a cell rather than off a network. That is why this screen is short.</p>
 */
public class GuiCellView extends AEBaseGui implements ISortSource {

    private static final int MARGIN = 8;
    private static final int SLOT = 18;
    private static final int COLUMNS = 9;
    private static final int ROWS = 6;

    /**
     * The heading is the cell in a slot and nothing else. A name, its byte count and its type count are all
     * longer than the room there is beside a search field, and all three are already in the tooltip the slot
     * hands over when the cursor is on it.
     */
    private static final int HEADER_TOP = 7;
    private static final int GRID_TOP = 31;

    private static final int SEARCH_TOP = 9;
    private static final int SCROLL_WIDTH = 12;
    private static final int SEARCH_WIDTH = 90;
    private static final int SEARCH_HEIGHT = 12;

    /** How far the plate overlaps the window's own edge, so the two read as one piece. */
    private static final int PLATE_OVERLAP = 3;
    private static final int PLATE_EDGE = 7;
    private static final int PLATE_SLOT_INSET = 8;

    private final StorageCell cell;
    private final ItemStack stack;
    private final GuiScreen parent;
    private final Repo repo;
    private final List<ItemStack> cards;

    private MEGuiTextField searchField;
    private GuiTabButton modeButton;
    private GuiImgButton searchKeepBtn;

    /** Where the search survives closing the window, while the keep setting says it should. */
    private static String memoryText = "";
    private boolean showingFilter;

    public GuiCellView(final InventoryPlayer inventoryPlayer, final StorageCell cell, final ItemStack stack,
            final GuiScreen parent) {
        super(new ContainerCellView(inventoryPlayer, stack, MARGIN, HEADER_TOP));
        this.cell = cell;
        this.stack = stack;
        this.parent = parent;
        this.cards = readCards(stack);

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
        this.repo = new Repo(scrollbar, this);
        this.repo.setPower(true);
        this.repo.setRowSize(COLUMNS);
    }

    @Override
    public void initGui() {
        this.xSize = MARGIN * 2 + COLUMNS * SLOT + 2 + 12;
        this.ySize = GRID_TOP + ROWS * SLOT + MARGIN;

        this.getMeSlots().clear();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLUMNS; x++) {
                this.getMeSlots().add(new InternalSlotME(this.repo, x + y * COLUMNS,
                        MARGIN + x * SLOT, GRID_TOP + y * SLOT));
            }
        }

        super.initGui();

        final MEGuiTextField previous = this.searchField;
        this.searchField = new MEGuiTextField(this.fontRenderer,
                this.guiLeft + this.searchLeft(), this.guiTop + SEARCH_TOP, SEARCH_WIDTH, SEARCH_HEIGHT);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setMaxStringLength(100);
        this.searchField.setTextColor(0xFFFFFF);
        this.searchField.setVisible(true);

        this.getScrollBar().setLeft(this.scrollLeft()).setTop(GRID_TOP).setHeight(ROWS * SLOT - 2);

        this.addModeButton();

        // Beside the box rather than out on a strip: this window is drawn, not textured, and hanging a
        // button off its edge would have to be reported to HEI and drawn over the screen underneath.
        this.searchKeepBtn = new GuiImgButton(this.guiLeft + this.searchLeft() - 18,
                this.guiTop + SEARCH_TOP - 2, Settings.SEARCH_KEEP,
                AEClientConfig.instance().getConfigManager().getSetting(Settings.SEARCH_KEEP));
        this.buttonList.add(this.searchKeepBtn);

        if (previous != null) {
            carryOver(previous, this.searchField);
        } else if (AEClientConfig.instance().keepsSearch() && !memoryText.isEmpty()) {
            this.searchField.setText(memoryText);
            this.repo.setSearchString(memoryText);
        }

        this.fill();
    }

    /**
     * The switch between what the cell holds and what it is set to accept. Rebuilt rather than relabelled,
     * because a tab button's message is fixed when it is made.
     */
    private void addModeButton() {
        this.buttonList.remove(this.modeButton);
        this.modeButton = null;

        if (!this.isPartitioned()) {
            return;
        }

        this.modeButton = new GuiTabButton(this.guiLeft + this.xSize, this.guiTop + HEADER_TOP - 5,
                this.stack, this.modeMessage(), this.itemRender);
        this.modeButton.setHideEdge(13);
        this.buttonList.add(this.modeButton);
    }

    /** The rows the grid shows: what the cell holds, or what it is set to accept. */
    private void fill() {
        this.repo.reset();

        final KeyCounter stored = this.cell.getAvailableStacks();

        if (this.showingFilter) {
            final IItemHandler config = ((ICellWorkbenchItem) this.stack.getItem()).getConfigInventory(this.stack);

            for (int i = 0; i < config.getSlots(); i++) {
                final GenericStack filtered = GenericStack.resolveItemStack(config.getStackInSlot(i));
                if (filtered != null) {
                    this.repo.postUpdate(new GridInventoryEntry(filtered.what(),
                            stored.get(filtered.what()), 0, false));
                }
            }
        } else {
            for (final Object2LongMap.Entry<AEKey> entry : stored) {
                this.repo.postUpdate(new GridInventoryEntry(entry.getKey(), entry.getLongValue(), 0, false));
            }
        }

        this.repo.updateView();
        this.setScrollRange();
    }

    private int searchLeft() {
        return this.xSize - MARGIN - SEARCH_WIDTH;
    }

    private int scrollLeft() {
        return MARGIN + COLUMNS * SLOT + 2;
    }

    private void setScrollRange() {
        final int rows = (this.repo.size() + COLUMNS - 1) / COLUMNS;
        this.getScrollBar().setRange(0, Math.max(0, rows - ROWS), Math.max(1, ROWS / 2));
    }

    private boolean isPartitioned() {
        return this.cell instanceof BasicCellInventory && ((BasicCellInventory) this.cell).isPreformatted()
                && this.stack.getItem() instanceof ICellWorkbenchItem;
    }

    private String modeMessage() {
        final BasicCellInventory basic = (BasicCellInventory) this.cell;
        final String list = (basic.getPartitionListMode() == IncludeExclude.WHITELIST
                ? GuiText.Included : GuiText.Excluded).getLocal();
        final String match = (basic.isFuzzy() ? GuiText.Fuzzy : GuiText.Precise).getLocal();

        return (this.showingFilter ? GuiText.CellFilter : GuiText.CellContents).getLocal()
                + '\n' + TextFormatting.GRAY + list + ' ' + match;
    }

    private static List<ItemStack> readCards(final ItemStack stack) {
        final List<ItemStack> cards = new ArrayList<>();

        if (stack.getItem() instanceof ICellWorkbenchItem) {
            final IItemHandler upgrades = ((ICellWorkbenchItem) stack.getItem()).getUpgradesInventory(stack);

            for (int i = 0; upgrades != null && i < upgrades.getSlots(); i++) {
                final ItemStack card = upgrades.getStackInSlot(i);
                if (!card.isEmpty()) {
                    cards.add(card);
                }
            }
        }

        return cards;
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.repo.updateView();
        this.setScrollRange();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // The same field running the same search, so the same hint - drawn by hand for the same reason the
        // terminal's is, a text field that is also a tooltip source crashing outside the development
        // environment.
        if (this.searchField != null && this.searchField.isMouseIn(mouseX, mouseY)) {
            this.drawTooltip(mouseX, mouseY, RepoSearch.syntaxTooltip());
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        drawPanel(offsetX, offsetY, this.xSize, this.ySize);

        for (final Slot slot : this.inventorySlots.inventorySlots) {
            drawSlotWell(offsetX + slot.xPos, offsetY + slot.yPos);
        }

        // Every other screen has these wells painted into its background texture; this one paints itself.
        drawWell(offsetX + this.searchLeft(), offsetY + SEARCH_TOP, SEARCH_WIDTH, SEARCH_HEIGHT);
        // A pixel wider than the bar on every side: a text field's frame sits inside the box it
        // declares, a scrollbar's sits outside it - which is how both are drawn into every texture.
        drawWell(offsetX + this.scrollLeft() - 1, offsetY + GRID_TOP - 1, SCROLL_WIDTH + 2,
                ROWS * SLOT - 2 + 2);

        // The plate hangs off the right edge the way a wireless terminal wears its own, and is exactly as
        // tall as the cell has cards - a cell with none gets no plate and a clean edge.
        if (!this.cards.isEmpty()) {
            GuiWirelessUpgradePlate.draw(this, offsetX + this.xSize - PLATE_OVERLAP, offsetY + GRID_TOP,
                    this.cards.size());
        }

        this.searchField.setMatched(this.repo.hasMatches());
        this.searchField.drawTextBox();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        for (int i = 0; i < this.cards.size(); i++) {
            this.drawItem(this.xSize - PLATE_OVERLAP + PLATE_SLOT_INSET,
                    GRID_TOP + PLATE_EDGE + i * SLOT + 1, this.cards.get(i));
        }
    }

    @Override
    protected void actionPerformed(final GuiButton button) {
        if (this.toggleSearchKeep(button, this.searchKeepBtn)) {
            return;
        }

        if (button == this.modeButton) {
            this.showingFilter = !this.showingFilter;
            this.getScrollBar().setCurrentScroll(0);
            this.addModeButton();
            this.fill();
        }
    }

    @Override
    protected void mouseClicked(final int x, final int y, final int btn) throws IOException {
        this.searchField.mouseClicked(x, y, btn);

        if (btn == 1 && this.searchField.isMouseIn(x, y)) {
            this.searchField.setText("");
            this.repo.setSearchString("");
            this.getScrollBar().setCurrentScroll(0);
        }

        super.mouseClicked(x, y, btn);
    }

    /**
     * Nothing here is taken or put. The rows are the terminal's own slots, whose clicks would ask a server
     * about a container that was never opened on one.
     */
    @Override
    protected void handleMouseClick(final Slot slot, final int slotIdx, final int mouseButton,
            final ClickType clickType) {
    }

    /**
     * Escape and the inventory key would ordinarily tell the server to close a window, but this screen never
     * opened one - the player's real container is still the screen underneath. Put that back instead.
     */
    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (key == 1 || (!this.searchField.isFocused()
                && key == this.mc.gameSettings.keyBindInventory.getKeyCode())) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }

        if (this.searchField.textboxKeyTyped(character, key)) {
            this.repo.setSearchString(this.searchField.getText());
            this.getScrollBar().setCurrentScroll(0);
            this.repo.updateView();
            this.setScrollRange();
            return;
        }

        super.keyTyped(character, key);
    }

    @Override
    public boolean isTextFieldFocused() {
        return this.searchField != null && this.searchField.isFocused();
    }

    @Override
    public void onGuiClosed() {
        memoryText = AEClientConfig.instance().keepsSearch() ? this.searchField.getText() : "";
        super.onGuiClosed();
        this.inventorySlots.onContainerClosed(this.mc.player);
    }

    /**
     * The plate and the mode tab are drawn wholly outside the window, so HEI has to be told: its item list
     * would otherwise cover them and swallow the clicks meant for the tab.
     */
    @Override
    public List<KeySearchTarget> getKeySearchTargets() {
        if (this.searchField == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new KeySearchTarget(this.searchField.getArea(), what -> {
            this.searchField.setText(RepoSearch.termFor(what));
            this.repo.setSearchString(this.searchField.getText());
            this.getScrollBar().setCurrentScroll(0);
            this.repo.updateView();
            this.setScrollRange();
        }));
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> areas = new ArrayList<>();

        if (!this.cards.isEmpty()) {
            GuiWirelessUpgradePlate.addExclusionArea(areas, this.guiLeft + this.xSize - PLATE_OVERLAP,
                    this.guiTop + GRID_TOP, this.cards.size());
        }

        addButtonArea(areas, this.modeButton);

        return areas;
    }

    @Override
    public Enum getSortBy() {
        return SortOrder.AMOUNT;
    }

    @Override
    public Enum getSortDir() {
        return SortDir.DESCENDING;
    }

    @Override
    public Enum getSortDisplay() {
        return ViewItems.ALL;
    }
}
