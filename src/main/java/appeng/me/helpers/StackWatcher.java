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

package appeng.me.helpers;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import appeng.api.networking.storage.IStackWatcher;
import appeng.api.stacks.AEKey;

/**
 * Maintain my interests, and a global watch list, they should always be fully synchronized.
 * <p>
 * Generic over the host type so it can back both {@link appeng.api.networking.storage.IStorageWatcherNode}
 * and {@code ICraftingWatcherHost}.
 */
public class StackWatcher<T> implements IStackWatcher {

    private final InterestManager<StackWatcher<T>> interestManager;
    private final T myHost;
    private final Set<AEKey> myInterests = new HashSet<>();
    private boolean destroyed = false;

    public StackWatcher(InterestManager<StackWatcher<T>> interestManager, T host) {
        this.interestManager = interestManager;
        this.myHost = host;
    }

    public T getHost() {
        return this.myHost;
    }

    @Override
    public void setWatchAll(boolean watchAll) {
        if (!destroyed) {
            interestManager.setWatchAll(watchAll, this);
        }
    }

    @Override
    public void add(AEKey e) {
        if (!destroyed && this.myInterests.add(e)) {
            interestManager.put(e, this);
        }
    }

    @Override
    public void remove(AEKey o) {
        if (!destroyed && this.myInterests.remove(o)) {
            interestManager.remove(o, this);
        }
    }

    @Override
    public void reset() {
        setWatchAll(false);

        final Iterator<AEKey> i = this.myInterests.iterator();

        while (i.hasNext()) {
            interestManager.remove(i.next(), this);
            i.remove();
        }
    }

    /**
     * Call this when the watcher is not going to be used anymore, to reset it and disable it forever. It's
     * important that we disable the watcher, since some hosts (e.g. level emitter) might still hold a
     * reference to it and try to modify it later, which could lead to invalid state and potentially crashes
     * down the line.
     */
    public void destroy() {
        reset();
        destroyed = true;
    }
}
