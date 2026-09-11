/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
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


import java.util.UUID;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;


/**
 * What one storage cell holds, with the totals kept beside it.
 * <p/>
 * Every {@link BasicCellInventory} opened on the same cell shares one of these through {@link CellContentsStore},
 * so what one of them puts in the others see. A detached one belongs to a single inventory: a cell nothing has
 * written to yet, or any cell on a side without a store.
 * <p/>
 * A summary is what the client knows of a cell whose contents live on the server: the few largest entries, and
 * totals that count everything. It cannot change.
 */
public final class CellContents {

    @Nullable
    private UUID id;
    @Nullable
    private CellContentsStore store;
    private final boolean summary;

    private final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
    private final Object2LongMap<AEKey> readOnlyAmounts = Object2LongMaps.unmodifiable(this.amounts);
    private final Object2LongOpenHashMap<AEKeyType> amountsByType = new Object2LongOpenHashMap<>();
    private int types;
    private long total;

    // Kept by the store, on the server thread.
    boolean dirty;
    long lastAccess;

    CellContents(@Nullable final UUID id, @Nullable final CellContentsStore store) {
        this.id = id;
        this.store = store;
        this.summary = false;
    }

    private CellContents(final boolean summary) {
        this.summary = summary;
    }

    public static CellContents detached() {
        return new CellContents(null, null);
    }

    /**
     * @param shown   the entries the client was sent, which need not be all of them
     * @param types   how many types the cell really holds
     * @param byType  how much of each key type it really holds
     */
    public static CellContents summary(final Object2LongMap<AEKey> shown, final int types,
            final Object2LongMap<AEKeyType> byType) {
        final CellContents contents = new CellContents(true);
        contents.amounts.putAll(shown);
        contents.types = types;
        for (final Object2LongMap.Entry<AEKeyType> entry : byType.object2LongEntrySet()) {
            contents.amountsByType.put(entry.getKey(), entry.getLongValue());
            contents.total += entry.getLongValue();
        }
        return contents;
    }

    @Nullable
    public UUID getId() {
        return this.id;
    }

    public boolean isAttached() {
        return this.store != null;
    }

    public boolean isSummary() {
        return this.summary;
    }

    /** Never mutate: go through {@link #set}. */
    public Object2LongMap<AEKey> amounts() {
        return this.readOnlyAmounts;
    }

    public long get(final AEKey key) {
        return this.amounts.getLong(key);
    }

    public int getTypes() {
        return this.summary ? this.types : this.amounts.size();
    }

    public long getTotal() {
        return this.total;
    }

    public long getTotal(final AEKeyType type) {
        return this.amountsByType.getLong(type);
    }

    public boolean isEmpty() {
        return this.getTypes() == 0;
    }

    /** Sets how much of {@code key} is held; zero or less removes it. Does not mark anything dirty. */
    public void set(final AEKey key, final long amount) {
        if (this.summary) {
            throw new IllegalStateException("The client's summary of a cell cannot change");
        }

        final long previous = amount > 0 ? this.amounts.put(key, amount) : this.amounts.removeLong(key);
        final long delta = Math.max(amount, 0) - previous;
        if (delta == 0) {
            return;
        }

        this.total += delta;
        final AEKeyType type = key.getType();
        final long byType = this.amountsByType.getLong(type) + delta;
        if (byType > 0) {
            this.amountsByType.put(type, byType);
        } else {
            this.amountsByType.removeLong(type);
        }
    }

    public void add(final AEKey key, final long amount) {
        this.set(key, this.amounts.getLong(key) + amount);
    }

    /** Moves everything {@code other} holds into this one, and leaves {@code other} empty. */
    public void takeAll(final CellContents other) {
        if (other == this) {
            return;
        }
        for (final Object2LongMap.Entry<AEKey> entry : other.amounts.object2LongEntrySet()) {
            this.add(entry.getKey(), entry.getLongValue());
        }
        other.amounts.clear();
        other.amountsByType.clear();
        other.total = 0;
    }

    /** Tells the store this cell has to be written again. Nothing happens to a detached one. */
    public void markDirty() {
        if (this.store != null) {
            this.store.changed(this);
        }
    }

    void attach(final UUID id, final CellContentsStore store) {
        this.id = id;
        this.store = store;
    }
}
