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

package appeng.helpers;


import appeng.container.implementations.ContainerSetAmount;
import appeng.core.sync.packets.PacketSwitchGuis;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;


/**
 * A machine's priority, which is a number about the machine rather than about anything in it - so the
 * window shows the machine itself, and reads in nothing but plain numbers.
 */
public class PriorityAmountTarget implements IAmountTarget {

    private final IPriorityHost host;

    public PriorityAmountTarget(final IPriorityHost host) {
        this.host = host;
    }

    @Override
    public ItemStack getIcon() {
        return this.host.getItemStackRepresentation();
    }

    @Override
    public long getAmount() {
        return this.host.getPriority();
    }

    @Override
    public long getMinAmount() {
        return Integer.MIN_VALUE;
    }

    @Override
    public long getMaxAmount() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void apply(final EntityPlayer player, final ContainerSetAmount from, final long amount) {
        this.host.setPriority((int) amount);

        PacketSwitchGuis.reopen(player, from, this.host.getGuiBridge());
    }
}
