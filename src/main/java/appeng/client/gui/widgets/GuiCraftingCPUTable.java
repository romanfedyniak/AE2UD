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

package appeng.client.gui.widgets;


import appeng.api.AEApi;
import appeng.api.config.CpuActivityFilter;
import appeng.api.config.CpuModeFilter;
import appeng.api.config.CpuSelectionMode;
import appeng.api.config.CpuSortOrder;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.api.util.IConfigManager;
import appeng.client.gui.AEBaseGui;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.container.implementations.ICraftingCPUTableHost;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * The list of crafting CPUs drawn to the left of a screen, shared by the crafting status and by the screens
 * that pick a CPU for a job. The rows it shows come from the container's {@link
 * appeng.container.implementations.CraftingCPUTable}.
 */
public class GuiCraftingCPUTable extends Gui {

    public static final int WIDTH = 94;
    public static final int FIXED_HEIGHT = 26;

    private static final int SLOT_XOFF = 100;
    private static final int SLOT_YOFF = 0;
    private static final int SLOT_WIDTH = 67;
    private static final int SLOT_HEIGHT = 23;
    private static final int SLOT_LEFT = 9;
    private static final int SLOT_TOP = 19;
    private static final int TEXT_COLOR = 0x202020;
    private static final int PROGRESS_START_COLOR = 0xFFE60A00;
    private static final int PROGRESS_MIDDLE_COLOR = 0xFFE6E600;
    private static final int PROGRESS_END_COLOR = 0xFF0AE600;
    private static final int SUSPENDED_OVERLAY_COLOR = 0xA0404040;

    /**
     * The filter buttons sit in a row above the panel, in the band these screens already hang their tabs in.
     * Beside the panel they would not fit: at the narrowest the game runs, its left edge is the screen's.
     */
    private static final int BUTTON_ROW_HEIGHT = 20;
    private static final int BUTTON_LEFT = 3;
    private static final int BUTTON_SPACING = 20;
    private static final int SEARCH_LEFT = 3;
    private static final int SEARCH_TOP = 3;
    private static final int SEARCH_WIDTH = 88;
    private static final int SEARCH_HEIGHT = 12;

    private final AEBaseGui parent;
    private final ICraftingCPUTableHost host;
    private final GuiScrollbar scrollbar = new GuiScrollbar();

    /** What the filters and the ordering leave of the container's list, rebuilt every frame. */
    private final List<CraftingCPUStatus> displayed = new ArrayList<>();

    private MEGuiTooltipTextField searchField;
    private GuiImgButton activityFilter;
    private GuiImgButton modeFilter;
    private GuiImgButton sortBy;
    private GuiImgButton sortDirection;

    private int rows = 1;

    private static ItemStack ACCELERATOR_ICON = ItemStack.EMPTY;

    public GuiCraftingCPUTable(final AEBaseGui parent, final ICraftingCPUTableHost host) {
        this.parent = parent;
        this.host = host;
    }

    public void initGui(final int rows, final List<GuiButton> buttonList) {
        this.rows = rows;
        this.scrollbar.setLeft(-16);
        this.scrollbar.setTop(SLOT_TOP);
        this.scrollbar.setWidth(12);
        this.scrollbar.setHeight(rows * SLOT_HEIGHT - 1);

        final IConfigManager config = AEConfig.instance().getConfigManager();
        int left = this.parent.getGuiLeft() - WIDTH + BUTTON_LEFT;
        final int top = this.parent.getGuiTop() - BUTTON_ROW_HEIGHT;

        this.activityFilter = new GuiImgButton(left, top, Settings.CPU_FILTER_ACTIVITY,
                config.getSetting(Settings.CPU_FILTER_ACTIVITY));
        this.modeFilter = new GuiImgButton(left += BUTTON_SPACING, top, Settings.CPU_FILTER_MODE,
                config.getSetting(Settings.CPU_FILTER_MODE));
        this.sortBy = new GuiImgButton(left += BUTTON_SPACING, top, Settings.CPU_SORT_BY,
                config.getSetting(Settings.CPU_SORT_BY));
        this.sortDirection = new GuiImgButton(left + BUTTON_SPACING, top, Settings.CPU_SORT_DIRECTION,
                config.getSetting(Settings.CPU_SORT_DIRECTION));

        buttonList.add(this.activityFilter);
        buttonList.add(this.modeFilter);
        buttonList.add(this.sortBy);
        buttonList.add(this.sortDirection);

        // Kept across a rebuild of the screen, which happens whenever the terminal style changes.
        final String text = this.searchField == null ? "" : this.searchField.getText();
        this.searchField = new MEGuiTooltipTextField(SEARCH_WIDTH, SEARCH_HEIGHT,
                ButtonToolTips.CpuSearch.getLocal());
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setMaxStringLength(25);
        this.searchField.setTextColor(0xFFFFFF);
        this.searchField.x = this.parent.getGuiLeft() - WIDTH + SEARCH_LEFT;
        this.searchField.y = this.parent.getGuiTop() + SEARCH_TOP;
        this.searchField.setText(text);
        this.searchField.setCursorPositionEnd();
    }

    /**
     * @return true when the button belonged to this table.
     */
    public boolean actionPerformed(final GuiButton btn) {
        final Settings setting;
        if (btn == this.activityFilter) {
            setting = Settings.CPU_FILTER_ACTIVITY;
        } else if (btn == this.modeFilter) {
            setting = Settings.CPU_FILTER_MODE;
        } else if (btn == this.sortBy) {
            setting = Settings.CPU_SORT_BY;
        } else if (btn == this.sortDirection) {
            setting = Settings.CPU_SORT_DIRECTION;
        } else {
            return false;
        }

        final IConfigManager config = AEConfig.instance().getConfigManager();
        final Enum<?> next = Platform.rotateEnum(config.getSetting(setting), Mouse.isButtonDown(1),
                setting.getPossibleValues());
        config.putSetting(setting, next);
        ((GuiImgButton) btn).set(next);
        return true;
    }

    /**
     * @return true when the search field took the key, so the screen must not act on it.
     */
    public boolean keyTyped(final char character, final int key) {
        return this.searchField != null && this.searchField.isFocused()
                && this.searchField.textboxKeyTyped(character, key);
    }

    /**
     * Kept in step every frame rather than on update, since rows can change with the window.
     */
    public void updateScrollRange() {
        this.rebuildDisplayed();
        this.scrollbar.setRange(0, Math.max(0, this.getRowCount() - this.rows), 1);
    }

    /**
     * Filtering and ordering are the screen's own business: the container's list is what the network holds,
     * and the selected CPU stays in sight whatever the filters say, so that what a job would run on - or
     * what the item list below belongs to - is never hidden from the one looking at it.
     */
    private void rebuildDisplayed() {
        final IConfigManager config = AEConfig.instance().getConfigManager();
        final CpuActivityFilter activity = (CpuActivityFilter) config.getSetting(Settings.CPU_FILTER_ACTIVITY);
        final CpuModeFilter mode = (CpuModeFilter) config.getSetting(Settings.CPU_FILTER_MODE);
        final String search = this.searchField == null ? "" : this.searchField.getText().trim().toLowerCase();
        final int selected = this.host.getSelectedCpuSerial();

        this.displayed.clear();
        for (final CraftingCPUStatus cpu : this.host.getCPUTable().getCPUs()) {
            if (cpu.getSerial() == selected || matches(cpu, activity, mode, search)) {
                this.displayed.add(cpu);
            }
        }

        this.displayed.sort(comparator(config));
    }

    private static boolean matches(final CraftingCPUStatus cpu, final CpuActivityFilter activity,
            final CpuModeFilter mode, final String search) {
        final boolean busy = cpu.getCrafting() != null;
        if (activity == CpuActivityFilter.ACTIVE && !busy || activity == CpuActivityFilter.IDLE && busy) {
            return false;
        }

        if (!mode.matches(cpu.getSelectionMode())) {
            return false;
        }

        if (search.isEmpty()) {
            return true;
        }

        if (displayName(cpu).toLowerCase().contains(search)) {
            return true;
        }

        // What it is crafting counts as its name too - "who is making iron" is the question this list is
        // usually asked, and the row already says the answer.
        final GenericStack crafting = cpu.getCrafting();
        return crafting != null
                && Platform.getItemDisplayName(crafting.what()).toLowerCase().contains(search);
    }

    private static Comparator<CraftingCPUStatus> comparator(final IConfigManager config) {
        final CpuSortOrder order = (CpuSortOrder) config.getSetting(Settings.CPU_SORT_BY);
        Comparator<CraftingCPUStatus> comparator;
        switch (order) {
            case STORAGE:
                comparator = Comparator.comparingLong(CraftingCPUStatus::getStorage);
                break;
            case COPROCESSORS:
                comparator = Comparator.comparingLong(CraftingCPUStatus::getCoprocessors);
                break;
            default:
                // Unnamed CPUs last, as they are in the list the server sends.
                comparator = Comparator
                        .comparing((CraftingCPUStatus cpu) -> cpu.getName() == null || cpu.getName().isEmpty())
                        .thenComparing(cpu -> displayName(cpu).toLowerCase());
                break;
        }

        if (config.getSetting(Settings.CPU_SORT_DIRECTION) == SortDir.DESCENDING) {
            comparator = comparator.reversed();
        }

        return comparator.thenComparingInt(CraftingCPUStatus::getSerial);
    }

    /** What the row calls this CPU, which is also what the search matches against. */
    private static String displayName(final CraftingCPUStatus cpu) {
        final String name = cpu.getName();
        return name == null || name.isEmpty() ? GuiText.CPUs.getLocal() + " #" + cpu.getSerial() : name;
    }

    public void drawBG(final int offsetX, final int offsetY) {
        this.parent.bindTexture("guis/cpu_selector.png");
        final int tableLeft = offsetX - WIDTH;
        this.parent.drawTexturedModalRect(tableLeft, offsetY, 0, 0, WIDTH, 41);
        int y = 41;
        for (int row = 1; row < this.rows - 1; row++) {
            this.parent.drawTexturedModalRect(tableLeft, offsetY + y, 0, 41, WIDTH, SLOT_HEIGHT);
            y += SLOT_HEIGHT;
        }
        this.parent.drawTexturedModalRect(tableLeft, offsetY + y, 0, 133, WIDTH, 31);

        if (this.searchField != null) {
            this.searchField.drawTextBox();
            // The field fills itself with drawRect, which leaves that colour behind for the textured rows.
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public void drawFG(final int mouseX, final int mouseY) {
        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        final int firstRow = this.scrollbar.getCurrentScroll();
        final CraftingCPUStatus hovered = this.hitCpu(mouseX, mouseY);
        final boolean hoveredIsAutomatic = hovered == null && this.hitsAutomaticRow(mouseX, mouseY);
        final int selected = this.host.getSelectedCpuSerial();

        for (int i = firstRow; i < firstRow + this.rows; i++) {
            if (i < 0 || i >= this.getRowCount()) {
                continue;
            }

            final CraftingCPUStatus cpu = this.getCpuForRow(i);
            final boolean automaticRow = cpu == null;
            if (automaticRow && !this.hasAutomaticRow()) {
                continue;
            }

            final int x = -WIDTH + SLOT_LEFT;
            final int y = SLOT_TOP + (i - firstRow) * SLOT_HEIGHT;

            final boolean isSelected = automaticRow ? selected == -1 : cpu.getSerial() == selected;
            final boolean isHovered = automaticRow ? hoveredIsAutomatic
                    : hovered != null && hovered.getSerial() == cpu.getSerial();
            if (isSelected) {
                GlStateManager.color(0.0F, 0.8352F, 1.0F, 1.0F);
            } else if (isHovered) {
                GlStateManager.color(0.65F, 0.9F, 1.0F, 1.0F);
            } else {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
            AEBaseGui.enableSpriteBlending();
            this.parent.bindTexture("guis/cpu_selector.png");
            this.parent.drawTexturedModalRect(x, y, SLOT_XOFF, SLOT_YOFF, SLOT_WIDTH, SLOT_HEIGHT);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            if (automaticRow) {
                this.drawAutomaticRow(font, x, y);
                continue;
            }

            this.drawCpuRow(font, cpu, x, y);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (this.host.getCPUTable().getCPUs().isEmpty()) {
            this.drawEmptyMessage(font);
        }

        this.scrollbar.draw(this.parent);
    }

    private void drawAutomaticRow(final FontRenderer font, final int x, final int y) {
        final String label = GuiText.Automatic.getLocal();
        font.drawString(label, x + (SLOT_WIDTH - font.getStringWidth(label)) / 2, y + 8, TEXT_COLOR);
    }

    private void drawCpuRow(final FontRenderer font, final CraftingCPUStatus cpu, final int x, final int y) {
        String name = displayName(cpu);
        if (name.length() > 12) {
            name = name.substring(0, 11) + "..";
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x + 3, y + 3, 0);
        GL11.glScalef(0.8f, 0.8f, 1.0f);
        font.drawString(name, 0, 0, TEXT_COLOR);
        GL11.glPopMatrix();

        GL11.glPushMatrix();
        GL11.glTranslatef(x + 3, y + 11, 0);
        final GenericStack craftingStack = cpu.getCrafting();
        if (craftingStack != null) {
            this.drawIcon(16 * 11 + 2);
            GL11.glTranslatef(18.0f, 2.0f, 0.0f);
            String amount = craftingStack.what().formatAmount(craftingStack.amount(), AmountFormat.PREVIEW_LARGE);
            if (amount.length() > 5) {
                amount = amount.substring(0, 5) + "..";
            }
            GL11.glScalef(1.5f, 1.5f, 1.0f);
            font.drawString(amount, 0, 0, 0x009000);
            GL11.glPopMatrix();

            GL11.glPushMatrix();
            GL11.glTranslatef(x + SLOT_WIDTH - 19, y + 3, 0);
            this.parent.drawItem(0, 0, GenericStack.wrapInItemStack(craftingStack));
            GL11.glPopMatrix();
            GL11.glPushMatrix();

            final double craftingProgress = getCraftingProgress(cpu);
            final int progressWidth = (int) (SLOT_WIDTH * craftingProgress);
            if (progressWidth > 0) {
                // Edge to edge: the plate's bottom border is a flat dark row, with no corner to leave alone.
                drawRect(x, y + SLOT_HEIGHT - 2, x + progressWidth, y + SLOT_HEIGHT - 1,
                        calculateProgressColor(craftingProgress));
            }

            if (cpu.isSuspended()) {
                drawRect(x, y, x + SLOT_WIDTH, y + SLOT_HEIGHT, SUSPENDED_OVERLAY_COLOR);
            }
        } else {
            this.drawIcon(16 * 4 + 3);
            this.drawIdleNumbers(font, cpu);
        }
        GL11.glPopMatrix();

        this.drawSelectionModeBadge(cpu, x, y);
    }

    /**
     * The bytes an idle CPU holds and, beside them, how many accelerators it has. Both numbers are smaller
     * than the bytes alone used to be, because two of them have to share the width one had.
     * The accelerator is drawn after the text: {@link AEBaseGui#drawItem} leaves the item renderer's
     * lighting behind it, which the font would pick up.
     */
    private void drawIdleNumbers(final FontRenderer font, final CraftingCPUStatus cpu) {
        final boolean accelerated = cpu.getCoprocessors() > 0;

        GL11.glPushMatrix();
        GL11.glTranslatef(18.0f, accelerated ? 6.0f : 2.0f, 0.0f);
        GL11.glScalef(accelerated ? 1.1f : 1.5f, accelerated ? 1.1f : 1.5f, 1.0f);
        font.drawString(cpu.formatStorage(), 0, 0, TEXT_COLOR);
        if (accelerated) {
            font.drawString(cpu.formatCoprocessors(), 16 * 4 - 5, 0, TEXT_COLOR);
        }
        GL11.glPopMatrix();

        if (accelerated) {
            this.parent.drawItem(16 * 4, 0, acceleratorIcon());
        }
    }

    private static ItemStack acceleratorIcon() {
        if (ACCELERATOR_ICON.isEmpty()) {
            ACCELERATOR_ICON = AEApi.instance().definitions().blocks().craftingAccelerator()
                    .maybeStack(1).orElse(ItemStack.EMPTY);
        }
        return ACCELERATOR_ICON;
    }

    /**
     * A CPU kept for players or for automation is marked in the corner of its row, at half size and drawn last
     * so it sits over the crafted item on a row that is busy - the plate has no free corner while a job runs.
     * A CPU open to everything is the usual case and is left unmarked.
     */
    private void drawSelectionModeBadge(final CraftingCPUStatus cpu, final int x, final int y) {
        final int icon;
        switch (cpu.getSelectionMode()) {
            case PLAYER_ONLY:
                icon = 16 * 14 + 4;
                break;
            case MACHINE_ONLY:
                icon = 16 * 14 + 5;
                break;
            default:
                return;
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(x + SLOT_WIDTH - 9, y + SLOT_HEIGHT - 10, 0);
        this.drawIcon(icon);
        GL11.glPopMatrix();
    }

    /**
     * Wrapped to the slot width and shrunk, because the message is far wider than this table and the
     * screen next to it has no room to spare.
     */
    private void drawEmptyMessage(final FontRenderer font) {
        final float scale = 0.7f;
        final int width = (int) ((SLOT_WIDTH - 4) / scale);
        final List<String> lines = font.listFormattedStringToWidth(GuiText.NoCraftingCPUs.getLocal(), width);

        GL11.glPushMatrix();
        GL11.glTranslatef(-WIDTH + SLOT_LEFT + 2, SLOT_TOP + (this.hasAutomaticRow() ? SLOT_HEIGHT : 0) + 4, 0);
        GL11.glScalef(scale, scale, 1.0f);
        int y = 0;
        for (final String line : lines) {
            font.drawString(line, (width - font.getStringWidth(line)) / 2, y, TEXT_COLOR);
            y += font.FONT_HEIGHT;
        }
        GL11.glPopMatrix();
    }

    private void drawIcon(final int iconIndex) {
        AEBaseGui.enableSpriteBlending();
        this.parent.bindTexture("guis/states.png");
        final int uv_y = iconIndex / 16;
        final int uv_x = iconIndex - uv_y * 16;

        GL11.glScalef(0.5f, 0.5f, 1.0f);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.parent.drawTexturedModalRect(0, 0, uv_x * 16, uv_y * 16, 16, 16);
    }


    @Nullable
    public String getTooltip(final int mouseX, final int mouseY) {
        if (this.searchField != null && this.searchField.isMouseIn(mouseX, mouseY)) {
            return this.searchField.getMessage();
        }

        if (this.hitsAutomaticRow(mouseX, mouseY)) {
            return GuiText.Automatic.getLocal() + '\n' + GuiText.CraftingCPU.getLocal();
        }

        final CraftingCPUStatus cpu = this.hitCpu(mouseX, mouseY);
        if (cpu == null) {
            return null;
        }

        final StringBuilder tooltip = new StringBuilder();
        final String name = cpu.getName();
        if (name != null && !name.isEmpty()) {
            tooltip.append(name);
        } else {
            tooltip.append(GuiText.CPUs.getLocal());
            tooltip.append(" #");
            tooltip.append(cpu.getSerial());
        }
        tooltip.append('\n');

        final GenericStack crafting = cpu.getCrafting();
        if (crafting != null && crafting.amount() > 0) {
            final NumberFormat numberFormat = NumberFormat.getInstance();
            final long totalItems = Math.max(cpu.getTotalItems(), 0);
            final long remainingItems = Math.max(0, Math.min(cpu.getRemainingItems(), totalItems));
            final long completedItems = totalItems - remainingItems;
            final NumberFormat percentageFormat = NumberFormat.getPercentInstance();
            percentageFormat.setMinimumFractionDigits(2);
            percentageFormat.setMaximumFractionDigits(2);

            tooltip.append(TextFormatting.GREEN);
            tooltip.append(GuiText.CraftName.getLocal());
            tooltip.append(TextFormatting.RESET);
            tooltip.append(": ");
            tooltip.append(Platform.getItemDisplayName(crafting.what()));
            tooltip.append('\n');

            tooltip.append(TextFormatting.GREEN);
            tooltip.append(GuiText.Remains.getLocal());
            tooltip.append(TextFormatting.RESET);
            tooltip.append(": ");
            tooltip.append(crafting.what().formatAmount(crafting.amount(), AmountFormat.FULL));
            tooltip.append('\n');

            tooltip.append(TextFormatting.GREEN);
            tooltip.append(GuiText.Progress.getLocal());
            tooltip.append(TextFormatting.RESET);
            tooltip.append(": ");
            tooltip.append(numberFormat.format(completedItems));
            tooltip.append(" / ");
            tooltip.append(numberFormat.format(totalItems));
            if (totalItems > 0) {
                tooltip.append(" (");
                tooltip.append(TextFormatting.GOLD);
                tooltip.append(percentageFormat.format(getCraftingProgress(cpu)));
                tooltip.append(TextFormatting.RESET);
                tooltip.append(')');
            }
            tooltip.append('\n');

            final long elapsedMilliseconds = TimeUnit.MILLISECONDS.convert(cpu.getCraftingElapsedTime(),
                    TimeUnit.NANOSECONDS);
            tooltip.append(TextFormatting.GREEN);
            tooltip.append(GuiText.TimeUsed.getLocal());
            tooltip.append(TextFormatting.RESET);
            tooltip.append(": ");
            tooltip.append(DurationFormatUtils.formatDuration(elapsedMilliseconds, GuiText.ETAFormat.getLocal()));
            tooltip.append('\n');

            tooltip.append(TextFormatting.GREEN);
            tooltip.append(GuiText.CPUSourcePlayer.getLocal());
            tooltip.append(TextFormatting.RESET);
            tooltip.append(": ");
            tooltip.append(TextFormatting.GOLD);
            tooltip.append(cpu.getSourcePlayer() == null
                    ? GuiText.CPUSourceMachineRequested.getLocal()
                    : cpu.getSourcePlayer());
            tooltip.append(TextFormatting.RESET);
            tooltip.append('\n');
        }
        if (cpu.getStorage() > 0) {
            tooltip.append(GuiText.Bytes.getLocal());
            tooltip.append(": ");
            tooltip.append(cpu.formatStorage());
            tooltip.append('\n');
        }
        if (cpu.getCoprocessors() > 0) {
            tooltip.append(GuiText.CoProcessors.getLocal());
            tooltip.append(": ");
            tooltip.append(cpu.getCoprocessors());
            tooltip.append('\n');
        }
        if (cpu.getSelectionMode() != CpuSelectionMode.ANY) {
            tooltip.append(TextFormatting.GRAY);
            tooltip.append(cpu.getSelectionMode() == CpuSelectionMode.PLAYER_ONLY
                    ? ButtonToolTips.CpuSelectionModePlayersOnly.getLocal()
                    : ButtonToolTips.CpuSelectionModeAutomationOnly.getLocal());
            tooltip.append(TextFormatting.RESET);
            tooltip.append('\n');
        }

        return tooltip.toString();
    }

    public void mouseClicked(final int xCoord, final int yCoord, final int button) {
        if (this.searchField != null) {
            this.searchField.mouseClicked(xCoord, yCoord, button);
        }

        this.scrollbar.click(this.parent, xCoord - this.parent.getGuiLeft(), yCoord - this.parent.getGuiTop());

        final int row = this.hitRow(xCoord, yCoord);
        if (row < 0) {
            return;
        }

        final CraftingCPUStatus cpu = this.getCpuForRow(row);
        this.selectCpu(cpu == null ? -1 : cpu.getSerial());
    }

    public void mouseClickMove(final int xCoord, final int yCoord) {
        this.scrollbar.click(this.parent, xCoord - this.parent.getGuiLeft(), yCoord - this.parent.getGuiTop());
    }

    /**
     * @return true when the wheel belonged to the table and the screen should not scroll its own list.
     */
    public boolean handleMouseWheel(final int xCoord, final int yCoord, final int wheel) {
        if (wheel == 0 || !this.isOverTable(xCoord, yCoord)) {
            return false;
        }

        this.scrollbar.wheel(wheel);
        return true;
    }

    public Rectangle getExclusionArea() {
        return new Rectangle(this.parent.getGuiLeft() - WIDTH, this.parent.getGuiTop() - BUTTON_ROW_HEIGHT,
                WIDTH, FIXED_HEIGHT + this.rows * SLOT_HEIGHT + BUTTON_ROW_HEIGHT);
    }

    private void selectCpu(final int serial) {
        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("Terminal.Cpu.Set", Integer.toString(serial)));
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    private boolean hasAutomaticRow() {
        return this.host.allowsAutomaticCpu();
    }

    private int getRowCount() {
        return this.displayed.size() + (this.hasAutomaticRow() ? 1 : 0);
    }

    /**
     * @return the CPU shown in the given row, or null for the row that leaves the choice to the network.
     */
    @Nullable
    private CraftingCPUStatus getCpuForRow(final int row) {
        final int index = this.hasAutomaticRow() ? row - 1 : row;
        return index >= 0 && index < this.displayed.size() ? this.displayed.get(index) : null;
    }

    private boolean isOverTable(int x, int y) {
        x -= this.parent.getGuiLeft() - WIDTH;
        y -= this.parent.getGuiTop();
        return x >= SLOT_LEFT && x < SLOT_WIDTH + SLOT_LEFT && y >= SLOT_TOP
                && y < SLOT_TOP + this.rows * SLOT_HEIGHT;
    }

    /**
     * @return the row under the cursor, or -1 when the cursor is not over one.
     */
    private int hitRow(int x, int y) {
        if (!this.isOverTable(x, y)) {
            return -1;
        }

        y -= this.parent.getGuiTop();
        final int row = this.scrollbar.getCurrentScroll() + (y - SLOT_TOP) / SLOT_HEIGHT;
        return row >= 0 && row < this.getRowCount() ? row : -1;
    }

    @Nullable
    private CraftingCPUStatus hitCpu(final int x, final int y) {
        final int row = this.hitRow(x, y);
        return row < 0 ? null : this.getCpuForRow(row);
    }

    private boolean hitsAutomaticRow(final int x, final int y) {
        return this.hasAutomaticRow() && this.hitRow(x, y) == 0;
    }

    private static double getCraftingProgress(final CraftingCPUStatus cpu) {
        final long totalItems = Math.max(cpu.getTotalItems(), 0);
        if (totalItems == 0) {
            return 0;
        }

        final long remainingItems = Math.max(0, Math.min(cpu.getRemainingItems(), totalItems));
        return (double) (totalItems - remainingItems) / totalItems;
    }

    private static int calculateProgressColor(final double progress) {
        if (progress <= 0.5) {
            return interpolateColor(PROGRESS_START_COLOR, PROGRESS_MIDDLE_COLOR, progress * 2);
        }
        return interpolateColor(PROGRESS_MIDDLE_COLOR, PROGRESS_END_COLOR, (progress - 0.5) * 2);
    }

    private static int interpolateColor(final int start, final int end, final double ratio) {
        final int alpha = interpolateChannel(start >>> 24, end >>> 24, ratio);
        final int red = interpolateChannel(start >>> 16 & 0xFF, end >>> 16 & 0xFF, ratio);
        final int green = interpolateChannel(start >>> 8 & 0xFF, end >>> 8 & 0xFF, ratio);
        final int blue = interpolateChannel(start & 0xFF, end & 0xFF, ratio);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int interpolateChannel(final int start, final int end, final double ratio) {
        return (int) (start + ratio * (end - start));
    }
}
