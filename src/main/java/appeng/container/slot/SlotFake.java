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


import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.AEKeyFilter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;


public class SlotFake extends AppEngSlot implements IJEITargetSlot {

    public SlotFake(final IItemHandler inv, final int idx, final int x, final int y) {
        super(inv, idx, x, y);
    }

    @Override
    public ItemStack onTake(final EntityPlayer par1EntityPlayer, final ItemStack par2ItemStack) {
        return par2ItemStack;
    }

    @Override
    public ItemStack decrStackSize(final int par1) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isItemValid(final ItemStack par1ItemStack) {
        return false;
    }

    @Override
    public void putStack(ItemStack is) {
        if (!is.isEmpty()) {
            // Whichever way the stack got here - a click, a dragged ingredient, a recipe carried over from
            // a recipe screen - a slot is never left standing for something it cannot mean.
            if (!this.accepts(is)) {
                return;
            }
            is = is.copy();
        }
        super.putStack(is);
    }

    /**
     * Which keys this slot may stand for. Anything the network can hold unless the slot says otherwise,
     * since a filter is written in the same terms as the thing it filters.
     */
    public AEKeyFilter acceptedKeys() {
        return AEKeyFilter.all();
    }

    /** What a stack in a fake slot names: the key inside a wrapper, or the item itself where there is none. */
    private boolean accepts(final ItemStack is) {
        final GenericStack wrapped = GenericStack.unwrapItemStack(is);

        return this.acceptedKeys().matches(wrapped != null ? wrapped.what() : AEItemKey.of(is));
    }

    /**
     * Strips the quantity out of what a filter slot is about to store, so it holds an identity and nothing
     * else.
     * <p>
     * A wrapped non-item key keeps its amount inside the wrapper where {@code setCount} cannot reach it, so
     * it has to be re-wrapped bare rather than trimmed. Shared because two slot classes normalise this way
     * and only one of them was taught to unwrap - which is how a capacity-card filter came to read "1000mB"
     * and to answer the scroll wheel.
     */
    static ItemStack typeOnly(ItemStack is) {
        if (is.isEmpty()) {
            return is;
        }

        final GenericStack wrapped = GenericStack.unwrapItemStack(is);
        if (wrapped != null) {
            return wrapped.what().wrapForDisplayOrFilter();
        }

        is = is.copy();
        if (is.getCount() > 1) {
            is.setCount(1);
        } else if (is.getCount() < -1) {
            is.setCount(-1);
        }
        return is;
    }

    @Override
    public boolean canTakeStack(final EntityPlayer par1EntityPlayer) {
        return false;
    }

    @Override
    public boolean needAccept() {
        return this.getStack().isEmpty();
    }

}
