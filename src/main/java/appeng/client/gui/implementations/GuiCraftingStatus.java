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
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerCraftingStatus;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartTerminal;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class GuiCraftingStatus extends GuiCraftingCPU {

    private final ContainerCraftingStatus status;
    private final GuiCraftingCPUTable cpuTable;

    private GuiTabButton originalGuiBtn;
    private GuiBridge originalGui;
    private ItemStack myIcon = ItemStack.EMPTY;

    public GuiCraftingStatus(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftingStatus(inventoryPlayer, te));

        this.status = (ContainerCraftingStatus) this.inventorySlots;
        this.cpuTable = new GuiCraftingCPUTable(this, this.status);
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
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (this.cpuTable.actionPerformed(btn)) {
            return;
        }

        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
        }
    }

    /**
     * The CPU shown here is whichever the table points at, and configuring one from across the network is not
     * what this screen is for. The mode is set on the CPU itself.
     */
    @Override
    protected boolean canEditSelectionMode() {
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.cpuTable.initGui(this.rows, this.buttonList);

        this.terminalStyleBox.x = this.guiLeft + this.xSize;
        this.terminalStyleBox.y = this.guiTop + 8;
        this.toggleHideStored.x = this.terminalStyleBox.x;
        this.toggleHideStored.y = this.terminalStyleBox.y + 20;

        if (!this.myIcon.isEmpty()) {
            this.buttonList.add(
                    this.originalGuiBtn = new GuiTabButton(this.guiLeft + 213, this.guiTop - 4, this.myIcon, this.myIcon.getDisplayName(), this.itemRender));
            this.originalGuiBtn.setHideEdge(13);
        }
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (this.cpuTable.keyTyped(character, key)) {
            return;
        }

        super.keyTyped(character, key);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.cpuTable.updateScrollRange();
        super.drawScreen(mouseX, mouseY, btn);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.cpuTable.drawFG(mouseX, mouseY);

        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        final String tooltip = this.cpuTable.getTooltip(mouseX, mouseY);
        if (tooltip != null) {
            this.drawTooltip(mouseX - offsetX, mouseY - offsetY, tooltip);
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        this.cpuTable.drawBG(offsetX, offsetY);
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> area = new ArrayList<>(super.getJEIExclusionArea());
        area.add(this.cpuTable.getExclusionArea());
        addButtonArea(area, this.originalGuiBtn);
        return area;
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) throws IOException {
        super.mouseClicked(xCoord, yCoord, btn);

        this.cpuTable.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(int x, int y, int c, long d) {
        super.mouseClickMove(x, y, c, d);
        this.cpuTable.mouseClickMove(x, y);
    }

    @Override
    public void handleMouseInput() throws IOException {
        final int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
        final int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (this.cpuTable.handleMouseWheel(x, y, Mouse.getEventDWheel())) {
            return;
        }

        super.handleMouseInput();
    }

    @Override
    protected String getGuiDisplayName(final String in) {
        return in; // the cup name is on the button
    }
}
