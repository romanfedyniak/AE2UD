/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
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


import appeng.api.parts.PartItemStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiSmallButton;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerMonitor;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.parts.reporting.AbstractPartMonitor;
import appeng.parts.reporting.ThroughputUnit;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import java.io.IOException;


/**
 * The window behind every monitor: what it watches, and whether that can still be changed.
 */
public class GuiMonitor extends AEBaseGui {

    private static final int BUTTON_ROW = 48;
    private static final int BUTTON_HEIGHT = 16;
    private static final int TOOLTIP_WIDTH = 160;

    /** Beside the slot, since it is about the slot rather than about the rate below it. */
    private static final int LOCK_X = 104;

    private static final int UNIT_WIDTH = 34;
    private static final int FIGURE_WIDTH = 38;
    private static final int UNIT_X = (ContainerMonitor.WIDTH - UNIT_WIDTH - 4 - FIGURE_WIDTH) / 2;
    private static final int FIGURE_X = UNIT_X + UNIT_WIDTH + 4;

    private final ContainerMonitor container;
    private final AbstractPartMonitor monitor;

    private GuiToggleButton lock;
    private GuiSmallButton unit;
    private GuiSmallButton figure;

    public GuiMonitor(final InventoryPlayer inventoryPlayer, final AbstractPartMonitor monitor) {
        super(new ContainerMonitor(inventoryPlayer, monitor));

        this.container = (ContainerMonitor) this.inventorySlots;
        this.monitor = monitor;
        this.xSize = ContainerMonitor.WIDTH;
        this.ySize = ContainerMonitor.HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();

        // The padlock the terminal's kept-search setting wears, which is the one icon in the sheet that
        // already means exactly this.
        this.lock = new GuiToggleButton(this.guiLeft + LOCK_X, this.guiTop + ContainerMonitor.CONFIG_Y,
                16 * 2 + 8, 16 * 2 + 7, GuiText.MonitorLock.getLocal(), GuiText.MonitorLockHint.getLocal());

        this.unit = new GuiSmallButton(1, this.guiLeft + UNIT_X, this.guiTop + BUTTON_ROW, UNIT_WIDTH, BUTTON_HEIGHT, "");
        this.figure = new GuiSmallButton(2, this.guiLeft + FIGURE_X, this.guiTop + BUTTON_ROW, FIGURE_WIDTH, BUTTON_HEIGHT, "");

        this.buttonList.add(this.lock);
        this.buttonList.add(this.unit);
        this.buttonList.add(this.figure);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.lock) {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("Monitor.Lock",
                    this.container.isLocked() ? "0" : "1"));
        } else if (btn == this.unit) {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("Monitor.Unit", "1"));
        } else if (btn == this.figure) {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("Monitor.Figure", "1"));
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final ThroughputUnit unit = this.container.getUnit();

        this.lock.setState(this.container.isLocked());
        this.unit.displayString = unit.getLabel().getLocal();
        this.unit.enabled = this.container.isThroughputAllowed();
        this.figure.displayString = this.container.getFigure().getLabel().getLocal();
        // Which of the three numbers to show is not a question while none of them is being measured.
        this.figure.enabled = this.container.isThroughputAllowed() && unit != ThroughputUnit.OFF;

        this.unit.setTooltip(this.tooltip(GuiText.MonitorThroughput, GuiText.MonitorThroughputHint));
        this.figure.setTooltip(this.tooltip(GuiText.MonitorShowing, GuiText.MonitorShowingHint));

        this.fontRenderer.drawString(this.monitor.getItemStack(PartItemStack.NETWORK).getDisplayName(), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);
    }

    /**
     * A button's name over what it is for, wrapped to a readable width - the sentence is longer in most other
     * languages than it is in English, so it is measured rather than trusted to fit.
     */
    private String tooltip(final GuiText name, final GuiText hint) {
        return name.getLocal() + '\n'
                + String.join("\n", this.fontRenderer.listFormattedStringToWidth(hint.getLocal(), TOOLTIP_WIDTH));
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.drawPanel(offsetX, offsetY, this.xSize, this.ySize);

        drawSlotWell(offsetX + ContainerMonitor.CONFIG_X, offsetY + ContainerMonitor.CONFIG_Y);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotWell(offsetX + 8 + col * 18, offsetY + ContainerMonitor.HEIGHT - 82 + row * 18);
            }
        }

        for (int col = 0; col < 9; col++) {
            drawSlotWell(offsetX + 8 + col * 18, offsetY + ContainerMonitor.HEIGHT - 24);
        }
    }
}
