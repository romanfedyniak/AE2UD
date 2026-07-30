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


import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;


/**
 * Wraps a single {@link StorageCell} mounted in a drive, blinking the cell's status light whenever its
 * {@link CellState} changes.
 * <p/>
 * Simplified from the old generic {@code DriveWatcher<T>}: it no longer needs a reference to the drive tile,
 * an {@code ICellHandler} or an {@code IActionSource} of its own to post network changes - the network's storage
 * service now periodically diffs its own cached amounts and notifies watchers, so all that is left here is the
 * blink callback, supplied by whoever mounts this (the drive tile, out of this package's scope).
 * <p/>
 * Also restores the Sticky Card flag (AE2UD-specific, see CONTRACT.md §10): {@link BasicCellInventory} reads the
 * Sticky Card from its own upgrade slots, and this watcher propagates it into
 * {@link MEInventoryHandler#setSticky(boolean)} so {@link NetworkStorage#insert} can honor it. This mirrors what
 * the old {@code BasicCellInventoryHandler} constructor used to do.
 */
public class DriveWatcher extends MEInventoryHandler {

    private CellState oldStatus;
    private final Runnable activityCallback;

    public DriveWatcher(final StorageCell i, final Runnable activityCallback) {
        super(i);
        this.activityCallback = activityCallback;
        this.oldStatus = this.getStatus();

        if (i instanceof BasicCellInventory basicCell && basicCell.isSticky()) {
            this.setSticky(true);
        }
    }

    public CellState getStatus() {
        return this.getCell().getStatus();
    }

    public StorageCell getCell() {
        return (StorageCell) this.getDelegate();
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        final long inserted = super.insert(what, amount, mode, source);

        if (mode == Actionable.MODULATE && inserted > 0) {
            final CellState newStatus = this.getStatus();

            if (newStatus != this.oldStatus) {
                this.activityCallback.run();
                this.oldStatus = newStatus;
            }
        }

        return inserted;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        final long extracted = super.extract(what, amount, mode, source);

        if (mode == Actionable.MODULATE && extracted > 0) {
            final CellState newStatus = this.getStatus();

            if (newStatus != this.oldStatus) {
                this.activityCallback.run();
                this.oldStatus = newStatus;
            }
        }

        return extracted;
    }
}
