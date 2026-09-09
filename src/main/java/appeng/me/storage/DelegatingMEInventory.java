/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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


import java.util.Objects;

import net.minecraft.util.text.ITextComponent;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageChangeSource;
import appeng.api.storage.MEStorage;


/**
 * Convenient base class for wrapping another {@link MEStorage} and forwarding <strong>all</strong> methods to it.
 * <p/>
 * Replaces {@code MEPassThrough}: unlike that class, it is not generic and it does not itself implement any
 * listener/priority machinery - that is layered on top by {@link MEInventoryHandler}.
 */
public class DelegatingMEInventory implements MEStorage, IStorageChangeSource {
    private MEStorage delegate;

    private final StorageChangeListeners listeners = new StorageChangeListeners();
    /** Held as a field rather than written twice, because unsubscribing needs the same object back. */
    private final Listener forwarder = this::report;

    public DelegatingMEInventory(final MEStorage delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    protected MEStorage getDelegate() {
        return this.delegate;
    }

    protected void setDelegate(final MEStorage delegate) {
        this.listenToDelegate(false);
        this.delegate = delegate;
        this.listenToDelegate(true);
    }

    @Override
    public void addChangeListener(final Listener listener) {
        final boolean first = this.listeners.isEmpty();
        this.listeners.addChangeListener(listener);

        if (first) {
            this.listenToDelegate(true);
        }
    }

    @Override
    public void removeChangeListener(final Listener listener) {
        this.listeners.removeChangeListener(listener);

        if (this.listeners.isEmpty()) {
            this.listenToDelegate(false);
        }
    }

    /**
     * Whether whatever is underneath says when it changes. A wrapper that has one of those below it must stay
     * quiet about its own insertions and extractions, or every one of them would be counted twice.
     */
    protected boolean delegateReportsChanges() {
        return this.delegate instanceof IStorageChangeSource source && source.reportsChanges();
    }

    /** Nothing of its own: a plain wrapper is worth listening to only for what it passes on. */
    @Override
    public boolean reportsChanges() {
        return this.delegateReportsChanges();
    }

    /** Passes a change on to whoever is listening to this wrapper. */
    protected void report(final AEKey what, final long delta) {
        this.listeners.post(what, delta);
    }

    private void listenToDelegate(final boolean listen) {
        if (this.listeners.isEmpty() || !(this.delegate instanceof IStorageChangeSource source)) {
            return;
        }

        if (listen) {
            source.addChangeListener(this.forwarder);
        } else {
            source.removeChangeListener(this.forwarder);
        }
    }

    @Override
    public boolean isPreferredStorageFor(final AEKey input, final IActionSource source) {
        return this.getDelegate().isPreferredStorageFor(input, source);
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        return this.getDelegate().insert(what, amount, mode, source);
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        return this.getDelegate().extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        this.getDelegate().getAvailableStacks(out);
    }

    @Override
    public KeyCounter getAvailableStacks() {
        return this.getDelegate().getAvailableStacks();
    }

    @Override
    public ITextComponent getDescription() {
        return this.getDelegate().getDescription();
    }
}
