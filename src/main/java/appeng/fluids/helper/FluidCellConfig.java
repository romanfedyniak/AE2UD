/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.fluids.helper;


import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.items.contents.CellConfig;
import appeng.tile.inventory.AppEngInternalAEInventory;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 * A fluid cell's own partition, stored on the cell item.
 * <p>
 * Its job is to keep item keys out of a fluid cell's partition, and to accept the several shapes a fluid can
 * arrive in - the generic {@code WrappedGenericStack} placeholder, the older {@code FluidDummyItem}, or a real
 * container such as a bucket - normalising them all to one.
 * <p>
 * It used to normalise specifically to {@code FluidDummyItem}, deciding what it was looking at by asking
 * {@code FluidUtil.getFluidContained}. That answers null for a placeholder, which is not a real container, so
 * once filters started carrying the generic wrapper this stored nothing at all: the cell's partition came back
 * empty, and {@code TileCellWorkbench}'s copy-back then wiped the slot the player had just filled. Nothing
 * reported an error - the fluid simply would not go in.
 */
public class FluidCellConfig extends CellConfig {

    public FluidCellConfig(ItemStack is) {
        super(is);
    }

    /**
     * @return the canonical placeholder for the fluid this stack stands for, or empty if it does not stand
     *         for a fluid at all. Amount-free, because a partition is a set of identities.
     */
    @Nonnull
    private static ItemStack asFluidFilter(@Nullable final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final GenericStack resolved = AppEngInternalAEInventory.toGenericStack(stack);
        if (resolved == null || resolved.what().getType() != AEKeyType.fluids()) {
            return ItemStack.EMPTY;
        }

        return resolved.what().wrapForDisplayOrFilter();
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        final ItemStack filter = asFluidFilter(stack);
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

        final ItemStack filter = asFluidFilter(stack);
        if (!filter.isEmpty()) {
            super.setStackInSlot(slot, filter);
        }
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !asFluidFilter(stack).isEmpty();
    }
}
