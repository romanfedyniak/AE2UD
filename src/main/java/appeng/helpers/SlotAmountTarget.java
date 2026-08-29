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


import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.container.implementations.ContainerSetAmount;
import appeng.container.slot.SlotFake;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.packets.PacketSwitchGuis;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;


/**
 * One fake slot of the screen the amount was opened from.
 * <p>
 * That screen's container does not exist while the amount screen is open - the player's container was
 * replaced by it - so the slot itself cannot be held onto. It is named by its place in the container that
 * comes back, which is why this returns the player first and writes second.
 */
public class SlotAmountTarget implements IAmountTarget {

    private final GuiBridge originGui;
    private final int slot;
    private final AEKey what;
    private final long amount;
    private final long max;

    public SlotAmountTarget(final GuiBridge originGui, final int slot, final AEKey what, final long amount,
            final long max) {
        this.originGui = originGui;
        this.slot = slot;
        this.what = what;
        this.amount = amount;
        this.max = max;
    }

    @Override
    public ItemStack getIcon() {
        return this.what.wrapForDisplayOrFilter();
    }

    @Override
    public long getAmount() {
        return this.amount;
    }

    @Override
    public long getMinAmount() {
        return 1;
    }

    @Override
    public long getMaxAmount() {
        return this.max;
    }

    @Override
    public void apply(final EntityPlayer player, final ContainerSetAmount from, final long amount) {
        // Read before switching: the open context belongs to the screen being left.
        if (!PacketSwitchGuis.reopen(player, from, this.originGui)) {
            return;
        }

        if (player.openContainer == from || this.slot >= player.openContainer.inventorySlots.size()) {
            return;
        }

        final Slot target = player.openContainer.inventorySlots.get(this.slot);
        if (target instanceof SlotFake && target.getHasStack()) {
            target.putStack(GenericStack.wrapInItemStack(this.what, amount));
        }
    }
}
