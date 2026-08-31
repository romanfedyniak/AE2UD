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

package appeng.items.contents;


import appeng.api.upgrades.IUpgradeInventoryListener;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.util.Platform;
import net.minecraft.item.ItemStack;


public final class CellUpgrades extends StackUpgradeInventory {
    private final ItemStack is;

    public CellUpgrades(final ItemStack is, final int upgrades) {
        this(is, upgrades, null);
    }

    /**
     * @param listener told after every change, for a host that has to work something out from its cards.
     *                 It is handed this inventory, not the stack: {@link #getUpgradableItem()} answers with
     *                 a copy, so anything writing back to the item has to hold the real one itself.
     */
    public CellUpgrades(final ItemStack is, final int upgrades, final IUpgradeInventoryListener listener) {
        super(is, null, upgrades, listener);
        this.is = is;
        this.readFromNBT(Platform.openNbtData(is), "upgrades");
    }

    @Override
    protected void onContentsChanged(int slot) {
        this.writeToNBT(Platform.openNbtData(this.is), "upgrades");
        this.saveChanges();
    }
}