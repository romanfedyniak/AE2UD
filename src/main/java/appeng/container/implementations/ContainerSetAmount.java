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


import appeng.api.stacks.AEKey;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotInaccessible;
import appeng.core.sync.GuiBridge;
import appeng.tile.inventory.AppEngInternalInventory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;


/**
 * Types an exact amount into one fake slot of the screen it was opened from. The key is remembered here
 * rather than re-read on the way back, so the amount is applied to what the player middle-clicked even if
 * the slot has changed since.
 */
public class ContainerSetAmount extends AEBaseContainer {

    private final Slot slotDisplay;

    private int originSlot = -1;
    /**
     * The inventory to write into, for an origin screen whose slots are not this container's.
     * <p>
     * The interface configuration terminal edits interfaces elsewhere on the network and addresses them by
     * an id its container mints per session, so returning to it builds a new container that hands the same
     * interfaces different ids. The inventory itself is owned by the interface and outlives both screens,
     * which makes it the only stable way to name the target.
     */
    @Nullable
    private IItemHandler originInventory;
    @Nullable
    private AEKey what;
    @Nullable
    private GuiBridge originGui;

    @GuiSync(10)
    public long initialAmount;

    @GuiSync(11)
    public long maxAmount = Long.MAX_VALUE;

    public ContainerSetAmount(final InventoryPlayer ip, final Object host) {
        super(ip, host);

        this.slotDisplay = new SlotInaccessible(new AppEngInternalInventory(null, 1), 0, 34, 53);
        this.addSlotToContainer(this.slotDisplay);
    }

    public void setOrigin(final GuiBridge originGui, final int slot, final AEKey what, final long amount, final long max) {
        this.setOrigin(originGui, slot, null, what, amount, max);
    }

    public void setOrigin(final GuiBridge originGui, final int slot, @Nullable final IItemHandler inventory, final AEKey what, final long amount, final long max) {
        this.originGui = originGui;
        this.originSlot = slot;
        this.originInventory = inventory;
        this.what = what;
        this.initialAmount = amount;
        this.maxAmount = max;
        this.slotDisplay.putStack(what.wrapForDisplayOrFilter());
    }

    public int getOriginSlot() {
        return this.originSlot;
    }

    @Nullable
    public IItemHandler getOriginInventory() {
        return this.originInventory;
    }

    @Nullable
    public AEKey getWhat() {
        return this.what;
    }

    @Nullable
    public GuiBridge getOriginGui() {
        return this.originGui;
    }
}
