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


import appeng.container.implementations.ContainerSetAmount;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSetAmount;
import appeng.helpers.Reflected;
import net.minecraft.entity.player.InventoryPlayer;


/**
 * The craft-amount screen, reused to type an amount into a fake slot instead of ordering a craft. Only the
 * title, the confirm button and what the confirm does differ, so it subclasses rather than copying the
 * text box and the eight step buttons.
 */
public class GuiSetAmount extends GuiCraftAmount {

    @Reflected
    public GuiSetAmount(final InventoryPlayer inventoryPlayer, final Object host) {
        super(new ContainerSetAmount(inventoryPlayer, host));
    }

    @Override
    protected long getInitialAmount() {
        return ((ContainerSetAmount) this.inventorySlots).initialAmount;
    }

    @Override
    protected boolean startsFromSuggestion() {
        // What the slot already held, not a suggestion - the first step button adds to it.
        return false;
    }

    @Override
    protected String getTitle() {
        return GuiText.SetAmount.getLocal();
    }

    @Override
    protected String getConfirmLabel() {
        return GuiText.Set.getLocal();
    }

    @Override
    protected void confirm(final long amount) {
        NetworkHandler.instance().sendToServer(new PacketSetAmount(amount));
    }
}
