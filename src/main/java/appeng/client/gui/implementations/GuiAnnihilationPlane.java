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


import java.io.IOException;

import appeng.api.config.ActionItems;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerAnnihilationPlane;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.parts.automation.PartAnnihilationPlane;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;


public class GuiAnnihilationPlane extends GuiUpgradeable {

    private GuiImgButton clear;
    private GuiImgButton keyTypes;

    public GuiAnnihilationPlane(final InventoryPlayer inventoryPlayer, final PartAnnihilationPlane te) {
        super(new ContainerAnnihilationPlane(inventoryPlayer, te));
        this.ySize = 251;
    }

    @Override
    protected void addButtons() {
        this.clear = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.ACTIONS, ActionItems.CLOSE);
        this.keyTypes = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.ACTIONS, ActionItems.CONFIGURE_PICKED_UP_TYPES);
        // Last in the column because it is the only one here that hides.
        this.fuzzyMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 48, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);

        this.buttonList.add(this.clear);
        this.buttonList.add(this.keyTypes);
        this.buttonList.add(this.fuzzyMode);
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    @Override
    protected GuiText getName() {
        return GuiText.AnnihilationPlane;
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.clear) {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("Filter.Clear", ""));
        } else if (btn == this.keyTypes) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_KEY_TYPES));
        }
    }
}
