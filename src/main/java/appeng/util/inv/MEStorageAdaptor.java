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

package appeng.util.inv;


import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.util.InventoryAdaptor;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.item.ItemStack;

import java.util.Iterator;


/**
 * Presents an {@link MEStorage} through the item-only {@link InventoryAdaptor} surface, so code that moves
 * stacks between vanilla inventories can treat a network as one more of them.
 * <p>
 * Nothing in the mod calls it - the integration that did was removed in 1.8.8 - and it is kept for addons.
 */
public class MEStorageAdaptor extends InventoryAdaptor {

    private final MEStorage target;
    private final IActionSource src;
    private int maxSlots = 0;

    public MEStorageAdaptor(final MEStorage input, final IActionSource src) {
        this.target = input;
        this.src = src;
    }

    @Override
    public boolean hasSlots() {
        return true;
    }

    @Override
    public Iterator<ItemSlot> iterator() {
        return new MEStorageAdaptorIterator(this, this.getList());
    }

    private KeyCounter getList() {
        return this.target.getAvailableStacks();
    }

    @Override
    public ItemStack removeItems(final int amount, final ItemStack filter, final IInventoryDestination destination) {
        return this.doRemoveItems(amount, filter, destination, Actionable.MODULATE);
    }

    private ItemStack doRemoveItems(final int amount, final ItemStack filter, final IInventoryDestination destination, final Actionable type) {
        AEKey req = null;

        if (filter.isEmpty()) {
            final KeyCounter list = this.getList();
            req = list.getFirstKey(AEItemKey.class);
        } else {
            req = AEItemKey.of(filter);
        }

        if (req instanceof AEItemKey itemKey) {
            final long extracted = this.target.extract(itemKey, amount, type, this.src);
            if (extracted > 0) {
                return itemKey.toStack((int) extracted);
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack simulateRemove(final int amount, final ItemStack filter, final IInventoryDestination destination) {
        return this.doRemoveItems(amount, filter, destination, Actionable.SIMULATE);
    }

    @Override
    public ItemStack removeSimilarItems(final int amount, final ItemStack filter, final FuzzyMode fuzzyMode, final IInventoryDestination destination) {
        if (filter.isEmpty()) {
            return this.doRemoveItems(amount, ItemStack.EMPTY, destination, Actionable.MODULATE);
        }
        return this.doRemoveItemsFuzzy(amount, filter, destination, Actionable.MODULATE, fuzzyMode);
    }

    private ItemStack doRemoveItemsFuzzy(final int amount, final ItemStack filter, final IInventoryDestination destination, final Actionable type, final FuzzyMode fuzzyMode) {
        final AEItemKey reqFilter = AEItemKey.of(filter);
        if (reqFilter == null) {
            return ItemStack.EMPTY;
        }

        for (final Object2LongMap.Entry<AEKey> entry : ImmutableList.copyOf(this.getList().findFuzzy(reqFilter, fuzzyMode))) {
            if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey itemKey) {
                final long extracted = this.target.extract(itemKey, amount, type, this.src);
                if (extracted > 0) {
                    return itemKey.toStack((int) extracted);
                }
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack simulateSimilarRemove(final int amount, final ItemStack filter, final FuzzyMode fuzzyMode, final IInventoryDestination destination) {
        if (filter.isEmpty()) {
            return this.doRemoveItems(amount, ItemStack.EMPTY, destination, Actionable.SIMULATE);
        }
        return this.doRemoveItemsFuzzy(amount, filter, destination, Actionable.SIMULATE, fuzzyMode);
    }

    @Override
    public ItemStack addItems(final ItemStack toBeAdded) {
        final AEItemKey in = AEItemKey.of(toBeAdded);
        if (in == null) {
            return ItemStack.EMPTY;
        }

        final long inserted = this.target.insert(in, toBeAdded.getCount(), Actionable.MODULATE, this.src);
        final long remainder = toBeAdded.getCount() - inserted;
        return remainder > 0 ? in.toStack((int) remainder) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack simulateAdd(final ItemStack toBeSimulated) {
        final AEItemKey in = AEItemKey.of(toBeSimulated);
        if (in == null) {
            return ItemStack.EMPTY;
        }

        final long inserted = this.target.insert(in, toBeSimulated.getCount(), Actionable.SIMULATE, this.src);
        final long remainder = toBeSimulated.getCount() - inserted;
        return remainder > 0 ? in.toStack((int) remainder) : ItemStack.EMPTY;
    }

    @Override
    public boolean containsItems() {
        return !this.getList().isEmpty();
    }

    int getMaxSlots() {
        return this.maxSlots;
    }

    void setMaxSlots(final int maxSlots) {
        this.maxSlots = maxSlots;
    }

}
