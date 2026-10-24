/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;

/**
 * The non-item ingredients of a pushed pattern that the machine did not take in full - two fluids for one tank,
 * say, each of which fits alone. They wait here and go in later, as an item that did not fit waits in the send
 * list, instead of being lost.
 */
public final class PendingExtras {

    private final List<GenericStack> stacks = new ArrayList<>();

    public boolean isEmpty() {
        return this.stacks.isEmpty();
    }

    public void addAll(final List<GenericStack> leftovers) {
        for (final GenericStack stack : leftovers) {
            if (stack.amount() > 0) {
                this.stacks.add(stack);
            }
        }
    }

    /** Into the block on that side, through the export strategy of each key's type. */
    public void push(final TileEntity self, final EnumFacing side) {
        if (this.isEmpty()) {
            return;
        }
        final List<StackExportStrategy> export = DualityInterface.getExportStrategies(self, side);
        final ListIterator<GenericStack> i = this.stacks.listIterator();
        while (i.hasNext()) {
            final GenericStack stack = i.next();
            this.moved(i, stack, DualityInterface.pushThrough(export, stack, Actionable.MODULATE));
        }
    }

    /** Into a neighbouring network's storage. */
    public void insert(final MEStorage inv, final IActionSource src) {
        final ListIterator<GenericStack> i = this.stacks.listIterator();
        while (i.hasNext()) {
            final GenericStack stack = i.next();
            this.moved(i, stack, inv.insert(stack.what(), stack.amount(), Actionable.MODULATE, src));
        }
    }

    private void moved(final ListIterator<GenericStack> i, final GenericStack stack, final long amount) {
        if (amount >= stack.amount()) {
            i.remove();
        } else if (amount > 0) {
            i.set(new GenericStack(stack.what(), stack.amount() - amount));
        }
    }

    public void writeToNBT(final NBTTagCompound data, final String name) {
        if (this.isEmpty()) {
            return;
        }
        final NBTTagList list = new NBTTagList();
        for (final GenericStack stack : this.stacks) {
            final NBTTagCompound tag = new NBTTagCompound();
            GenericStack.writeTag(tag, stack);
            list.appendTag(tag);
        }
        data.setTag(name, list);
    }

    public void readFromNBT(final NBTTagCompound data, final String name) {
        this.stacks.clear();
        final NBTTagList list = data.getTagList(name, Constants.NBT.TAG_COMPOUND);
        for (int x = 0; x < list.tagCount(); x++) {
            final GenericStack stack = GenericStack.readTag(list.getCompoundTagAt(x));
            if (stack != null) {
                this.stacks.add(stack);
            }
        }
    }
}
