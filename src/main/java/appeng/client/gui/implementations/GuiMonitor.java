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
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerMonitor;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.parts.reporting.AbstractPartMonitor;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import java.io.IOException;


/**
 * The window behind every monitor: what it watches, and whether that can still be changed.
 */
public class GuiMonitor extends AEBaseGui {


    /** Beside the slot, since it is about the slot rather than about the rate below it. */
    private static final int LOCK_X = 104;

    private final ContainerMonitor container;
    private final AbstractPartMonitor monitor;

    private GuiToggleButton lock;

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

        this.buttonList.add(this.lock);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.lock) {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("Monitor.Lock",
                    this.container.isLocked() ? "0" : "1"));
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.lock.setState(this.container.isLocked());

        this.fontRenderer.drawString(this.monitor.getItemStack(PartItemStack.NETWORK).getDisplayName(), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);
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
