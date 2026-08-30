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


import appeng.api.config.ActionItems;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.config.PlaneMode;
import appeng.api.config.RedstoneMode;
import appeng.api.upgrades.UpgradeCards;
import appeng.client.gui.widgets.GuiCraftPriorityButton;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerFormationPlane;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.parts.automation.PartFormationPlane;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Mouse;

import java.io.IOException;


public class GuiFormationPlane extends GuiUpgradeable {

    private GuiTabButton priority;
    private GuiImgButton placeMode;
    private GuiImgButton clear;
    private GuiImgButton keyTypes;
    private GuiImgButton planeMode;
    private GuiImgButton craftMode;
    private GuiCraftPriorityButton craftPriority;

    public GuiFormationPlane(final InventoryPlayer inventoryPlayer, final PartFormationPlane te) {
        super(new ContainerFormationPlane(inventoryPlayer, te));
        this.ySize = 251;
    }

    @Override
    protected void addButtons() {
        this.clear = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.ACTIONS, ActionItems.CLOSE);
        this.keyTypes = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.ACTIONS, ActionItems.CONFIGURE_PLACED_TYPES);
        this.planeMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.PLANE_MODE, PlaneMode.PASSIVE);
        this.placeMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.PLACE_BLOCK, YesNo.YES);
        this.redstoneMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.fuzzyMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.craftMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.CRAFT_ONLY, YesNo.NO);
        // Under the button the crafting card already adds, and shown on the same terms as that one.
        this.craftPriority = new GuiCraftPriorityButton(this.guiLeft - 18, this.guiTop + 8);

        this.column.clear();
        this.column.add(this.clear);
        this.column.add(this.keyTypes);
        this.column.add(this.planeMode);
        this.column.add(this.placeMode);
        this.column.add(this.redstoneMode);
        this.column.add(this.fuzzyMode);
        this.column.add(this.craftMode);
        this.column.add(this.craftPriority);

        this.buttonList.add(this.priority = new GuiTabButton(this.guiLeft + 154, this.guiTop, 2 + 4 * 16, GuiText.Priority.getLocal(), this.itemRender));
        this.buttonList.addAll(this.column);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.FormationPlane.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);

        if (this.fuzzyMode != null) {
            this.fuzzyMode.set(this.cvb.getFuzzyMode());
        }

        if (this.placeMode != null) {
            this.placeMode.set(((ContainerFormationPlane) this.cvb).getPlaceMode());
        }

        if (this.planeMode != null) {
            this.planeMode.set(((ContainerFormationPlane) this.cvb).getPlaneMode());
        }

        if (this.craftMode != null) {
            this.craftMode.set(((ContainerFormationPlane) this.cvb).getCraftingMode());
        }
    }

    /**
     * A crafting card is only worth anything to a plane that fetches what it places, so both of its buttons
     * wait for the active mode as well as for the card.
     */
    @Override
    protected void handleButtonVisibility() {
        super.handleButtonVisibility();

        final boolean carded = this.bc.getInstalledUpgrades(UpgradeCards.crafting()) > 0
                && ((ContainerFormationPlane) this.cvb).getPlaneMode() == PlaneMode.ACTIVE;

        if (this.craftMode != null) {
            this.craftMode.setVisibility(carded);
        }

        if (this.craftPriority != null) {
            this.craftPriority.visible = carded;
            this.craftPriority.enabled = carded;
            this.craftPriority.setPriority(this.cvb.getCraftPriority());
        }
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.priority) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
        } else if (btn == this.clear) {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("Filter.Clear", ""));
        } else if (btn == this.keyTypes) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_KEY_TYPES));
        } else if (btn == this.placeMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.placeMode.getSetting(), backwards));
        } else if (btn == this.planeMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.planeMode.getSetting(), backwards));
        } else if (btn == this.craftMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.craftMode.getSetting(), backwards));
        } else if (btn == this.craftPriority) {
            this.mc.displayGuiScreen(new GuiCraftPriority(this, this.mc.player.inventory,
                    UpgradeCards.crafting(), this.cvb));
        }
    }
}
