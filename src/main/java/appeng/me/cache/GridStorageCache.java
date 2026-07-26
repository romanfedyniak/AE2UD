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

package appeng.me.cache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.InterestManager;
import appeng.me.helpers.StackWatcher;
import appeng.me.storage.NetworkStorage;

/**
 * The network's storage cache. Replaces the old {@code IStorageGrid} implementation.
 * <p>
 * Mirrors {@code appeng.me.service.StorageService} from AE2-original (1.20+), adapted to the 1.12.2
 * {@link IGridCache} lifecycle (no {@code IGridServiceProvider}, node/machine pairs instead of
 * node/savedData, no debug-dump hook).
 */
public class GridStorageCache implements IStorageService, IGridCache {

    private final IGrid myGrid;

    /**
     * Tracks the storage service's state for each grid node that provides storage to the network.
     */
    private final Map<IGridNode, ProviderState> nodeProviders = new IdentityHashMap<>();
    /**
     * Tracks state for storage providers that are provided outside of the normal node lifecycle (e.g. a
     * wireless terminal's cell).
     */
    private final List<ProviderState> globalProviders = new ArrayList<>();
    private final SetMultimap<AEKey, StackWatcher<IStorageWatcherNode>> interests = HashMultimap.create();
    private final InterestManager<StackWatcher<IStorageWatcherNode>> interestManager = new InterestManager<>(
            this.interests);
    private final NetworkStorage storage;
    /**
     * Publicly exposed cached available stacks.
     */
    private final KeyCounter cachedAvailableStacks = new KeyCounter();
    /**
     * Private cached amounts, to ensure that we send correct change notifications even if
     * {@link #cachedAvailableStacks} is modified by mistake.
     */
    private final Object2LongMap<AEKey> cachedAvailableAmounts = new Object2LongOpenHashMap<>();
    private boolean cachedStacksNeedUpdate = true;
    /**
     * Tracks the stack watcher associated with a given grid node. Needed to clean up watchers when the node
     * leaves the grid.
     */
    private final Map<IGridNode, StackWatcher<IStorageWatcherNode>> watchers = new IdentityHashMap<>();

    public GridStorageCache(final IGrid g) {
        this.myGrid = g;
        this.storage = new NetworkStorage();
    }

    @Override
    public void onUpdateTick() {
        if (interestManager.isEmpty()) {
            // lazily rebuild cache list
            cachedStacksNeedUpdate = true;
        } else {
            // we need to rebuild the cache every tick to notify listeners
            updateCachedStacks();
        }
    }

    private void updateCachedStacks() {
        cachedStacksNeedUpdate = false;

        cachedAvailableStacks.clear();
        storage.getAvailableStacks(cachedAvailableStacks);
        // clear() only clears the inner maps,
        // so ensure that the outer map gets cleaned up too
        cachedAvailableStacks.removeEmptySubmaps();

        // Post watcher update for currently available stacks
        for (var entry : cachedAvailableStacks) {
            var what = entry.getKey();
            var newAmount = entry.getLongValue();
            if (newAmount != cachedAvailableAmounts.getLong(what)) {
                postWatcherUpdate(what, newAmount);
            }
        }
        // Post watcher update for removed stacks
        for (var what : cachedAvailableAmounts.keySet()) {
            var newAmount = cachedAvailableStacks.get(what);
            if (newAmount == 0) {
                postWatcherUpdate(what, newAmount);
            }
        }

        // Update private amounts
        cachedAvailableAmounts.clear();
        for (var entry : cachedAvailableStacks) {
            cachedAvailableAmounts.put(entry.getKey(), entry.getLongValue());
        }
    }

    private void postWatcherUpdate(AEKey what, long newAmount) {
        for (var watcher : interestManager.get(what)) {
            watcher.getHost().onStackChange(what, newAmount);
        }
        for (var watcher : interestManager.getAllStacksWatchers()) {
            watcher.getHost().onStackChange(what, newAmount);
        }
    }

    /**
     * When a node joins the grid, we automatically register a provided {@link IStorageProvider} and/or
     * {@link IStorageWatcherNode}.
     */
    @Override
    public void addNode(final IGridNode node, final IGridHost machine) {
        if (machine instanceof IStorageProvider) {
            final IStorageProvider storageProvider = (IStorageProvider) machine;
            final ProviderState state = new ProviderState(storageProvider);
            this.nodeProviders.put(node, state);
            state.mount();
        }

        if (machine instanceof IStorageWatcherNode) {
            final IStorageWatcherNode watcherNode = (IStorageWatcherNode) machine;
            final StackWatcher<IStorageWatcherNode> watcher = new StackWatcher<>(interestManager, watcherNode);
            this.watchers.put(node, watcher);
            watcherNode.updateWatcher(watcher);
        }
    }

    /**
     * When a node leaves the grid, we automatically unregister the previously registered
     * {@link IStorageProvider} and/or {@link IStorageWatcherNode}.
     */
    @Override
    public void removeNode(final IGridNode node, final IGridHost machine) {
        final StackWatcher<IStorageWatcherNode> watcher = this.watchers.remove(node);
        if (watcher != null) {
            watcher.destroy();
        }

        final ProviderState providerState = this.nodeProviders.remove(node);
        if (providerState != null) {
            providerState.unmount();
        }
    }

    @Override
    public void onSplit(final IGridStorage destinationStorage) {
        // nothing!
    }

    @Override
    public void onJoin(final IGridStorage sourceStorage) {
        // nothing!
    }

    @Override
    public void populateGridStorage(final IGridStorage destinationStorage) {
        // nothing!
    }

    @Override
    public MEStorage getInventory() {
        return storage;
    }

    @Override
    public KeyCounter getCachedInventory() {
        if (cachedStacksNeedUpdate) {
            updateCachedStacks();
        }
        return cachedAvailableStacks;
    }

    @Override
    public void addGlobalStorageProvider(IStorageProvider provider) {
        for (var state : globalProviders) {
            if (state.provider == provider) {
                throw new IllegalArgumentException("Duplicate storage provider registration for " + provider);
            }
        }

        var state = new ProviderState(provider);
        this.globalProviders.add(state);
        state.mount();
    }

    @Override
    public void removeGlobalStorageProvider(IStorageProvider provider) {
        var it = this.globalProviders.iterator();
        while (it.hasNext()) {
            var state = it.next();
            if (state.provider == provider) {
                it.remove();
                state.unmount();
            }
        }
    }

    @Override
    public void refreshNodeStorageProvider(IGridNode node) {
        var state = nodeProviders.get(node);
        if (state == null) {
            throw new IllegalArgumentException("The given node is not part of this grid or has no storage provider.");
        }
        state.update();
    }

    @Override
    public void refreshGlobalStorageProvider(IStorageProvider provider) {
        for (var state : globalProviders) {
            if (state.provider == provider) {
                state.update();
                return;
            }
        }

        throw new IllegalArgumentException("Storage provider " + provider + " is not part of this grid.");
    }

    @Override
    public void invalidateCache() {
        cachedStacksNeedUpdate = true;
    }

    IGrid getGrid() {
        return this.myGrid;
    }

    /**
     * A {@link IStorageProvider}-specific mount table facade which allows the provider to easily
     * mount/remount its storage.
     */
    private class ProviderState implements IStorageMounts {
        private final IStorageProvider provider;
        private final Set<MEStorage> inventories = new HashSet<>();
        private boolean mounted;

        ProviderState(final IStorageProvider provider) {
            this.provider = provider;
        }

        /**
         * Performs the first mount operation on this storage provider, which does not assume any of the
         * provider's inventories are currently mounted and need to be removed first.
         */
        private void mount() {
            Preconditions.checkState(!mounted, "Can't mount a provider's inventories when it's already mounted");

            mounted = true;
            provider.mountInventories(this);
        }

        @Override
        public void mount(MEStorage inventory, int priority) {
            Preconditions.checkState(mounted, "Cannot use StorageMounts after the storage has been unmounted.");

            if (!inventories.add(inventory)) {
                throw new IllegalStateException("Cannot mount the same inventory twice.");
            }

            // Mount this inventory into the network storage
            storage.mount(priority, inventory);
        }

        void update() {
            unmount();
            mount();
        }

        void unmount() {
            if (!mounted) {
                return;
            }
            mounted = false;

            for (var inventory : inventories) {
                storage.unmount(inventory);
            }
            inventories.clear();
        }
    }
}
