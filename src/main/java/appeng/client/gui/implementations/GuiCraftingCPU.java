/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;


import appeng.api.AEApi;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.config.TerminalStyle;
import appeng.api.config.ViewItems;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.KeyCounter;
import appeng.container.me.GridInventoryEntry;
import appeng.api.util.AEColor;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.IKeyUnderMouse;
import appeng.client.gui.KeySearchTarget;
import appeng.client.gui.widgets.GuiCraftPriorityButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiSettingsDrawer;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.search.RepoSearch;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.core.AEClientConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.Platform;
import com.google.common.base.Joiner;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class GuiCraftingCPU extends AEBaseGui implements ISortSource, IKeyUnderMouse {
    private static final int GUI_HEIGHT = 184;
    private static final int GUI_WIDTH = 238;
    private static final int TEXTURE_TOP_HEIGHT = 41;

    private static final int MIN_ROWS = 6;
    private static final int ROW_HEIGHT = 23;

    /** The last list row, and then the window's foot, both taken from the bottom of the texture. */
    private static final int LIST_TAIL_Y = 133;
    private static final int LIST_TAIL_HEIGHT = 24;
    /** Down to just past the step where the window's right edge comes in. */
    private static final int FOOTER_TEXTURE_Y = 157;
    private static final int FOOTER_HEAD = 10;
    /** One plain row of the narrower lower part, repeated to whatever height the foot needs. */
    private static final int FOOTER_BODY_ROW = 170;
    private static final int FOOTER_TAIL = 8;
    private static final int FOOTER_HEIGHT = 46;
    /** Six pixels clear of the buttons, which sit twenty-five up from the bottom. */
    private static final int FOOTER_TEXT_TOP = 39;

    private static final int FIXED_HEIGHT = TEXTURE_TOP_HEIGHT + LIST_TAIL_HEIGHT + FOOTER_HEIGHT
            - 2 * ROW_HEIGHT;

    private static final int TEXT_COLOR = 0x404040;
    private static final int BACKGROUND_ALPHA = 0x5A000000;

    private static final int SECTION_LENGTH = 67;

    private static final int SCROLLBAR_TOP = 19;
    private static final int SCROLLBAR_LEFT = 218;
    private static final int CANCEL_LEFT_OFFSET = 163;
    private static final int CANCEL_TOP_OFFSET = 25;
    private static final int CANCEL_HEIGHT = 20;
    private static final int CANCEL_WIDTH = 50;
    private static final int SUSPEND_LEFT_OFFSET = CANCEL_LEFT_OFFSET - CANCEL_WIDTH - 2;
    private static final int SUSPEND_TOP_OFFSET = 25;
    private static final int SUSPEND_HEIGHT = 20;
    private static final int SUSPEND_WIDTH = 50;
    private static final int PRIORITY_LEFT_OFFSET = SUSPEND_LEFT_OFFSET - 16 - 4;

    private static final int TITLE_TOP_OFFSET = 7;
    private static final int TITLE_LEFT_OFFSET = 8;

    /** The right end of the header strip; the title has what is left of it. */
    private static final int SEARCH_LEFT = 92;
    private static final int SEARCH_TOP = 4;
    private static final int SEARCH_WIDTH = 118;
    private static final int SEARCH_HEIGHT = 12;

    private static final int ITEMSTACK_LEFT_OFFSET = 9;
    private static final int ITEMSTACK_TOP_OFFSET = 22;

    private final ContainerCraftingCPU craftingCpu;

    private final KeyCounter storage = new KeyCounter();
    private final KeyCounter active = new KeyCounter();
    private final KeyCounter pending = new KeyCounter();
    /** The part of {@link #active} that nothing is making - see {@link CraftingCPUCluster#getPromised}. */
    private final KeyCounter waiting = new KeyCounter();

    private final List<AEKey> visual = new ArrayList<>();
    /**
     * What the screen actually shows: {@link #visual} without the rows the filter hides. Kept apart so a
     * filtered-out row still tracks its amounts and comes back when the filter is turned off.
     */
    private final List<AEKey> displayed = new ArrayList<>();
    private GuiButton cancel;
    private GuiButton suspend;
    private GuiCraftPriorityButton priority;
    protected final GuiSettingsDrawer settings = new GuiSettingsDrawer();
    protected GuiImgButton terminalStyleBox;
    protected GuiImgButton toggleHideStored;
    private GuiImgButton selectionMode;
    protected int rows = MIN_ROWS;
    private int tooltip = -1;

    private MEGuiTextField searchField;
    protected GuiImgButton searchKeepBtn;
    private final RepoSearch search = new RepoSearch();

    /** Where the search survives closing the screen, while the keep setting says it should. */
    private static String memoryText = "";

    public GuiCraftingCPU(final InventoryPlayer inventoryPlayer, final Object te) {
        this(new ContainerCraftingCPU(inventoryPlayer, te));
    }

    protected GuiCraftingCPU(final ContainerCraftingCPU container) {
        super(container);
        this.craftingCpu = container;
        this.craftingCpu.setGui(this);
        this.ySize = GUI_HEIGHT;
        this.xSize = GUI_WIDTH;

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
    }

    public void clearItems() {
        this.storage.clear();
        this.active.clear();
        this.pending.clear();
        this.visual.clear();
        this.displayed.clear();
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (this.priority == btn) {
            // Over this screen rather than in its place: a container switch would lose the list of what
            // the processor is holding, which is the whole of what this screen is.
            this.mc.displayGuiScreen(new GuiCraftPriority(this, this.mc.player.inventory,
                    AEApi.instance().definitions().blocks().craftingUnit().maybeStack(1).orElse(ItemStack.EMPTY),
                    this.craftingCpu));
            return;
        }

        if (this.toggleSearchKeep(btn, this.searchKeepBtn)) {
            return;
        }

        if (this.toggleHideStored == btn) {
            final YesNo next = (YesNo) Platform.rotateEnum(
                    AEClientConfig.instance().getConfigManager().getSetting(Settings.HIDE_STORED),
                    Mouse.isButtonDown(1), Settings.HIDE_STORED.getPossibleValues());
            AEClientConfig.instance().getConfigManager().putSetting(Settings.HIDE_STORED, next);
            this.toggleHideStored.set(next);
            this.setScrollBar();
            return;
        }

        if (this.settings.actionPerformed(btn)) {
            return;
        }

        if (this.terminalStyleBox == btn) {
            final TerminalStyle current = (TerminalStyle) AEClientConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
            final TerminalStyle next = (TerminalStyle) Platform.rotateEnum(current, Mouse.isButtonDown(1),
                    Settings.TERMINAL_STYLE.getPossibleValues());
            AEClientConfig.instance().getConfigManager().putSetting(Settings.TERMINAL_STYLE, next);
            this.refreshLayout();
            return;
        }

        if (this.cancel == btn) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("TileCrafting.Cancel", "Cancel"));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }

        if (this.suspend == btn) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("TileCrafting.Suspend", "Suspend"));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }

        if (this.selectionMode == btn) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("TileCrafting.SelectionMode",
                        Mouse.isButtonDown(1) ? "Backwards" : "Forwards"));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    /**
     * @return whether this screen may configure the CPU it is showing. Mirrors
     * {@link ContainerCraftingCPU#allowsConfiguration()}, which is what actually holds on the server.
     */
    protected boolean canEditSelectionMode() {
        return true;
    }

    /** Where this screen's column of buttons stands, and which way what opens off it should go. */
    protected int columnLeft() {
        return this.guiLeft - 18;
    }

    protected boolean columnOpensLeft() {
        return true;
    }

    @Override
    public void initGui() {
        final TerminalStyle style = (TerminalStyle) AEClientConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
        final int availableRows = (this.height - 64 - FIXED_HEIGHT) / ROW_HEIGHT;
        this.rows = Math.max(MIN_ROWS, style.getRows(availableRows));
        this.ySize = FIXED_HEIGHT + this.rows * ROW_HEIGHT;
        super.initGui();
        this.setScrollBar();
        this.cancel = new GuiButton(0, this.guiLeft + CANCEL_LEFT_OFFSET, this.guiTop + this.ySize - CANCEL_TOP_OFFSET, CANCEL_WIDTH, CANCEL_HEIGHT, GuiText.Cancel
                .getLocal());
        this.buttonList.add(this.cancel);
        this.suspend = new GuiButton(0, this.guiLeft + SUSPEND_LEFT_OFFSET, this.guiTop + this.ySize - SUSPEND_TOP_OFFSET, SUSPEND_WIDTH, SUSPEND_HEIGHT, GuiText.Suspend
                .getLocal());
        this.buttonList.add(this.suspend);
        this.priority = new GuiCraftPriorityButton(this.guiLeft + PRIORITY_LEFT_OFFSET,
                this.guiTop + this.ySize - SUSPEND_TOP_OFFSET + 2);
        this.buttonList.add(this.priority);
        // The column, on whichever side of the window this screen puts it.
        final int column = this.columnLeft();
        int offset = this.settings.attach(this.buttonList, column, this.guiTop + 8, this.columnOpensLeft());

        this.toggleHideStored = new GuiImgButton(column, offset, Settings.HIDE_STORED,
                AEClientConfig.instance().getConfigManager().getSetting(Settings.HIDE_STORED));
        this.buttonList.add(this.toggleHideStored);
        offset += 20;

        if (this.canEditSelectionMode()) {
            this.selectionMode = new GuiImgButton(column, offset, Settings.CPU_SELECTION_MODE,
                    this.craftingCpu.selectionMode);
            this.buttonList.add(this.selectionMode);
        }

        this.buttonList.add(this.settings.take(
                this.terminalStyleBox = new GuiImgButton(0, 0, Settings.TERMINAL_STYLE, style)));
        this.buttonList.add(this.settings.take(this.searchKeepBtn = new GuiImgButton(0, 0,
                Settings.SEARCH_KEEP, AEClientConfig.instance().getConfigManager().getSetting(Settings.SEARCH_KEEP))));

        final MEGuiTextField previous = this.searchField;
        this.searchField = new MEGuiTextField(this.fontRenderer, this.guiLeft + SEARCH_LEFT,
                this.guiTop + SEARCH_TOP, SEARCH_WIDTH, SEARCH_HEIGHT);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setMaxStringLength(100);
        this.searchField.setTextColor(MEGuiTextField.TEXT_COLOR);
        this.searchField.setVisible(true);

        if (previous != null) {
            carryOver(previous, this.searchField);
        } else if (AEClientConfig.instance().keepsSearch() && !memoryText.isEmpty()) {
            this.searchField.setText(memoryText, true);
        }

        this.setScrollBar();
    }

    /**
     * Elapsed, not remaining: how long this job has been running is a fact, while the estimate was a moving
     * prediction derived from throughput so far.
     * <p>
     * On its own line above the buttons, centred across the window - the same place the Crafting Plan puts
     * the bytes a plan will take.
     */
    private void drawElapsed() {
        if (this.craftingCpu.elapsed <= 0 || this.visual.isEmpty()) {
            return;
        }

        final long elapsedMilliseconds = TimeUnit.MILLISECONDS.convert(this.craftingCpu.elapsed, TimeUnit.NANOSECONDS);
        final String text = DurationFormatUtils.formatDuration(elapsedMilliseconds, GuiText.ETAFormat.getLocal());

        this.fontRenderer.drawString(text, (this.xSize - this.fontRenderer.getStringWidth(text)) / 2,
                this.ySize - FOOTER_TEXT_TOP, TEXT_COLOR);
    }

    /**
     * A row with nothing active and nothing pending is work already done - the filter is for watching what
     * is left rather than what has been gathered.
     */
    private void rebuildDisplayed() {
        final boolean hideStored = AEClientConfig.instance().getConfigManager()
                .getSetting(Settings.HIDE_STORED) == YesNo.YES;

        this.search.setSearchString(this.searchField == null ? "" : this.searchField.getText());
        this.search.refresh();

        this.displayed.clear();
        for (final AEKey what : this.visual) {
            if ((!hideStored || this.active.get(what) > 0 || this.pending.get(what) > 0)
                    && this.search.matches(what)) {
                this.displayed.add(what);
            }
        }
    }

    private void setScrollBar() {
        this.rebuildDisplayed();

        final int size = this.displayed.size();

        this.getScrollBar().setTop(SCROLLBAR_TOP).setLeft(SCROLLBAR_LEFT).setHeight(this.rows * ROW_HEIGHT - 1);
        this.getScrollBar().setRange(0, (size + 2) / 3 - this.rows, 1);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.cancel.enabled = !this.visual.isEmpty();
        this.toggleHideStored.set(AEClientConfig.instance().getConfigManager().getSetting(Settings.HIDE_STORED));
        this.suspend.enabled = this.cancel.enabled;
        this.suspend.displayString = this.craftingCpu.suspended ? GuiText.Resume.getLocal() : GuiText.Suspend.getLocal();
        // Nothing to order while nothing is being crafted, the same condition the two buttons beside it use.
        this.priority.enabled = this.cancel.enabled;
        this.priority.setPriority(this.craftingCpu.craftPriority);
        if (this.selectionMode != null) {
            this.selectionMode.set(this.craftingCpu.selectionMode);
        }

        final int gx = (this.width - this.xSize) / 2;
        final int gy = (this.height - this.ySize) / 2;

        this.tooltip = -1;

        final int offY = 23;
        int y = 0;
        int x = 0;
        for (int z = 0; z < 3 * this.rows; z++) {
            final int minX = gx + 9 + x * 67;
            final int minY = gy + 22 + y * offY;

            if (minX < mouseX && minX + 67 > mouseX) {
                if (minY < mouseY && minY + offY - 2 > mouseY) {
                    this.tooltip = z;
                    break;
                }
            }

            x++;

            if (x > 2) {
                y++;
                x = 0;
            }
        }

        super.drawScreen(mouseX, mouseY, btn);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        String title = this.getGuiDisplayName(GuiText.CraftingStatus.getLocal());
        final int titleRoom = SEARCH_LEFT - TITLE_LEFT_OFFSET - 4;
        while (title.length() > 2 && this.fontRenderer.getStringWidth(title) > titleRoom) {
            title = title.substring(0, title.length() - 1);
        }

        this.fontRenderer.drawString(title, TITLE_LEFT_OFFSET, TITLE_TOP_OFFSET, TEXT_COLOR);
        this.drawElapsed();

        int x = 0;
        int y = 0;
        final int viewStart = this.getScrollBar().getCurrentScroll() * 3;
        final int viewEnd = viewStart + 3 * this.rows;

        String dspToolTip = "";
        final List<String> lineList = new ArrayList<>();
        int toolPosX = 0;
        int toolPosY = 0;

        final int offY = 23;

        for (int z = viewStart; z < Math.min(viewEnd, this.displayed.size()); z++) {
            final AEKey refKey = this.displayed.get(z);// repo.getReferenceItem( z );
            if (refKey != null) {
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5, 0.5, 0.5);

                final long stored = this.storage.get(refKey);
                final long activeAmount = this.active.get(refKey);
                final long pendingAmount = this.pending.get(refKey);

                // What the cpu expects to arrive splits in two: a machine is making some of it, and the
                // rest was only promised - by a level emitter, or by a job started short of it. Calling
                // both "Crafting" said something untrue about the second.
                final long waitingAmount = Math.min(activeAmount, this.waiting.get(refKey));
                final long craftingAmount = activeAmount - waitingAmount;

                int lines = 0;

                if (stored > 0) {
                    lines++;
                }
                boolean active = false;
                if (craftingAmount > 0) {
                    lines++;
                    active = true;
                }
                if (waitingAmount > 0) {
                    lines++;
                }
                boolean scheduled = false;
                if (pendingAmount > 0) {
                    lines++;
                    scheduled = true;
                }

                final boolean stalled = waitingAmount > 0;

                if (AEClientConfig.instance().isUseColoredCraftingStatus() && (stalled || active || scheduled)) {
                    // Orange outranks the other two: green and yellow both mean the network is getting on
                    // with it, and orange means it cannot until someone brings this. A row where a machine
                    // is making part of the amount and the rest is waited for is still a row to look at.
                    final int bgColor = (stalled ? AEColor.ORANGE.blackVariant
                            : active ? AEColor.GREEN.blackVariant
                            : AEColor.YELLOW.blackVariant) | BACKGROUND_ALPHA;
                    final int startX = (x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET) * 2;
                    final int startY = ((y * offY + ITEMSTACK_TOP_OFFSET) - 3) * 2;
                    drawRect(startX, startY, startX + (SECTION_LENGTH * 2), startY + (offY * 2) - 2, bgColor);
                }

                final int negY = ((lines - 1) * 5) / 2;
                int downY = 0;

                if (stored > 0) {
                    final String str = GuiText.Stored.getLocal() + ": " + refKey.formatAmount(stored, AmountFormat.PREVIEW_LARGE);
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str, (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5)) * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2, TEXT_COLOR);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Stored.getLocal() + ": " + refKey.formatAmount(stored, AmountFormat.FULL));
                    }

                    downY += 5;
                }

                if (craftingAmount > 0) {
                    downY = this.drawAmountLine(GuiText.Crafting, refKey, craftingAmount, x, y, offY, negY,
                            downY, lineList, z - viewStart);
                }

                if (waitingAmount > 0) {
                    downY = this.drawAmountLine(GuiText.Waiting, refKey, waitingAmount, x, y, offY, negY,
                            downY, lineList, z - viewStart);
                }

                if (pendingAmount > 0) {
                    final String str = GuiText.Scheduled.getLocal() + ": " + refKey.formatAmount(pendingAmount, AmountFormat.PREVIEW_LARGE);
                    final int w = 4 + this.fontRenderer.getStringWidth(str);

                    this.fontRenderer.drawString(str, (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5)) * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2, TEXT_COLOR);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Scheduled.getLocal() + ": " + refKey.formatAmount(pendingAmount, AmountFormat.FULL));
                    }
                }

                GlStateManager.popMatrix();
                final int posX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19;
                final int posY = y * offY + ITEMSTACK_TOP_OFFSET;

                final ItemStack is = refKey.wrapForDisplayOrFilter();

                if (this.tooltip == z - viewStart) {
                    dspToolTip = Platform.getItemDisplayName(refKey);

                    if (lineList.size() > 0) {
                        dspToolTip = dspToolTip + '\n' + Joiner.on("\n").join(lineList);
                    }

                    toolPosX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 8;
                    toolPosY = y * offY + ITEMSTACK_TOP_OFFSET;
                }

                this.drawItem(posX, posY, is);

                x++;

                if (x > 2) {
                    y++;
                    x = 0;
                }
            }
        }

        if (this.tooltip >= 0 && !dspToolTip.isEmpty()) {
            this.drawTooltip(toolPosX, toolPosY + 10, dspToolTip);
        }

        if (this.searchField != null && this.searchField.isMouseIn(mouseX, mouseY)) {
            this.drawTooltip(mouseX - offsetX, mouseY - offsetY, RepoSearch.syntaxTooltip());
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/craftingcpu.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, TEXTURE_TOP_HEIGHT);
        int y = TEXTURE_TOP_HEIGHT;
        for (int row = 1; row < this.rows - 1; row++) {
            this.drawTexturedModalRect(offsetX, offsetY + y, 0, TEXTURE_TOP_HEIGHT, this.xSize, ROW_HEIGHT);
            y += ROW_HEIGHT;
        }
        this.drawTexturedModalRect(offsetX, offsetY + y, 0, LIST_TAIL_Y, this.xSize, LIST_TAIL_HEIGHT);
        this.drawFooter(offsetX, offsetY + y + LIST_TAIL_HEIGHT);

        if (this.searchField != null) {
            drawWell(offsetX + SEARCH_LEFT, offsetY + SEARCH_TOP, SEARCH_WIDTH, SEARCH_HEIGHT);
            this.searchField.setMatched(!this.displayed.isEmpty() || this.searchField.getText().isEmpty());
            this.searchField.drawTextBox();
        }
    }

    /**
     * The window's foot, at whatever height it has been given: the step in its right edge, then one plain
     * row repeated, then the bottom border. Taken in three pieces rather than one so the foot can be made
     * taller than the texture draws it without either the step or the border being skipped.
     */
    private void drawFooter(final int x, final int y) {
        this.drawTexturedModalRect(x, y, 0, FOOTER_TEXTURE_Y, this.xSize, FOOTER_HEAD);

        for (int row = FOOTER_HEAD; row < FOOTER_HEIGHT - FOOTER_TAIL; row++) {
            this.drawTexturedModalRect(x, y + row, 0, FOOTER_BODY_ROW, this.xSize, 1);
        }

        this.drawTexturedModalRect(x, y + FOOTER_HEIGHT - FOOTER_TAIL, 0, GUI_HEIGHT - FOOTER_TAIL,
                this.xSize, FOOTER_TAIL);
    }

    /**
     * Every button in the side column, not just the first: they stand outside the window, so anything left out
     * here has HEI's item list drawn straight over it.
     */
    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> areas = new ArrayList<>(4);
        addButtonArea(areas, this.terminalStyleBox);
        addButtonArea(areas, this.toggleHideStored);
        addButtonArea(areas, this.selectionMode);
        addButtonArea(areas, this.searchKeepBtn);
        this.settings.addExclusionAreas(areas);
        return areas;
    }

    @Override
    public List<KeySearchTarget> getKeySearchTargets() {
        if (this.searchField == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new KeySearchTarget(this.searchField.getArea(), what -> {
            this.searchField.setText(RepoSearch.termFor(what));
            this.setScrollBar();
        }));
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        if (this.searchField != null) {
            this.searchField.mouseClicked(xCoord, yCoord, btn);

            if (btn == 1 && this.searchField.isMouseIn(xCoord, yCoord)) {
                this.searchField.setText("", true);
                this.setScrollBar();
            }
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (this.searchField != null && this.searchField.isFocused()
                && this.searchField.textboxKeyTyped(character, key)) {
            this.setScrollBar();
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
        memoryText = this.searchField != null && AEClientConfig.instance().keepsSearch()
                ? this.searchField.getText() : "";
        super.onGuiClosed();
    }

    public void postUpdate(final List<GridInventoryEntry> list, final byte ref) {
        switch (ref) {
            case 0:
                for (final GridInventoryEntry l : list) {
                    this.handleInput(this.storage, l);
                }
                break;

            case 1:
                for (final GridInventoryEntry l : list) {
                    this.handleInput(this.active, l);
                    this.waiting.set(l.getWhat(), l.getRequestableAmount());
                }
                break;

            case 2:
                for (final GridInventoryEntry l : list) {
                    this.handleInput(this.pending, l);
                }
                break;
        }

        for (final GridInventoryEntry l : list) {
            final long amt = this.getTotal(l.getWhat());

            if (amt <= 0) {
                this.visual.remove(l.getWhat());
            } else if (!this.visual.contains(l.getWhat())) {
                this.visual.add(l.getWhat());
            }
        }

        this.setScrollBar();
    }

    /** One "<label>: <amount>" line of a row, centred the way all three of them are. */
    private int drawAmountLine(final GuiText label, final AEKey what, final long amount, final int x,
            final int y, final int offY, final int negY, final int downY, final List<String> lineList,
            final int row) {
        final String str = label.getLocal() + ": " + what.formatAmount(amount, AmountFormat.PREVIEW_LARGE);
        final int w = 4 + this.fontRenderer.getStringWidth(str);

        this.fontRenderer.drawString(str,
                (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5)) * 2),
                (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2, TEXT_COLOR);

        if (this.tooltip == row) {
            lineList.add(label.getLocal() + ": " + what.formatAmount(amount, AmountFormat.FULL));
        }

        return downY + 5;
    }

    private void handleInput(final KeyCounter s, final GridInventoryEntry l) {
        s.set(l.getWhat(), l.getStoredAmount());
    }

    private long getTotal(final AEKey what) {
        return this.storage.get(what) + this.active.get(what) + this.pending.get(what);
    }

    @Override
    public Enum getSortBy() {
        return SortOrder.NAME;
    }

    @Override
    public Enum getSortDir() {
        return SortDir.ASCENDING;
    }

    @Override
    public Enum getSortDisplay() {
        return ViewItems.ALL;
    }

    @Nullable
    @Override
    public AEKey getKeyUnderMouse(final int mouseX, final int mouseY) {
        final int index = this.getListSlotUnderMouse(mouseX, mouseY, this.rows);
        return index >= 0 && index < this.displayed.size() ? this.displayed.get(index) : null;
    }

    public List<AEKey> getVisual() {
        return this.displayed;
    }

    public int getDisplayedRows() {
        return this.rows;
    }
}
