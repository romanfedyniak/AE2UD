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

package appeng.container.slot;


import appeng.api.stacks.GenericStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;


public class SlotFakeTypeOnly extends SlotFake {

    public SlotFakeTypeOnly(final IItemHandler inv, final int idx, final int x, final int y) {
        super(inv, idx, x, y);
    }

    @Override
    public void putStack(ItemStack is) {
        if (!is.isEmpty()) {
            // A wrapped non-item key carries its amount inside the wrapper, where setCount() cannot reach
            // it: normalising the ItemStack alone left a filter reading "Water: 1B", as though the slot
            // were storing a bucket's worth. Re-wrap the bare key instead, which is amount-free by
            // definition (AEKey.wrapForDisplayOrFilter), so the tooltip states an identity and no quantity.
            final GenericStack wrapped = GenericStack.unwrapItemStack(is);
            if (wrapped != null) {
                super.putStack(wrapped.what().wrapForDisplayOrFilter());
                return;
            }

            is = is.copy();
            if (is.getCount() > 1) {
                is.setCount(1);
            } else if (is.getCount() < -1) {
                is.setCount(-1);
            }
        }

        super.putStack(is);
    }
}
