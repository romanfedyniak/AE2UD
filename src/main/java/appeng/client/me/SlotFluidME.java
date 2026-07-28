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

package appeng.client.me;


import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.container.interfaces.ISpecialSlotIngredient;
import appeng.container.me.GridInventoryEntry;
import appeng.fluids.container.slots.IMEFluidSlot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;


/**
 * @author BrockWS
 * @version rv6 - 22/05/2018
 * @since rv6 22/05/2018
 *
 * NOTE for wave 5: {@code appeng.fluids.container.slots.IMEFluidSlot} still declares
 * {@code IAEFluidStack getAEFluidStack()} - a deleted api type (CONTRACT.md §4.1) - because
 * {@code appeng.fluids} is wave 5's whole package and untouched so far. This class is written against
 * what that interface must become: {@code GenericStack getGenericStack()}, mirroring the exact rename
 * {@code appeng.util.inv.ItemSlot} already got in wave 1a ({@code getAEItemStack()} -> {@code
 * getGenericStack()}, CONTRACT.md §9 wave 1a). Wave 5 must apply the same rename to {@code IMEFluidSlot}
 * and to every other implementor/caller in {@code appeng.fluids}; nothing else about the interface
 * changes.
 */
public class SlotFluidME extends SlotItemHandler implements IMEFluidSlot, ISpecialSlotIngredient {

    private final InternalFluidSlotME slot;

    public SlotFluidME(InternalFluidSlotME slot) {
        super(null, 0, slot.getxPosition(), slot.getyPosition());
        this.slot = slot;
    }

    @Override
    public GenericStack getGenericStack() {
        if (this.slot.hasPower()) {
            final GridInventoryEntry entry = this.slot.getEntry();
            return entry == null ? null : new GenericStack(entry.getWhat(), entry.getStoredAmount());
        }
        return null;
    }

    @Override
    public boolean isItemValid(final ItemStack par1ItemStack) {
        return false;
    }

    @Nonnull
    @Override
    public ItemStack getStack() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean getHasStack() {
        if (this.slot.hasPower()) {
            return this.getGenericStack() != null;
        }
        return false;
    }

    @Override
    public void putStack(final ItemStack par1ItemStack) {

    }

    @Override
    public int getSlotStackLimit() {
        return 0;
    }

    @Nonnull
    @Override
    public ItemStack decrStackSize(final int par1) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isHere(final IInventory inv, final int slotIn) {
        return false;
    }

    @Override
    public boolean canTakeStack(final EntityPlayer par1EntityPlayer) {
        return false;
    }

    @Nullable
    @Override
    public Object getIngredient() {
        final GenericStack stack = this.getGenericStack();
        if (stack == null || !(stack.what() instanceof AEFluidKey fluidKey)) {
            return null;
        }
        return fluidKey.toStack((int) Math.min(stack.amount(), Integer.MAX_VALUE));
    }

}
