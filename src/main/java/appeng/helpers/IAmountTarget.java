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

package appeng.helpers;


import appeng.container.implementations.ContainerSetAmount;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;


/**
 * Something one number can be typed into by {@link appeng.client.gui.implementations.GuiSetAmount}: a fake
 * slot, an inventory elsewhere on the network, a setting on a machine.
 * <p>
 * A target is built where the amount screen is opened, held by that screen's container while it is open,
 * and handed the amount when the player confirms. Putting the amount away is the target's own business,
 * including when to send the player back to the screen they came from: the two are not independent, and
 * which of them has to come first differs from one target to the next.
 */
public interface IAmountTarget {

    /**
     * What the amount screen shows in its display slot, so that the window says what it is about.
     */
    ItemStack getIcon();

    /**
     * The amount to start on - what the target already holds.
     */
    long getAmount();

    long getMinAmount();

    long getMaxAmount();

    /**
     * @param amount already held between {@link #getMinAmount()} and {@link #getMaxAmount()}
     * @param from   the amount screen's container, which is the player's current one
     */
    void apply(EntityPlayer player, ContainerSetAmount from, long amount);
}
