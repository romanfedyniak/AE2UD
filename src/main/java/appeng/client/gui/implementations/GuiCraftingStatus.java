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

/**
 *
 */

package appeng.client.gui.implementations;


import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerCraftingStatus;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.core.AELog;
import appeng.core.features.registries.WirelessRegistry;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartTerminal;
import appeng.util.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class GuiCraftingStatus extends GuiCraftingCPU {

    private static final int CPU_TABLE_WIDTH = 94;
    private static final int CPU_TABLE_FIXED_HEIGHT = 26;
    private static final int CPU_TABLE_SLOT_XOFF = 100;
    private static final int CPU_TABLE_SLOT_YOFF = 0;
    private static final int CPU_TABLE_SLOT_WIDTH = 67;
    private static final int CPU_TABLE_SLOT_HEIGHT = 23;
    private static final int PROGRESS_START_COLOR = 0xFFE60A00;
    private static final int PROGRESS_MIDDLE_COLOR = 0xFFE6E600;
    private static final int PROGRESS_END_COLOR = 0xFF0AE600;
    private static final int SUSPENDED_OVERLAY_COLOR = 0xA0404040;

    private final ContainerCraftingStatus status;
    private GuiScrollbar cpuScrollbar;

    private GuiTabButton originalGuiBtn;
    private GuiBridge originalGui;
    private ItemStack myIcon = ItemStack.EMPTY;

    public GuiCraftingStatus(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftingStatus(inventoryPlayer, te));

        this.status = (ContainerCraftingStatus) this.inventorySlots;
        final Object target = this.status.getTarget();
        final IDefinitions definitions = AEApi.instance().definitions();
        final IParts parts = definitions.parts();

        if (target instanceof WirelessTerminalGuiObject) {
            myIcon = ((WirelessTerminalGuiObject) target).getItemStack();
            this.originalGui = (GuiBridge) AEApi.instance().registries().wireless().getWirelessTerminalHandler(myIcon).getGuiHandler(myIcon);
        }

        if (target instanceof PartTerminal) {
            this.myIcon = parts.terminal().maybeStack(1).orElse(ItemStack.EMPTY);

            this.originalGui = GuiBridge.GUI_ME;
        }

        if (target instanceof PartCraftingTerminal) {
            this.myIcon = parts.craftingTerminal().maybeStack(1).orElse(ItemStack.EMPTY);

            this.originalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (target instanceof PartPatternTerminal) {
            this.myIcon = parts.patternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);

            this.originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (target instanceof PartExpandedProcessingPatternTerminal) {
            myIcon = parts.expandedProcessingPatternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = GuiBridge.GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        this.cpuScrollbar = new GuiScrollbar();
        this.cpuScrollbar.setLeft(-16);
        this.cpuScrollbar.setTop(19);
        this.cpuScrollbar.setWidth(12);
        this.cpuScrollbar.setHeight(this.rows * CPU_TABLE_SLOT_HEIGHT - 1);

        this.terminalStyleBox.x = this.guiLeft + this.xSize;
        this.terminalStyleBox.y = this.guiTop + 8;

        if (!this.myIcon.isEmpty()) {
            this.buttonList.add(
                    this.originalGuiBtn = new GuiTabButton(this.guiLeft + 213, this.guiTop - 4, this.myIcon, this.myIcon.getDisplayName(), this.itemRender));
            this.originalGuiBtn.setHideEdge(13);
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        List<CraftingCPUStatus> cpus = this.status.getCPUs();
        this.cpuScrollbar.setRange(0, Integer.max(0, cpus.size() - this.rows), 1);
        super.drawScreen(mouseX, mouseY, btn);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        List<CraftingCPUStatus> cpus = this.status.getCPUs();
        final int firstCpu = this.cpuScrollbar.getCurrentScroll();
        CraftingCPUStatus hoveredCpu = hitCpu(mouseX, mouseY);
        {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            final int TEXT_COLOR = 0x202020;
            for (int i = firstCpu; i < firstCpu + this.rows; i++) {
                if (i < 0 || i >= cpus.size()) {
                    continue;
                }
                CraftingCPUStatus cpu = cpus.get(i);
                if (cpu == null) {
                    continue;
                }
                int x = -CPU_TABLE_WIDTH + 9;
                int y = 19 + (i - firstCpu) * CPU_TABLE_SLOT_HEIGHT;
                if (cpu.getSerial() == this.status.selectedCpuSerial) {
                    GL11.glColor4f(0.0F, 0.8352F, 1.0F, 1.0F);
                } else if (hoveredCpu != null && hoveredCpu.getSerial() == cpu.getSerial()) {
                    GL11.glColor4f(0.65F, 0.9F, 1.0F, 1.0F);
                } else {
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                }
                this.bindTexture("guis/cpu_selector.png");
                this.drawTexturedModalRect(x, y, CPU_TABLE_SLOT_XOFF, CPU_TABLE_SLOT_YOFF, CPU_TABLE_SLOT_WIDTH, CPU_TABLE_SLOT_HEIGHT);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                String name = cpu.getName();
                if (name == null || name.isEmpty()) {
                    name = GuiText.CPUs.getLocal() + " #" + cpu.getSerial();
                }
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
                    final int iconIndex = 16 * 11 + 2;
                    this.bindTexture("guis/states.png");
                    final int uv_y = iconIndex / 16;
                    final int uv_x = iconIndex - uv_y * 16;

                    GL11.glScalef(0.5f, 0.5f, 1.0f);
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    this.drawTexturedModalRect(0, 0, uv_x * 16, uv_y * 16, 16, 16);
                    GL11.glTranslatef(18.0f, 2.0f, 0.0f);
                    String amount = craftingStack.what().formatAmount(craftingStack.amount(), AmountFormat.PREVIEW_LARGE);
                    if (amount.length() > 5) {
                        amount = amount.substring(0, 5) + "..";
                    }
                    GL11.glScalef(1.5f, 1.5f, 1.0f);
                    font.drawString(amount, 0, 0, 0x009000);
                    GL11.glPopMatrix();
                    GL11.glPushMatrix();
                    GL11.glTranslatef(x + CPU_TABLE_SLOT_WIDTH - 19, y + 3, 0);
                    this.drawItem(0, 0, GenericStack.wrapInItemStack(craftingStack));
                    GL11.glPopMatrix();
                    GL11.glPushMatrix();

                    final double craftingProgress = getCraftingProgress(cpu);
                    final int progressWidth = (int) ((CPU_TABLE_SLOT_WIDTH - 2) * craftingProgress);
                    if (progressWidth > 0) {
                        drawRect(
                                x + 1,
                                y + CPU_TABLE_SLOT_HEIGHT - 2,
                                x + 1 + progressWidth,
                                y + CPU_TABLE_SLOT_HEIGHT - 1,
                                calculateProgressColor(craftingProgress));
                    }

                    if (cpu.isSuspended()) {
                        drawRect(x, y, x + CPU_TABLE_SLOT_WIDTH, y + CPU_TABLE_SLOT_HEIGHT, SUSPENDED_OVERLAY_COLOR);
                    }
                } else {
                    final int iconIndex = 16 * 4 + 3;
                    this.bindTexture("guis/states.png");
                    final int uv_y = iconIndex / 16;
                    final int uv_x = iconIndex - uv_y * 16;

                    GL11.glScalef(0.5f, 0.5f, 1.0f);
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    this.drawTexturedModalRect(0, 0, uv_x * 16, uv_y * 16, 16, 16);
                    GL11.glTranslatef(18.0f, 2.0f, 0.0f);
                    GL11.glScalef(1.5f, 1.5f, 1.0f);
                    font.drawString(cpu.formatStorage(), 0, 0, TEXT_COLOR);
                }
                GL11.glPopMatrix();
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
        StringBuilder tooltip = new StringBuilder();
        if (hoveredCpu != null) {
            String name = hoveredCpu.getName();
            if (name != null && !name.isEmpty()) {
                tooltip.append(name);
                tooltip.append('\n');
            } else {
                tooltip.append(GuiText.CPUs.getLocal());
                tooltip.append(" #");
                tooltip.append(hoveredCpu.getSerial());
                tooltip.append('\n');
            }
            GenericStack crafting = hoveredCpu.getCrafting();
            if (crafting != null && crafting.amount() > 0) {
                final NumberFormat numberFormat = NumberFormat.getInstance();
                final long totalItems = Math.max(hoveredCpu.getTotalItems(), 0);
                final long remainingItems = Math.max(0, Math.min(hoveredCpu.getRemainingItems(), totalItems));
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
                    tooltip.append(percentageFormat.format(getCraftingProgress(hoveredCpu)));
                    tooltip.append(TextFormatting.RESET);
                    tooltip.append(')');
                }
                tooltip.append('\n');

                final long elapsedMilliseconds = TimeUnit.MILLISECONDS.convert(
                        hoveredCpu.getCraftingElapsedTime(),
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
                tooltip.append(hoveredCpu.getSourcePlayer() == null
                        ? GuiText.CPUSourceMachineRequested.getLocal()
                        : hoveredCpu.getSourcePlayer());
                tooltip.append(TextFormatting.RESET);
                tooltip.append('\n');
            }
            if (hoveredCpu.getStorage() > 0) {
                tooltip.append(GuiText.Bytes.getLocal());
                tooltip.append(": ");
                tooltip.append(hoveredCpu.formatStorage());
                tooltip.append('\n');
            }
            if (hoveredCpu.getCoprocessors() > 0) {
                tooltip.append(GuiText.CoProcessors.getLocal());
                tooltip.append(": ");
                tooltip.append(hoveredCpu.getCoprocessors());
                tooltip.append('\n');
            }
        }
        if (this.cpuScrollbar != null) {
            this.cpuScrollbar.draw(this);
        }
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        if (tooltip.length() > 0) {
            this.drawTooltip(mouseX - offsetX, mouseY - offsetY, tooltip.toString());
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        this.bindTexture("guis/cpu_selector.png");
        final int tableLeft = offsetX - CPU_TABLE_WIDTH;
        this.drawTexturedModalRect(tableLeft, offsetY, 0, 0, CPU_TABLE_WIDTH, 41);
        int y = 41;
        for (int row = 1; row < this.rows - 1; row++) {
            this.drawTexturedModalRect(tableLeft, offsetY + y, 0, 41,
                    CPU_TABLE_WIDTH, CPU_TABLE_SLOT_HEIGHT);
            y += CPU_TABLE_SLOT_HEIGHT;
        }
        this.drawTexturedModalRect(tableLeft, offsetY + y, 0, 133, CPU_TABLE_WIDTH, 31);
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        Rectangle craftingCPUArea = new Rectangle(this.guiLeft - CPU_TABLE_WIDTH, this.guiTop,
                CPU_TABLE_WIDTH, CPU_TABLE_FIXED_HEIGHT + this.rows * CPU_TABLE_SLOT_HEIGHT);
        List<Rectangle> area = new ArrayList<>(super.getJEIExclusionArea());
        area.add(craftingCPUArea);
        return area;
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) throws IOException {
        super.mouseClicked(xCoord, yCoord, btn);

        if (cpuScrollbar != null) {
            cpuScrollbar.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);
        }
        CraftingCPUStatus hit = hitCpu(xCoord, yCoord);
        if (hit != null) {
            try {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Terminal.Cpu.Set", Integer.toString(hit.getSerial())));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int c, long d) {
        super.mouseClickMove(x, y, c, d);
        if (cpuScrollbar != null) {
            cpuScrollbar.click(this, x - this.guiLeft, y - this.guiTop);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        x -= guiLeft - CPU_TABLE_WIDTH;
        y -= guiTop;
        int dwheel = Mouse.getEventDWheel();
        if (x >= 9 && x < CPU_TABLE_SLOT_WIDTH + 9 && y >= 19
                && y < 19 + this.rows * CPU_TABLE_SLOT_HEIGHT) {
            if (this.cpuScrollbar != null && dwheel != 0) {
                this.cpuScrollbar.wheel(dwheel);
                return;
            }
        }
        super.handleMouseInput();
    }

    private CraftingCPUStatus hitCpu(int x, int y) {
        x -= guiLeft - CPU_TABLE_WIDTH;
        y -= guiTop;
        if (!(x >= 9 && x < CPU_TABLE_SLOT_WIDTH + 9 && y >= 19
                && y < 19 + this.rows * CPU_TABLE_SLOT_HEIGHT)) {
            return null;
        }
        int scrollOffset = this.cpuScrollbar != null ? this.cpuScrollbar.getCurrentScroll() : 0;
        int cpuId = scrollOffset + (y - 19) / CPU_TABLE_SLOT_HEIGHT;
        List<CraftingCPUStatus> cpus = this.status.getCPUs();
        return (cpuId >= 0 && cpuId < cpus.size()) ? cpus.get(cpuId) : null;
    }

    @Override
    protected String getGuiDisplayName(final String in) {
        return in; // the cup name is on the button
    }

    public void postCPUUpdate(CraftingCPUStatus[] cpus) {
        this.status.postCPUUpdate(cpus);
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
