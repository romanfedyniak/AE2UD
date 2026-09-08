/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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


import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;


/**
 * A terminal carrying a real 3x3 crafting grid. The wired and wireless crafting terminals differ only in
 * which container they build and in whether they are wireless, so everything the screen itself does lives
 * here.
 */
public abstract class GuiCraftingTermBase extends GuiMEMonitorable {

    private GuiImgButton clearBtn;

    public GuiCraftingTermBase(final InventoryPlayer inventoryPlayer, final ITerminalHost te, final ContainerMEMonitorable c) {
        super(inventoryPlayer, te, c);
        this.setReservedSpace(73);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        if (this.clearBtn == btn) {
            final Slot s = this.firstGridSlot();

            if (s != null) {
                NetworkHandler.instance().sendToServer(new PacketInventoryAction(InventoryAction.MOVE_REGION, s.slotNumber, 0));
            }
        }
    }

    /**
     * @return any one slot of the crafting grid. The action that empties it works on the whole region, and
     * the server finds the rest from the class of the slot it is given.
     */
    private Slot firstGridSlot() {
        for (final Slot s : this.inventorySlots.inventorySlots) {
            if (s instanceof SlotCraftingMatrix) {
                return s;
            }
        }

        return null;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.add(this.clearBtn = new GuiImgButton(this.guiLeft + 92, this.guiTop + this.ySize - 156, Settings.ACTIONS, ActionItems.STASH));
        this.clearBtn.setHalfSize(true);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        this.fontRenderer.drawString(GuiText.CraftingTerminal.getLocal(), 8, this.ySize - 96 + 1 - this.getReservedSpace(), 4210752);
    }

    @Override
    protected String getBackground() {
        return "guis/crafting.png";
    }
}
