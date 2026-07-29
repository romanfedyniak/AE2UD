/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.container.slot;


import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.misc.TileCellWorkbench;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.function.Supplier;


/**
 * A cell workbench partition slot, which only accepts what the installed cell can actually store.
 * <p>
 * Config inventories became type-agnostic so that a fluid could be put in a fluid filter; the side effect
 * was that a fluid could also be put into an <em>item</em> cell's partition, where it is not merely useless
 * but harmful. The partition would then list a key the cell can never hold, nothing would match it, and the
 * cell would quietly stop accepting anything at all - with no error, and no visible difference from a cell
 * that simply refuses to fill.
 * <p>
 * The check lives here rather than in the inventory because only the workbench knows which cell is in the
 * slot; the same inventory class serves filters that have no type restriction whatsoever.
 */
public class SlotCellWorkbenchConfig extends SlotFakeTypeOnly {

    /**
     * Resolved on use, not on construction. {@code ContainerUpgradeable}'s constructor calls
     * {@code setupConfig()} - which builds these slots - before {@code ContainerCellWorkbench} has assigned
     * its own field, so anything captured here would be null.
     */
    private final Supplier<TileCellWorkbench> workBench;

    public SlotCellWorkbenchConfig(final Supplier<TileCellWorkbench> workBench, final IItemHandler inv, final int idx, final int x, final int y) {
        super(inv, idx, x, y);
        this.workBench = workBench;
    }

    @Override
    public void putStack(ItemStack is) {
        if (!is.isEmpty() && !this.acceptsKeyOf(is)) {
            return;
        }

        super.putStack(is);
    }

    private boolean acceptsKeyOf(final ItemStack is) {
        final TileCellWorkbench te = this.workBench.get();
        if (te == null) {
            return true;
        }

        final AEKeyType allowed = te.getCellKeyType();
        if (allowed == null) {
            // No cell in the workbench: there is nothing to partition, so nothing to check against either.
            return true;
        }

        final GenericStack stack = AppEngInternalAEInventory.toGenericStack(is);
        return stack == null || stack.what().getType() == allowed;
    }
}
