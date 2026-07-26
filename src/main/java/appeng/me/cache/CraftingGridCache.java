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


import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPostCacheConstruction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingLinkNexus;
import appeng.crafting.CraftingWatcher;
import appeng.me.helpers.InterestManager;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingStorageTile;
import appeng.tile.crafting.TileCraftingTile;
import com.google.common.collect.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.world.World;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.StreamSupport;


/**
 * Replaces the old {@code IMEInventoryHandler<IAEItemStack>}/{@code ICellProvider} dual role of this
 * class: craftable-but-not-stored keys are no longer smuggled into the storage system as a fake cell.
 * They are exposed directly through {@link #getCraftables(AEKeyFilter)} (contract change §8.3), and
 * changes are pushed to interested {@link ICraftingWatcherHost}s the same way AE2-original's
 * {@code CraftingService} does it, instead of round-tripping through the storage grid.
 * <p>
 * The old "fake storage cell at max priority" trick used to detect job completion (a crafting CPU
 * used to register itself as an {@code IMEInventoryHandler} so it would see the network trying to
 * insert the item it was waiting for) still exists, just expressed through the current API: this
 * cache mounts itself as a normal-priority-{@code MAX_VALUE} {@link MEStorage} via
 * {@link IStorageProvider}/{@link IStorageMounts}, and forwards inserts to whichever
 * {@link CraftingCPUCluster} is waiting for that key.
 */
public class CraftingGridCache implements ICraftingGrid, ICraftingProviderHelper, IStorageProvider, MEStorage {

    private static final ExecutorService CRAFTING_POOL;
    private static final Comparator<ICraftingPatternDetails> COMPARATOR = (firstDetail, nextDetail) -> nextDetail.getPriority() - firstDetail.getPriority();

    static {
        final ThreadFactory factory = ar -> new Thread(ar, "AE Crafting Calculator");

        CRAFTING_POOL = Executors.newCachedThreadPool(factory);
    }

    private final Set<CraftingCPUCluster> craftingCPUClusters = new HashSet<>();
    private final Set<ICraftingProvider> craftingProviders = new HashSet<>();
    private final Map<IGridNode, ICraftingWatcher> craftingWatchers = new HashMap<>();
    private final IGrid grid;
    private final Object2ObjectMap<ICraftingPatternDetails, List<ICraftingMedium>> craftingMethods = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<AEKey, ImmutableList<ICraftingPatternDetails>> craftableItems = new Object2ObjectOpenHashMap<>();
    private final Set<AEKey> emitableItems = new HashSet<>();
    private final Map<String, CraftingLinkNexus> craftingLinks = new HashMap<>();
    private final Multimap<AEKey, CraftingWatcher> interests = HashMultimap.create();
    private final InterestManager<CraftingWatcher> interestManager = new InterestManager<>(this.interests);
    private IEnergyGrid energyGrid;
    int i;
    private boolean updateList = false;
    private boolean updatePatterns = false;

    public CraftingGridCache(final IGrid grid) {
        this.grid = grid;
    }

    @MENetworkEventSubscribe
    public void afterCacheConstruction(final MENetworkPostCacheConstruction cacheConstruction) {
        this.energyGrid = this.grid.getCache(IEnergyGrid.class);

        final IStorageService storageService = this.grid.getCache(IStorageService.class);
        storageService.addGlobalStorageProvider(this);
    }

    /**
     * Mounts this cache itself at the highest possible priority, so completed crafting jobs are
     * observed before any regular cell gets a chance to swallow the item silently.
     */
    @Override
    public void mountInventories(final IStorageMounts storageMounts) {
        storageMounts.mount(this, Integer.MAX_VALUE);
    }

    /**
     * Gives every waiting {@link CraftingCPUCluster} a chance to claim (part of) an insertion before
     * it lands in regular storage. See the class javadoc for why this exists.
     */
    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        long remaining = amount;

        for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {
            if (remaining <= 0) {
                break;
            }
            remaining -= cpu.injectItems(what, remaining, mode, source);
        }

        return amount - remaining;
    }

    @Override
    public void onUpdateTick() {
        if (this.updateList) {
            this.updateList = false;
            this.updateCPUClusters();
        }

        if (updatePatterns) {
            this.recalculateCraftingPatterns();
            this.updatePatterns = false;
        }

        final Iterator<CraftingLinkNexus> craftingLinkIterator = this.craftingLinks.values().iterator();
        while (craftingLinkIterator.hasNext()) {
            if (craftingLinkIterator.next().isDead(this.grid, this)) {
                craftingLinkIterator.remove();
            }
        }

        for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {
            cpu.updateCraftingLogic(this.grid, this.energyGrid, this);
        }
    }

    @Override
    public void removeNode(final IGridNode gridNode, final IGridHost machine) {
        if (machine instanceof ICraftingWatcherHost) {
            final ICraftingWatcher craftingWatcher = this.craftingWatchers.get(gridNode);
            if (craftingWatcher != null) {
                craftingWatcher.reset();
                this.craftingWatchers.remove(gridNode);
            }
        }

        if (machine instanceof ICraftingRequester) {
            for (final CraftingLinkNexus link : this.craftingLinks.values()) {
                if (link.isMachine(machine)) {
                    link.removeNode();
                }
            }
        }

        if (machine instanceof TileCraftingTile) {
            this.updateList = true;
        }

        if (machine instanceof ICraftingProvider) {
            this.craftingProviders.remove(machine);
            this.updatePatterns = true;
        }
    }

    @Override
    public void addNode(final IGridNode gridNode, final IGridHost machine) {
        if (machine instanceof ICraftingWatcherHost) {
            final ICraftingWatcherHost watcherHost = (ICraftingWatcherHost) machine;
            final CraftingWatcher watcher = new CraftingWatcher(this, watcherHost);
            this.craftingWatchers.put(gridNode, watcher);
            watcherHost.updateWatcher(watcher);
        }

        if (machine instanceof ICraftingRequester) {
            for (final ICraftingLink link : ((ICraftingRequester) machine).getRequestedJobs()) {
                if (link instanceof CraftingLink) {
                    this.addLink((CraftingLink) link);
                }
            }
        }

        if (machine instanceof TileCraftingTile) {
            this.updateList = true;
        }

        if (machine instanceof ICraftingProvider) {
            this.craftingProviders.add((ICraftingProvider) machine);
            this.updatePatterns = true;
        }
    }

    @Override
    public void onSplit(final IGridStorage destinationStorage) { // nothing!
    }

    @Override
    public void onJoin(final IGridStorage sourceStorage) {
        // nothing!
    }

    @Override
    public void populateGridStorage(final IGridStorage destinationStorage) {
        // nothing!
    }

    private void updatePatterns() {
        this.updatePatterns = true;
    }

    private void recalculateCraftingPatterns() {
        final Object2ObjectMap<AEKey, ImmutableList<ICraftingPatternDetails>> oldItems = new Object2ObjectOpenHashMap<>(this.craftableItems);
        final Set<AEKey> oldEmitableItems = new HashSet<>(this.emitableItems);

        // erase list.
        this.craftingMethods.clear();
        this.craftableItems.clear();
        this.emitableItems.clear();

        // re-create list..
        for (final ICraftingProvider provider : this.craftingProviders) {
            provider.provideCrafting(this);
        }

        final Object2ObjectMap<AEKey, ObjectSet<ICraftingPatternDetails>> tmpCraft = new Object2ObjectOpenHashMap<>();

        // new craftables!
        for (final ICraftingPatternDetails details : this.craftingMethods.keySet()) {
            for (final GenericStack out : details.getOutputs()) {
                if (out == null) {
                    continue;
                }
                final AEKey key = out.what();

                ObjectSet<ICraftingPatternDetails> methods = tmpCraft.get(key);

                if (methods == null) {
                    tmpCraft.put(key, methods = new ObjectRBTreeSet<>(COMPARATOR));
                }

                methods.add(details);
            }
        }

        // make them immutable
        for (final Entry<AEKey, ObjectSet<ICraftingPatternDetails>> e : tmpCraft.entrySet()) {
            this.craftableItems.put(e.getKey(), ImmutableList.copyOf(e.getValue()));
        }

        // Figure out which keys flipped craftable-state (either via a pattern or an emitter) so
        // interested ICraftingWatcherHosts can be told, mirroring AE2-original's CraftingService
        // instead of pushing the change through the storage grid (removed, see contract §8.3).
        final Set<AEKey> changed = new HashSet<>();

        for (final AEKey key : oldItems.keySet()) {
            if (!this.craftableItems.containsKey(key)) {
                changed.add(key);
            }
        }
        for (final AEKey key : this.craftableItems.keySet()) {
            if (!oldItems.containsKey(key)) {
                changed.add(key);
            }
        }
        for (final AEKey key : oldEmitableItems) {
            if (!this.emitableItems.contains(key)) {
                changed.add(key);
            }
        }
        for (final AEKey key : this.emitableItems) {
            if (!oldEmitableItems.contains(key)) {
                changed.add(key);
            }
        }

        for (final AEKey key : changed) {
            this.notifyCraftableChange(key);
        }
    }

    private void notifyCraftableChange(final AEKey what) {
        for (final CraftingWatcher watcher : this.interestManager.get(what)) {
            watcher.getHost().onRequestChange(this, what);
        }
    }

    private void updateCPUClusters() {
        this.craftingCPUClusters.clear();

        for (Object cls: StreamSupport.stream(grid.getMachinesClasses().spliterator(), false).filter(TileCraftingStorageTile.class::isAssignableFrom).toArray()) {
            for (final IGridNode cst : this.grid.getMachines((Class<? extends IGridHost>) cls)) {
                final TileCraftingStorageTile tile = (TileCraftingStorageTile) cst.getMachine();
                final CraftingCPUCluster cluster = (CraftingCPUCluster) tile.getCluster();
                if (cluster != null) {
                    this.craftingCPUClusters.add(cluster);

                    if (cluster.getLastCraftingLink() != null) {
                        this.addLink((CraftingLink) cluster.getLastCraftingLink());
                    }
                }
            }
        }

    }

    public void addLink(final CraftingLink link) {
        if (link.isStandalone()) {
            return;
        }

        CraftingLinkNexus nexus = this.craftingLinks.get(link.getCraftingID());
        if (nexus == null) {
            this.craftingLinks.put(link.getCraftingID(), nexus = new CraftingLinkNexus(link.getCraftingID()));
        }

        link.setNexus(nexus);
    }

    @MENetworkEventSubscribe
    public void updateCPUClusters(final MENetworkCraftingCpuChange c) {
        this.updateList = true;
    }

    @MENetworkEventSubscribe
    public void updateCPUClusters(final MENetworkCraftingPatternChange c) {
        this.updatePatterns();
    }

    @Override
    public void addCraftingOption(final ICraftingMedium medium, final ICraftingPatternDetails api) {
        List<ICraftingMedium> details = this.craftingMethods.get(api);
        if (details == null) {
            details = new ArrayList<>();
            details.add(medium);
            this.craftingMethods.put(api, details);
        } else {
            details.add(medium);
        }
    }

    @Override
    public void setEmitable(final AEKey what) {
        this.emitableItems.add(what);
    }

    @Override
    public ImmutableCollection<ICraftingPatternDetails> getCraftingFor(final AEKey whatToCraft, final ICraftingPatternDetails details, final int slotIndex, final World world) {
        final ImmutableList<ICraftingPatternDetails> res = this.craftableItems.get(whatToCraft);

        if (res == null) {
            return ImmutableSet.of();
        }

        return res;
    }

    @Override
    public Future<ICraftingJob> beginCraftingJob(final World world, final IGrid grid, final IActionSource actionSrc, final GenericStack craftWhat, final ICraftingCallback cb) {
        if (world == null || grid == null || actionSrc == null || craftWhat == null) {
            throw new IllegalArgumentException("Invalid Crafting Job Request");
        }

        final CraftingJob job = new CraftingJob(world, grid, actionSrc, craftWhat, cb);

        return CRAFTING_POOL.submit(job, job);
    }

    @Override
    public ICraftingLink submitJob(final ICraftingJob job, final ICraftingRequester requestingMachine, final ICraftingCPU target, final boolean prioritizePower, final IActionSource src) {
        if (job.isSimulation()) {
            return null;
        }

        CraftingCPUCluster cpuCluster = null;

        if (target instanceof CraftingCPUCluster) {
            cpuCluster = (CraftingCPUCluster) target;
        }

        if (target == null) {
            final List<CraftingCPUCluster> validCpusClusters = new ArrayList<>();
            for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {
                if (cpu.isActive() && !cpu.isBusy() && cpu.getAvailableStorage() >= job.getByteTotal()) {
                    validCpusClusters.add(cpu);
                }
            }

            Collections.sort(validCpusClusters, (firstCluster, nextCluster) -> {
                if (prioritizePower) {
                    final int comparison1 = Long.compare(nextCluster.getCoProcessors(), firstCluster.getCoProcessors());
                    if (comparison1 != 0) {
                        return comparison1;
                    }
                    return Long.compare(nextCluster.getAvailableStorage(), firstCluster.getAvailableStorage());
                }

                final int comparison2 = Long.compare(firstCluster.getCoProcessors(), nextCluster.getCoProcessors());
                if (comparison2 != 0) {
                    return comparison2;
                }
                return Long.compare(firstCluster.getAvailableStorage(), nextCluster.getAvailableStorage());
            });

            if (!validCpusClusters.isEmpty()) {
                cpuCluster = validCpusClusters.get(0);
            }
        }

        if (cpuCluster != null) {
            return cpuCluster.submitJob(this.grid, job, src, requestingMachine);
        }

        return null;
    }

    @Override
    public ImmutableSet<ICraftingCPU> getCpus() {
        return ImmutableSet.copyOf(new ActiveCpuIterator(this.craftingCPUClusters));
    }

    @Override
    public boolean canEmitFor(final AEKey someItem) {
        return this.emitableItems.contains(someItem);
    }

    @Override
    public Set<AEKey> getCraftables(final AEKeyFilter filter) {
        final Set<AEKey> result = new HashSet<>();

        for (final AEKey key : this.craftableItems.keySet()) {
            if (filter.matches(key)) {
                result.add(key);
            }
        }
        for (final AEKey key : this.emitableItems) {
            if (filter.matches(key)) {
                result.add(key);
            }
        }

        return result;
    }

    @Override
    public boolean isRequesting(final AEKey what) {
        return this.requesting(what) > 0;
    }

    @Override
    public long requesting(final AEKey what) {
        long requested = 0;

        for (final CraftingCPUCluster cluster : this.craftingCPUClusters) {
            requested += cluster.making(what);
        }

        return requested;
    }

    public List<ICraftingMedium> getMediums(final ICraftingPatternDetails key) {
        List<ICraftingMedium> mediums = this.craftingMethods.get(key);

        if (mediums == null) {
            mediums = ImmutableList.of();
        }

        return mediums;
    }

    public boolean hasCpu(final ICraftingCPU cpu) {
        if (cpu instanceof CraftingCPUCluster) {
            return this.craftingCPUClusters.contains((CraftingCPUCluster) cpu);
        }
        return false;
    }

    public InterestManager<CraftingWatcher> getInterestManager() {
        return this.interestManager;
    }

    private static class ActiveCpuIterator implements Iterator<ICraftingCPU> {

        private final Iterator<CraftingCPUCluster> iterator;
        private CraftingCPUCluster cpuCluster;

        public ActiveCpuIterator(final Collection<CraftingCPUCluster> o) {
            this.iterator = o.iterator();
            this.cpuCluster = null;
        }

        @Override
        public boolean hasNext() {
            this.findNext();

            return this.cpuCluster != null;
        }

        private void findNext() {
            while (this.iterator.hasNext() && this.cpuCluster == null) {
                this.cpuCluster = this.iterator.next();
                if (!this.cpuCluster.isActive() || this.cpuCluster.isDestroyed()) {
                    this.cpuCluster = null;
                }
            }
        }

        @Override
        public ICraftingCPU next() {
            final ICraftingCPU o = this.cpuCluster;
            this.cpuCluster = null;

            return o;
        }

        @Override
        public void remove() {
            // no..
        }
    }
}
