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


import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import appeng.api.storage.IStorageChangeSource;
import appeng.api.storage.MEStorage;


public final class DelegatingMEInventoryTest {

    /** A storage that reports its own changes and nothing else. */
    private static final class ReportingStorage implements MEStorage, IStorageChangeSource {
        final StorageChangeListeners listeners = new StorageChangeListeners();

        @Override
        public void addChangeListener(final Listener listener) {
            this.listeners.addChangeListener(listener);
        }

        @Override
        public void removeChangeListener(final Listener listener) {
            this.listeners.removeChangeListener(listener);
        }
    }

    /**
     * A storage bus moved from one network to another - which happens to every bus on a world load, as the
     * grid is merged together - lets go of the first network and is picked up by the second. It used to stay
     * subscribed to what it wraps after the first let go, and then subscribe again, so every change underneath
     * reached the second network twice.
     */
    @Test
    public void aWrapperRemountedCountsEachChangeOnce() {
        final ReportingStorage underneath = new ReportingStorage();
        final DelegatingMEInventory wrapper = new DelegatingMEInventory(underneath);

        final IStorageChangeSource.Listener first = (what, delta) -> { };
        wrapper.addChangeListener(first);
        wrapper.removeChangeListener(first);

        final AtomicLong counted = new AtomicLong();
        wrapper.addChangeListener((what, delta) -> counted.addAndGet(delta));

        underneath.listeners.post(null, 1);

        assertThat(counted.get(), is(1L));
    }
}
