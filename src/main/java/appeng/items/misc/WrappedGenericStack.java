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

package appeng.items.misc;


import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.items.AEBaseItem;


/**
 * Wraps a {@link GenericStack} in an {@link ItemStack}, so any non-item {@link AEKey} (fluids today, anything an
 * addon registers tomorrow) can travel through a vanilla {@link net.minecraft.inventory.Slot} or be shown to HEI.
 * This is the 1.12.2 implementation of the {@link GenericStack.Wrapper} SPI declared in {@code src/api}
 * (CONTRACT.md &sect;1.5 / &sect;8 item 3) and generalises what {@link appeng.fluids.items.FluidDummyItem} does for
 * fluids alone.
 * <p>
 * This item is a display/transport shim only:
 * <ul>
 * <li>it never stacks ({@code maxStackSize == 1});</li>
 * <li>it has no crafting recipe and cannot be obtained;</li>
 * <li>it is not added to any creative tab;</li>
 * <li>{@link #onUpdate(ItemStack, World, Entity, int, boolean)} sweeps it out of a player's inventory the moment it
 * ends up there, so a desync between the wrapped slot and the underlying key can never leave a player actually
 * holding one.</li>
 * </ul>
 */
public class WrappedGenericStack extends AEBaseItem implements GenericStack.Wrapper {

    public WrappedGenericStack() {
        this.setMaxStackSize(1);
    }

    @Override
    public ItemStack wrap(AEKey what, long amount) {
        Objects.requireNonNull(what, "what");

        final ItemStack stack = new ItemStack(this);
        final NBTTagCompound tag = new NBTTagCompound();
        GenericStack.writeTag(tag, new GenericStack(what, amount));
        stack.setTagCompound(tag);
        return stack;
    }

    @Override
    public boolean isWrapped(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == this;
    }

    @Override
    @Nullable
    public GenericStack unwrap(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != this || !stack.hasTagCompound()) {
            return null;
        }
        return GenericStack.readTag(stack.getTagCompound());
    }

    @Override
    public String getItemStackDisplayName(final ItemStack stack) {
        final GenericStack wrapped = this.unwrap(stack);
        if (wrapped != null) {
            return wrapped.what().getDisplayName().getFormattedText();
        }
        return super.getItemStackDisplayName(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines, final ITooltipFlag advancedTooltips) {
        final GenericStack wrapped = this.unwrap(stack);
        if (wrapped != null) {
            lines.add(wrapped.what().getDisplayName().getFormattedText() + ": "
                    + wrapped.what().formatAmount(wrapped.amount(), AmountFormat.FULL));
        }
    }

    @Override
    protected void getCheckedSubItems(final CreativeTabs creativeTab, final NonNullList<ItemStack> itemStacks) {
        // Never obtainable through creative search or HEI cheat mode.
    }

    /**
     * Guards against a desync (dropped packet, mod-added inventory move, ...) leaving a player actually holding
     * a placeholder item: every tick, if this item is found in a player's inventory, it is removed on the spot.
     * Uses the same defensive by-identity slot scan {@code ItemEncodedPattern.clearPattern} uses.
     */
    @Override
    public void onUpdate(final ItemStack stack, final World world, final Entity entity, final int itemSlot, final boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);

        if (world.isRemote || !(entity instanceof EntityPlayer)) {
            return;
        }

        final InventoryPlayer inv = ((EntityPlayer) entity).inventory;
        for (int s = 0; s < inv.getSizeInventory(); s++) {
            if (inv.getStackInSlot(s) == stack) {
                inv.setInventorySlotContents(s, ItemStack.EMPTY);
                break;
            }
        }
    }
}
