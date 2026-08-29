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

package appeng.container.implementations;


import appeng.container.AEBaseContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;


/**
 * Something for the button settings screen to hang on, and nothing else. What that screen edits is the
 * client's own and no server ever hears about it, so this holds no slots and syncs nothing - see
 * {@link appeng.client.gui.implementations.GuiAmountSteps}.
 */
public class ContainerAmountSteps extends AEBaseContainer {

    public ContainerAmountSteps(final InventoryPlayer ip) {
        super(ip, null, null);
    }

    @Override
    public boolean canInteractWith(final EntityPlayer player) {
        return true;
    }
}
