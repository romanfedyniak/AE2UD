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

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.client.gui.widgets.GuiTerminalModeSwitch;
import appeng.container.implementations.ContainerWirelessInterfaceConfigurationTerminal;
import appeng.container.interfaces.IWirelessTerminalContainer;
import appeng.helpers.WirelessTerminalGuiObject;

public class GuiWirelessInterfaceConfigurationTerminal extends GuiInterfaceConfigurationTerminal {

    /**
     * Three pixels clear of the window, and below the step in its right edge. The lower part of this window is
     * narrower than the rest, and the corner between the two takes two rows to come in.
     */
    private static final int PLATE_X = 193;

    /**
     * Where the search box is remembered between openings. The panel on a cable keeps it on itself; a terminal
     * in a pocket has nowhere of its own, and one player has one of these screens open at a time.
     */
    private static String rememberedSearch = "";

    private final GuiTerminalModeSwitch modeSwitch = new GuiTerminalModeSwitch(this);

    public GuiWirelessInterfaceConfigurationTerminal(final InventoryPlayer inventoryPlayer,
            final WirelessTerminalGuiObject te) {
        super(new ContainerWirelessInterfaceConfigurationTerminal(inventoryPlayer, te), null);
    }

    private int plateY() {
        return this.ySize - 74;
    }

    @Override
    protected String loadSearchText() {
        return rememberedSearch;
    }

    @Override
    protected void saveSearchText(final String text) {
        rememberedSearch = text;
    }

    @Override
    protected void addExtraButtons() {
        if (this.inventorySlots instanceof IWirelessTerminalContainer) {
            this.modeSwitch.attach(this.buttonList,
                    ((IWirelessTerminalContainer) this.inventorySlots).getTerminal(), this.guiLeft, this.guiTop);
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (this.modeSwitch.actionPerformed(btn)) {
            return;
        }

        super.actionPerformed(btn);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/wirelessupgrades.png");
        Gui.drawModalRectWithCustomSizedTexture(offsetX + PLATE_X, offsetY + this.plateY(), 0, 0, 32, 32, 32, 32);
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> area = new ArrayList<>(super.getJEIExclusionArea());
        area.add(new Rectangle(this.guiLeft + PLATE_X, this.guiTop + this.plateY(), 32, 32));
        this.modeSwitch.addExclusionAreas(area);
        return area;
    }
}
