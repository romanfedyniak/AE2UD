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
import appeng.core.sync.GuiBridge;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.util.helpers.ItemHandlerUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;


/**
 * A slot of an inventory that is not the origin screen's own: the interface configuration terminal edits
 * interfaces elsewhere on the network.
 * <p>
 * That inventory belongs to the interface and outlives both screens, so here the write comes first. The
 * terminal addresses interfaces by an id it mints per session, and going back to it builds a fresh
 * container which hands the same interfaces different ids - by then there would be nothing left to look
 * the slot up by.
 */
public class InventoryAmountTarget implements IAmountTarget {

    private final GuiBridge originGui;
    private final IItemHandler inventory;
    private final int slot;
    private final AEKey what;
    private final long amount;
    private final long max;

    public InventoryAmountTarget(final GuiBridge originGui, final IItemHandler inventory, final int slot,
            final AEKey what, final long amount, final long max) {
        this.originGui = originGui;
        this.inventory = inventory;
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
        if (this.slot < this.inventory.getSlots()) {
            ItemHandlerUtil.setStackInSlot(this.inventory, this.slot, GenericStack.wrapInItemStack(this.what, amount));
        }

        PacketSwitchGuis.reopen(player, from, this.originGui);
    }
}
