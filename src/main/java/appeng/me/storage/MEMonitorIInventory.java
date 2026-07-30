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

package appeng.me.storage;


import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import appeng.api.config.Actionable;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.util.InventoryAdaptor;
import appeng.util.inv.ItemSlot;


/**
 * Adapts an adjacent, non-ME item inventory (via {@link InventoryAdaptor}) so it behaves like an
 * {@link MEStorage}. Used by storage buses attached to a chest, drawer, etc.
 * <p/>
 * There is no 1.20+ equivalent to mirror: AE2-original's replacement ({@code ExternalStorageFacade}) is built on
 * NeoForge's unified {@code ResourceHandler}/{@code Transaction} transfer API, which does not exist in 1.12.2.
 * This keeps the pre-migration approach - {@link InventoryAdaptor} plus periodic re-scanning via
 * {@link ITickingMonitor} - and only replaces {@code IAEItemStack}/{@code IItemList} with {@link AEItemKey}/
 * {@link KeyCounter}. The per-listener notification that {@code IMEMonitor} used to do is gone; the network's
 * storage service diffs its own cached amounts instead.
 */
public class MEMonitorIInventory implements MEStorage, ITickingMonitor {

    private final InventoryAdaptor adaptor;
    private KeyCounter cache = new KeyCounter();
    private StorageFilter mode = StorageFilter.EXTRACTABLE_ONLY;

    public MEMonitorIInventory(final InventoryAdaptor adaptor) {
        this.adaptor = adaptor;
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable type, final IActionSource source) {
        if (!(what instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }

        final int requested = (int) Math.min(amount, Integer.MAX_VALUE);
        final ItemStack toInsert = itemKey.toStack(requested);

        final ItemStack remainder = type == Actionable.SIMULATE
                ? this.adaptor.simulateAdd(toInsert)
                : this.adaptor.addItems(toInsert);

        final int notInserted = remainder.isEmpty() ? 0 : remainder.getCount();
        final long inserted = requested - notInserted;

        if (inserted > 0 && type == Actionable.MODULATE) {
            this.cache.add(itemKey, inserted);
        }

        return inserted;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable type, final IActionSource source) {
        if (!(what instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }

        final int requested = (int) Math.min(amount, Integer.MAX_VALUE);
        final ItemStack filter = itemKey.toStack();

        final ItemStack extracted = type == Actionable.SIMULATE
                ? this.adaptor.simulateRemove(requested, filter, null)
                : this.adaptor.removeItems(requested, filter, null);

        if (extracted.isEmpty()) {
            return 0;
        }

        if (type == Actionable.MODULATE) {
            this.cache.remove(itemKey, extracted.getCount());
        }

        return extracted.getCount();
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        out.addAll(this.cache);
    }

    @Override
    public TickRateModulation onTick() {
        final KeyCounter next = new KeyCounter();

        for (final ItemSlot slot : this.adaptor) {
            if (this.mode == StorageFilter.EXTRACTABLE_ONLY && !slot.isExtractable()) {
                continue;
            }
            final GenericStack stack = slot.getGenericStack();
            if (stack != null) {
                next.add(stack.what(), stack.amount());
            }
        }

        final boolean changed = this.hasChanged(next);
        this.cache = next;

        return changed ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    private boolean hasChanged(final KeyCounter next) {
        for (final var entry : next) {
            if (this.cache.get(entry.getKey()) != entry.getLongValue()) {
                return true;
            }
        }
        for (final var entry : this.cache) {
            if (next.get(entry.getKey()) != entry.getLongValue()) {
                return true;
            }
        }
        return false;
    }

    public void setMode(final StorageFilter mode) {
        this.mode = mode;
    }

    @Override
    public ITextComponent getDescription() {
        return new TextComponentString("Inventory");
    }
}
