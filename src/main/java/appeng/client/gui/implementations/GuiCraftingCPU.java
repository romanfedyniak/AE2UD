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


import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.config.ViewItems;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.KeyCounter;
import appeng.container.me.GridInventoryEntry;
import appeng.api.util.AEColor;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.ISortSource;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.core.AEConfig;
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

import java.io.IOException;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class GuiCraftingCPU extends AEBaseGui implements ISortSource {
    private static final int GUI_HEIGHT = 184;
    private static final int GUI_WIDTH = 238;

    private static final int MIN_ROWS = 6;
    private static final int ROW_HEIGHT = 23;
    private static final int FIXED_HEIGHT = 46;

    private static final int TEXT_COLOR = 0x404040;
    private static final int BACKGROUND_ALPHA = 0x5A000000;

    private static final int SECTION_LENGTH = 67;

    private static final int SCROLLBAR_TOP = 19;
    private static final int SCROLLBAR_LEFT = 218;
    private static final int CANCEL_LEFT_OFFSET = 163;
    private static final int CANCEL_TOP_OFFSET = 25;
    private static final int CANCEL_HEIGHT = 20;
    private static final int CANCEL_WIDTH = 50;

    private static final int TITLE_TOP_OFFSET = 7;
    private static final int TITLE_LEFT_OFFSET = 8;

    private static final int ITEMSTACK_LEFT_OFFSET = 9;
    private static final int ITEMSTACK_TOP_OFFSET = 22;

    private final ContainerCraftingCPU craftingCpu;

    private final KeyCounter storage = new KeyCounter();
    private final KeyCounter active = new KeyCounter();
    private final KeyCounter pending = new KeyCounter();

    private final List<AEKey> visual = new ArrayList<>();
    private GuiButton cancel;
    protected GuiImgButton terminalStyleBox;
    protected int rows = MIN_ROWS;
    private int tooltip = -1;

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
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (this.terminalStyleBox == btn) {
            final TerminalStyle current = (TerminalStyle) AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
            final TerminalStyle next = (TerminalStyle) Platform.rotateEnum(current, Mouse.isButtonDown(1),
                    Settings.TERMINAL_STYLE.getPossibleValues());
            AEConfig.instance().getConfigManager().putSetting(Settings.TERMINAL_STYLE, next);
            this.buttonList.clear();
            this.initGui();
            return;
        }

        if (this.cancel == btn) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("TileCrafting.Cancel", "Cancel"));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    @Override
    public void initGui() {
        final TerminalStyle style = (TerminalStyle) AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
        final int availableRows = (this.height - 64 - FIXED_HEIGHT) / ROW_HEIGHT;
        this.rows = Math.max(MIN_ROWS, style.getRows(availableRows));
        this.ySize = FIXED_HEIGHT + this.rows * ROW_HEIGHT;
        super.initGui();
        this.setScrollBar();
        this.cancel = new GuiButton(0, this.guiLeft + CANCEL_LEFT_OFFSET, this.guiTop + this.ySize - CANCEL_TOP_OFFSET, CANCEL_WIDTH, CANCEL_HEIGHT, GuiText.Cancel
                .getLocal());
        this.buttonList.add(this.cancel);
        this.terminalStyleBox = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8,
                Settings.TERMINAL_STYLE, style);
        this.buttonList.add(this.terminalStyleBox);
    }

    private void setScrollBar() {
        final int size = this.visual.size();

        this.getScrollBar().setTop(SCROLLBAR_TOP).setLeft(SCROLLBAR_LEFT).setHeight(this.rows * ROW_HEIGHT - 1);
        this.getScrollBar().setRange(0, (size + 2) / 3 - this.rows, 1);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.cancel.enabled = !this.visual.isEmpty();

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

        // Elapsed, not remaining: how long this job has been running is a fact, while the estimate was a
        // moving prediction derived from throughput so far.
        if (this.craftingCpu.elapsed > 0 && !this.visual.isEmpty()) {
            final long elapsedMilliseconds = TimeUnit.MILLISECONDS.convert(this.craftingCpu.elapsed, TimeUnit.NANOSECONDS);
            final String elapsedText = DurationFormatUtils.formatDuration(elapsedMilliseconds, GuiText.ETAFormat.getLocal());
            title += " - " + elapsedText;
        }

        this.fontRenderer.drawString(title, TITLE_LEFT_OFFSET, TITLE_TOP_OFFSET, TEXT_COLOR);

        int x = 0;
        int y = 0;
        final int viewStart = this.getScrollBar().getCurrentScroll() * 3;
        final int viewEnd = viewStart + 3 * this.rows;

        String dspToolTip = "";
        final List<String> lineList = new ArrayList<>();
        int toolPosX = 0;
        int toolPosY = 0;

        final int offY = 23;

        for (int z = viewStart; z < Math.min(viewEnd, this.visual.size()); z++) {
            final AEKey refKey = this.visual.get(z);// repo.getReferenceItem( z );
            if (refKey != null) {
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5, 0.5, 0.5);

                final long stored = this.storage.get(refKey);
                final long activeAmount = this.active.get(refKey);
                final long pendingAmount = this.pending.get(refKey);

                int lines = 0;

                if (stored > 0) {
                    lines++;
                }
                boolean active = false;
                if (activeAmount > 0) {
                    lines++;
                    active = true;
                }
                boolean scheduled = false;
                if (pendingAmount > 0) {
                    lines++;
                    scheduled = true;
                }

                if (AEConfig.instance().isUseColoredCraftingStatus() && (active || scheduled)) {
                    final int bgColor = (active ? AEColor.GREEN.blackVariant : AEColor.YELLOW.blackVariant) | BACKGROUND_ALPHA;
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

                if (activeAmount > 0) {
                    final String str = GuiText.Crafting.getLocal() + ": " + refKey.formatAmount(activeAmount, AmountFormat.PREVIEW_LARGE);
                    final int w = 4 + this.fontRenderer.getStringWidth(str);

                    this.fontRenderer.drawString(str, (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5)) * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2, TEXT_COLOR);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Crafting.getLocal() + ": " + refKey.formatAmount(activeAmount, AmountFormat.FULL));
                    }

                    downY += 5;
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
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/craftingcpu.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, 41);
        int y = 41;
        for (int row = 1; row < this.rows - 1; row++) {
            this.drawTexturedModalRect(offsetX, offsetY + y, 0, 41, this.xSize, ROW_HEIGHT);
            y += ROW_HEIGHT;
        }
        this.drawTexturedModalRect(offsetX, offsetY + y, 0, GUI_HEIGHT - 51, this.xSize, 51);
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        if (this.terminalStyleBox == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new Rectangle(this.terminalStyleBox.x - 1,
                this.terminalStyleBox.y - 1, this.terminalStyleBox.width + 2,
                this.terminalStyleBox.height + 2));
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

    public List<AEKey> getVisual() {
        return visual;
    }

    public int getDisplayedRows() {
        return this.rows;
    }
}
