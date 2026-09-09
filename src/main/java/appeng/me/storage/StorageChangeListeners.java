/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.me.storage;


import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageChangeSource;
import appeng.api.storage.MEStorage;


/**
 * The listener list every {@link IStorageChangeSource} in this package keeps, so that none of them writes it
 * again. Small on purpose: one subscriber is the normal case (the network a storage is mounted on), and a
 * second only turns up where one network is mounted on another.
 */
public final class StorageChangeListeners implements IStorageChangeSource {

    private final List<Listener> listeners = new ArrayList<>(1);

    @Override
    public void addChangeListener(final Listener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeChangeListener(final Listener listener) {
        this.listeners.remove(listener);
    }

    public boolean isEmpty() {
        return this.listeners.isEmpty();
    }

    public void post(final AEKey what, final long delta) {
        if (delta == 0) {
            return;
        }

        // Indexed, because a listener is allowed to react by mounting or unmounting something.
        for (int i = 0; i < this.listeners.size(); i++) {
            this.listeners.get(i).onStorageChange(what, delta);
        }
    }

    /** Posts the difference between two counts of the same storage, taken at different times. */
    public void postDiff(final KeyCounter before, final KeyCounter after) {
        if (this.listeners.isEmpty()) {
            return;
        }

        for (final var entry : after) {
            this.post(entry.getKey(), entry.getLongValue() - before.get(entry.getKey()));
        }

        for (final var entry : before) {
            if (after.get(entry.getKey()) == 0) {
                this.post(entry.getKey(), -entry.getLongValue());
            }
        }
    }

    /** Posts everything {@code storage} holds as if it had just appeared, or vanished when {@code sign} is -1. */
    public void postContents(final MEStorage storage, final int sign) {
        if (this.listeners.isEmpty()) {
            return;
        }

        for (final var entry : storage.getAvailableStacks()) {
            this.post(entry.getKey(), sign * entry.getLongValue());
        }
    }

    public static boolean reports(final Object storage) {
        return storage instanceof IStorageChangeSource;
    }
}
