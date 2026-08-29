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
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotInaccessible;
import appeng.helpers.IAmountTarget;
import appeng.tile.inventory.AppEngInternalInventory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

import javax.annotation.Nullable;


/**
 * Types an exact amount into whatever the screen it was opened from asked for. The target is remembered
 * here rather than looked up again on the way back, so the amount reaches what the player pointed at even
 * if the screen behind has changed since.
 */
public class ContainerSetAmount extends AEBaseContainer {

    private final Slot slotDisplay;

    @Nullable
    private IAmountTarget target;

    @GuiSync(10)
    public long initialAmount;

    @GuiSync(11)
    public long maxAmount = Long.MAX_VALUE;

    public ContainerSetAmount(final InventoryPlayer ip, final Object host) {
        super(ip, host);

        this.slotDisplay = new SlotInaccessible(new AppEngInternalInventory(null, 1), 0, 34, 53);
        this.addSlotToContainer(this.slotDisplay);
    }

    public void setAmountTarget(final IAmountTarget target) {
        this.target = target;
        this.initialAmount = target.getAmount();
        this.maxAmount = target.getMaxAmount();
        this.slotDisplay.putStack(target.getIcon());
    }

    /**
     * Named apart from {@link #getTarget()}, which answers with the host this screen was opened on and is
     * what sends the player back to it.
     */
    @Nullable
    public IAmountTarget getAmountTarget() {
        return this.target;
    }
}
