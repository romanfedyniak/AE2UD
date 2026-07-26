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


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import javax.annotation.Nullable;

import com.github.bsideup.jabel.Desugar;
import com.google.common.base.Preconditions;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;


/**
 * Manages all {@link MEStorage} mounted onto the network. Replaces {@code NetworkInventoryHandler}, which was
 * generic over a single storage channel; this one handles keys of any registered type at once, and this is the
 * single object every other cache/service on the network inserts into and extracts from.
 * <p/>
 * Consumed by {@code appeng.me.cache} (the storage service caches and mounts inventories through
 * {@link #mount(int, MEStorage)}/{@link #unmount(MEStorage)}); its public shape is frozen by the migration
 * contract and must not change without coordinating with that package.
 */
public class NetworkStorage implements MEStorage {
    private static final Comparator<Integer> PRIORITY_SORTER = (o1, o2) -> Integer.compare(o2, o1);

    // This flag prevents both concurrent modifications of the mounted storage while
    // they're being iterated, and recursive extract/insert/list operations.
    private boolean mountsInUse;

    private final NavigableMap<Integer, List<MEStorage>> priorityInventory;
    private final List<MEStorage> secondPassInventories = new ArrayList<>();

    // Queued mount/unmount operations that occurred while an insert/extract was ongoing.
    // Only non-null if something is queued.
    @Nullable
    private List<QueuedOperation> queuedOperations;

    public NetworkStorage() {
        this.priorityInventory = new TreeMap<>(PRIORITY_SORTER);
    }

    public void mount(final int priority, final MEStorage inventory) {
        if (this.mountsInUse) {
            if (this.queuedOperations == null) {
                this.queuedOperations = new ArrayList<>();
            }
            this.queuedOperations.add(new MountOperation(priority, inventory));
        } else {
            this.priorityInventory.computeIfAbsent(priority, k -> new ArrayList<>()).add(inventory);
        }
    }

    public void unmount(final MEStorage inventory) {
        if (this.mountsInUse) {
            if (this.queuedOperations == null) {
                this.queuedOperations = new ArrayList<>();
            }
            this.queuedOperations.add(new UnmountOperation(inventory));
        } else {
            final var prioIt = this.priorityInventory.entrySet().iterator();
            while (prioIt.hasNext()) {
                final var prioEntry = prioIt.next();

                final var inventories = prioEntry.getValue();
                if (inventories.remove(inventory) && inventories.isEmpty()) {
                    prioIt.remove();
                }
            }
        }
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable type, final IActionSource src) {
        if (this.mountsInUse) {
            return 0; // Prevent recursive use
        }

        long remaining = amount;

        this.mountsInUse = true;
        try {
            // Sticky Card pass (AE2UD-specific, no upstream equivalent - see CONTRACT.md §10, recovered from the
            // pre-migration NetworkInventoryHandler's stickyPriorityInventory). Sticky mounts get first crack at
            // any key they're "interested" in (same predicate as isPreferredStorageFor: whitelisted, or already
            // holding a matching stack), in priority order and across every priority level. Unlike an ordinary
            // preferred mount, if ANY sticky mount claims interest in `what`, insertion must not fall through to
            // non-sticky mounts afterwards - even if the sticky mount(s) can't hold all of `remaining`. The excess
            // is simply not inserted this call, exactly like the old sticky pass returning early.
            boolean stickyClaimed = false;
            for (final var invList : this.priorityInventory.values()) {
                for (final var inv : invList) {
                    if (this.isQueuedForRemoval(inv) || !isSticky(inv)) {
                        continue;
                    }

                    if (inv.isPreferredStorageFor(what, src)) {
                        stickyClaimed = true;
                        if (remaining > 0) {
                            remaining -= inv.insert(what, remaining, type, src);
                        }
                    }
                }
            }

            if (!stickyClaimed) {
                for (final var invList : this.priorityInventory.values()) {
                    this.secondPassInventories.clear();

                    // First give every inventory a chance to accept the item if it's preferred storage for it.
                    final var ii = invList.iterator();
                    while (ii.hasNext() && remaining > 0) {
                        final var inv = ii.next();

                        if (this.isQueuedForRemoval(inv) || isSticky(inv)) {
                            continue; // sticky mounts already had their exclusive pass above
                        }

                        if (inv.isPreferredStorageFor(what, src)) {
                            remaining -= inv.insert(what, remaining, type, src);
                        } else {
                            this.secondPassInventories.add(inv);
                        }
                    }

                    // Then give every remaining inventory a chance.
                    for (final var inv : this.secondPassInventories) {
                        if (remaining <= 0) {
                            break;
                        }

                        if (this.isQueuedForRemoval(inv)) {
                            continue;
                        }

                        remaining -= inv.insert(what, remaining, type, src);
                    }
                }
            }
        } finally {
            this.mountsInUse = false;
        }

        this.flushQueuedOperations();

        return amount - remaining;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (this.mountsInUse) {
            return 0; // Prevent recursive use
        }

        long extracted = 0L;

        this.mountsInUse = true;
        try {
            // Non-sticky mounts are drained first...
            for (final var invList : this.priorityInventory.descendingMap().values()) {
                final var ii = invList.iterator();
                while (ii.hasNext() && extracted < amount) {
                    final var inv = ii.next();

                    if (this.isQueuedForRemoval(inv) || isSticky(inv)) {
                        continue;
                    }

                    extracted += inv.extract(what, amount - extracted, mode, source);
                }
            }

            // ...Sticky Card mounts (AE2UD-specific, see CONTRACT.md §10) are only touched once nothing else could
            // supply the rest, mirroring the old NetworkInventoryHandler's separate stickyPriorityInventory pass,
            // which was likewise appended after the regular descending-priority extraction.
            if (extracted < amount) {
                for (final var invList : this.priorityInventory.descendingMap().values()) {
                    final var ii = invList.iterator();
                    while (ii.hasNext() && extracted < amount) {
                        final var inv = ii.next();

                        if (this.isQueuedForRemoval(inv) || !isSticky(inv)) {
                            continue;
                        }

                        extracted += inv.extract(what, amount - extracted, mode, source);
                    }
                }
            }
        } finally {
            this.mountsInUse = false;
        }

        this.flushQueuedOperations();

        return extracted;
    }

    /**
     * @return true if {@code inv} has the Sticky Card installed. Sticky is an AE2UD-specific mount option (see
     *         CONTRACT.md §10) with no upstream equivalent, so it's deliberately not part of {@link MEStorage}
     *         itself - only {@link MEInventoryHandler} (and its {@link DriveWatcher} subclass) can carry it.
     */
    private static boolean isSticky(final MEStorage inv) {
        return inv instanceof MEInventoryHandler handler && handler.isSticky();
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        if (this.mountsInUse) {
            return; // Prevent recursive use
        }

        this.mountsInUse = true;
        try {
            for (final var i : this.priorityInventory.values()) {
                for (final var j : i) {
                    j.getAvailableStacks(out);
                }
            }
        } finally {
            this.mountsInUse = false;
        }
    }

    @Override
    public ITextComponent getDescription() {
        return new TextComponentString("Network Storage");
    }

    private void flushQueuedOperations() {
        Preconditions.checkState(!this.mountsInUse);
        final var queuedOperations = this.queuedOperations;
        if (queuedOperations != null) {
            this.queuedOperations = null;
            for (final var op : queuedOperations) {
                if (op instanceof MountOperation mountOp) {
                    this.mount(mountOp.priority(), mountOp.storage());
                } else if (op instanceof UnmountOperation unmountOp) {
                    this.unmount(unmountOp.storage());
                } else {
                    throw new IllegalStateException("Unknown operation: " + op);
                }
            }
        }
    }

    private boolean isQueuedForRemoval(final MEStorage inv) {
        if (this.queuedOperations != null) {
            for (final var queuedOperation : this.queuedOperations) {
                if (queuedOperation instanceof UnmountOperation unmountOperation && unmountOperation.storage() == inv) {
                    return true;
                }
            }
        }
        return false;
    }

    private interface QueuedOperation {
    }

    @Desugar
    private record MountOperation(int priority, MEStorage storage) implements QueuedOperation {
    }

    @Desugar
    private record UnmountOperation(MEStorage storage) implements QueuedOperation {
    }
}
