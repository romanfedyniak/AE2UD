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


import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;


/**
 * A cell's own partition, stored on the cell item.
 * <p>
 * It knows which key types the cell can actually hold, and keeps everything else out. That check lives here
 * rather than on the cell item because this is the only gate every write passes through - the workbench
 * screen, the copy-back from the workbench, and an addon writing a partition programmatically.
 * <p>
 * It also accepts the several shapes a key can arrive in - the generic {@code WrappedGenericStack}
 * placeholder, or a real container such as a bucket - normalising them all to one. Item keys normalise to
 * themselves ({@code AEItemKey.wrapForDisplayOrFilter} returns the plain stack), so this costs an item cell
 * nothing.
 */
public class CellConfig extends AppEngInternalInventory {

    private final ItemStack is;
    // A Collection rather than a Set so that both a one-type cell (Collections.singleton) and a cell that
    // stores anything (AEKeyTypes.getAll(), the registry's own live collection) pass without a copy.
    private final Collection<AEKeyType> supportedTypes;

    public CellConfig(final ItemStack is, final Collection<AEKeyType> supportedTypes) {
        super(null, 63);
        this.is = is;
        this.supportedTypes = supportedTypes;
        this.readFromNBT(Platform.openNbtData(is), "list");
    }

    /**
     * @return the canonical placeholder for the key this stack stands for, or empty if it stands for nothing
     *         this cell can hold. Amount-free, because a partition is a set of identities.
     */
    @Nonnull
    private ItemStack asFilter(@Nullable final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final GenericStack resolved = AppEngInternalAEInventory.toGenericStack(stack);
        if (resolved == null || !this.supportedTypes.contains(resolved.what().getType())) {
            return ItemStack.EMPTY;
        }

        return resolved.what().wrapForDisplayOrFilter();
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        final ItemStack filter = asFilter(stack);
        if (filter.isEmpty()) {
            return stack;
        }

        super.insertItem(slot, filter, simulate);
        // The filter is a marker, not a transfer: whatever was offered stays with the player.
        return stack;
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            super.setStackInSlot(slot, ItemStack.EMPTY);
            return;
        }

        final ItemStack filter = asFilter(stack);
        if (!filter.isEmpty()) {
            super.setStackInSlot(slot, filter);
        }
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !asFilter(stack).isEmpty();
    }

    @Override
    protected void onContentsChanged(int slot) {
        this.writeToNBT(Platform.openNbtData(this.is), "list");
    }
}
