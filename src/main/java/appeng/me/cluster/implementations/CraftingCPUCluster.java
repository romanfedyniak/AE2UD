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

package appeng.me.cluster.implementations;


import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.WorldCoord;
import appeng.container.ContainerNull;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.features.AEFeature;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingToast;
import appeng.crafting.*;
import appeng.helpers.PatternHelper;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.IAECluster;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.tile.crafting.TileCraftingMonitorTile;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.Platform;
import com.google.common.base.Preconditions;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;


/**
 * Storage-facing parts of this class (everything that used to speak {@code IAEItemStack}/
 * {@code IItemList}/{@code IMEInventory}/{@code IMEMonitorHandlerReceiver}) have been migrated to
 * {@code AEKey}/{@code GenericStack}/{@code KeyCounter}/{@link MEStorage}. The job-tree math itself
 * ({@code CraftingTreeNode}, {@code MECraftingInventory}, {@code CraftingJob}, {@code CraftingLink})
 * lives in {@code appeng.crafting}, which is out of this migration's scope and still needs its own
 * pass (see the migration report) — this class calls into it assuming the same mapping applied here
 * (a plain {@link MEStorage}-shaped {@code MECraftingInventory}, {@code GenericStack}-based
 * {@code CraftingJob}/{@code CraftingLink}).
 * <p/>
 * <b>Push notifications (CONTRACT.md §10):</b> the old {@code addListener}/{@code removeListener}/
 * {@code postChange} trio, built on the deleted {@code IBaseMonitor}/{@code IMEMonitorHandlerReceiver} model, is
 * restored here against {@link ICraftingCPUListener} instead — same push behaviour, {@link AEKey}-shaped. The
 * owner rejected replacing this with polling, so {@code ContainerCraftingCPU} (wave 3) must keep subscribing via
 * {@link #addListener}/{@link #removeListener} rather than re-reading state every tick.
 */
public final class CraftingCPUCluster implements IAECluster, ICraftingCPU {

    private static final GenericStack[] EMPTY_EXTRAS = new GenericStack[0];

    private static final String LOG_MARK_AS_COMPLETE = "Completed job for %s.";

    private final WorldCoord min;
    private final WorldCoord max;
    private final int[] usedOps = new int[3];
    private final Map<ICraftingPatternDetails, TaskProgress> tasks = new HashMap<>();
    // INSTANCE sate
    private final List<TileCraftingTile> tiles = new ArrayList<>();
    private final List<TileCraftingTile> storage = new ArrayList<>();
    private final List<TileCraftingMonitorTile> status = new ArrayList<>();
    private final Map<ICraftingCPUListener, Object> listeners = new HashMap<>();
    private final Map<ICraftingPatternDetails, Queue<ICraftingMedium>> visitedMediums = new HashMap<>();
    /** This tick's working copy of {@link #tasks}; a task drops out of it once it is known unworkable. */
    private final Map<ICraftingPatternDetails, TaskProgress> workableTasks = new HashMap<>();
    /** Mediums already found busy this tick. Cleared every tick, so nothing stays skipped. */
    private final Set<ICraftingMedium> busyMediums = new HashSet<>();
    /** Per pattern and medium: {@code {interval, earliest tick to try again}}. See {@link #backedOff}. */
    private final Map<ICraftingPatternDetails, Map<ICraftingMedium, long[]>> pushBackoff = new HashMap<>();
    private ICraftingLink myLastLink;
    private String myName = "";
    private boolean isDestroyed = false;
    /**
     * crafting job info
     */
    private MECraftingInventory inventory = new MECraftingInventory();
    private GenericStack finalOutput;
    /**
     * How much the job was submitted for. {@link #finalOutput} counts down to zero as the job delivers, so
     * by the time {@link #completeJob()} runs it no longer knows what was asked for.
     */
    private long requestedAmount;
    private boolean waiting = false;
    private KeyCounter waitingFor = new KeyCounter();
    private long availableStorage = 0;
    private MachineSource machineSrc = null;
    private int accelerator = 0;
    private boolean isComplete = true;
    private int remainingOperations;
    private boolean somethingChanged;

    private long lastTime;
    private long elapsedTime;
    private long startItemCount;
    private long remainingItemCount;
    private UUID requestingPlayerUUID;
    private String requestingPlayerName;

    public CraftingCPUCluster(final WorldCoord min, final WorldCoord max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public GenericStack getFinalOutput() {
        return finalOutput;
    }

    public boolean isDestroyed() {
        return this.isDestroyed;
    }

    public ICraftingLink getLastCraftingLink() {
        return this.myLastLink;
    }

    public MEStorage getInventory() {
        return this.inventory;
    }

    /**
     * Add a new listener to this CPU's push notifications, be sure to properly {@link #removeListener} when done.
     */
    public void addListener(final ICraftingCPUListener l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    /**
     * Remove a previously added listener.
     */
    public void removeListener(final ICraftingCPUListener l) {
        this.listeners.remove(l);
    }

    /**
     * Notifies every still-valid listener that {@code what}'s stored/active/pending amount on this CPU may have
     * changed. See the class javadoc and {@link ICraftingCPUListener} for the contract this restores.
     */
    private void postChange(final AEKey what, final IActionSource src) {
        if (this.listeners.isEmpty()) {
            return;
        }

        final Iterator<Entry<ICraftingCPUListener, Object>> i = this.listeners.entrySet().iterator();
        while (i.hasNext()) {
            final Entry<ICraftingCPUListener, Object> entry = i.next();

            if (entry.getKey().isValid(entry.getValue())) {
                entry.getKey().onCraftingCPUChange(what, src);
            } else {
                i.remove();
            }
        }
    }

    @Override
    public void updateStatus(final boolean updateGrid) {
        for (final TileCraftingTile r : this.tiles) {
            r.updateMeta(true);
        }
    }

    @Override
    public void destroy() {
        if (this.isDestroyed) {
            return;
        }
        this.isDestroyed = true;

        boolean posted = false;

        for (final TileCraftingTile r : this.tiles) {
            final IGridNode n = r.getActionableNode();
            if (n != null && !posted) {
                final IGrid g = n.getGrid();
                if (g != null) {
                    g.postEvent(new MENetworkCraftingCpuChange(n));
                    posted = true;
                }
            }

            r.updateStatus(null);
        }
    }

    @Override
    public Iterator<IGridHost> getTiles() {
        return (Iterator) this.tiles.iterator();
    }

    void addTile(final TileCraftingTile te) {
        if (this.machineSrc == null || te.isCoreBlock()) {
            this.machineSrc = new MachineSource(te);
        }

        te.setCoreBlock(false);
        te.saveChanges();
        this.tiles.add(0, te);

        if (te.isStorage()) {
            this.availableStorage += te.getStorageBytes();
            this.storage.add(te);
        } else if (te.isStatus()) {
            this.status.add((TileCraftingMonitorTile) te);
        } else if (te.isAccelerator()) {
            this.accelerator++;
        }
    }

    public boolean canAccept(final AEKey input) {
        return this.waitingFor.get(input) > 0;
    }

    /**
     * Called by {@link CraftingGridCache#insert} when the network storage system is trying to insert
     * {@code what} — this is how a completed (or partially completed) crafting job is detected and
     * finalized. Replaces the old {@code IMEInventoryHandler.injectItems} interception that used to
     * work because this cluster's cache registered itself as a max-priority fake storage cell; see
     * the class javadoc on {@link CraftingGridCache}.
     *
     * @return how much of {@code amount} this job claimed.
     */
    public long injectItems(final AEKey what, final long amount, final Actionable type, final IActionSource src) {
        // also stop accepting items when the job is complete, i.e. to prevent re-insertion when pushing out
        // items during storeItems
        if (amount <= 0 || this.isComplete) {
            return 0;
        }

        final long waitingAmount = this.waitingFor.get(what);
        if (waitingAmount <= 0) {
            return 0;
        }

        final long used = Math.min(waitingAmount, amount);

        if (type == Actionable.SIMULATE) {
            return used;
        }

        this.waiting = false;
        this.postChange(what, src);
        this.waitingFor.remove(what, used);
        // The pre-port code called updateRemainingItemCount(what) here. Without it the counter stays at
        // the job's starting value, so the crafting status tooltip reads "10 / 10" for the whole job and
        // ContainerCraftingCPU's ETA divides by max(1, start - remaining) == 1, making the estimate
        // meaningless as well.
        this.remainingItemCount -= used;
        this.postCraftingStatusChange(what);
        this.markDirty();

        if (this.finalOutput != null && this.finalOutput.what().equals(what)) {
            long delivered = used;

            if (this.myLastLink != null) {
                final GenericStack leftover = ((CraftingLink) this.myLastLink).injectItems(new GenericStack(what, used), type);
                delivered = used - GenericStack.getStackSizeOrZero(leftover);
            }

            this.finalOutput = new GenericStack(what, this.finalOutput.amount() - used);

            if (this.finalOutput.amount() <= 0) {
                this.completeJob();
            }

            this.updateCPU();

            return delivered;
        }

        return this.inventory.insert(what, used, Actionable.MODULATE, src);
    }

    private void markDirty() {
        this.getCore().saveChanges();
    }

    /**
     * Tells whichever {@link ICraftingWatcherHost} is interested in {@code what} that its
     * craftable/requested state may have changed.
     */
    private void postCraftingStatusChange(final AEKey what) {
        if (this.getGrid() == null) {
            return;
        }

        final CraftingGridCache sg = this.getGrid().getCache(ICraftingGrid.class);

        for (final CraftingWatcher watcher : sg.getInterestManager().get(what)) {
            watcher.getHost().onRequestChange(sg, what);
        }
    }

    private void completeJob() {
        if (this.myLastLink != null) {
            ((CraftingLink) this.myLastLink).markDone();
        }

        if (AELog.isCraftingLogEnabled() && this.finalOutput != null) {
            AELog.crafting(LOG_MARK_AS_COMPLETE, this.finalOutput.what().getDisplayName());
        }

        // Waiting for can potentially contain items at this point, if the user has a 64xplank->64xbutton processing
        // recipe for example, but only requested 1xbutton. We just ignore the rest since it will be dumped
        // back into the network inventory regardless. For this to work it's important that injectItems in this CPU
        // does not accept any further items if isComplete is true.
        // clear(), not reset(): KeyCounter.reset() zeroes the amounts but KEEPS the keys, and
        // KeyCounter.isEmpty() counts keys rather than amounts. The old IItemList.isEmpty() walked a
        // meaningful iterator that skipped zero-size entries, so resetStatus() did empty it. With reset()
        // here, isBusy() stayed true forever and the CPU was never released - no further job could be
        // submitted until the crafting storage was broken and replaced.
        this.waitingFor.clear();
        this.remainingItemCount = 0;
        this.startItemCount = 0;
        this.lastTime = 0;
        this.elapsedTime = 0;
        this.isComplete = true;

        notifyRequester(false);
        fireCraftedEventForRequester();
        this.requestingPlayerUUID = null;
        this.requestingPlayerName = null;
    }

    /**
     * Fires {@link PlayerEvent.ItemCraftedEvent} for the player who requested this job, once, for the item
     * they asked for - so achievement and quest mods gated on that event see an autocrafted result the same
     * way they would a hand-crafted one.
     * <p>
     * Deliberately not a copy of upstream AE2's equivalent: upstream fires this per finished pattern under a
     * fixed system identity that belongs to no player and no team, which was verified (against the shipped
     * mod's bytecode) to satisfy no player-gated quest at all. Firing once here, for the real requester, is
     * what actually credits them - at the cost of not firing per intermediate ingredient the way upstream
     * does. Applies to both crafting-mode and processing-mode jobs alike, since a CPU's completed job has no
     * concept of "which pattern shape produced it" once the network has it - only whether a player asked.
     */
    private void fireCraftedEventForRequester() {
        if (!Platform.isServer()) {
            return;
        }
        if (!AEConfig.instance().isFeatureEnabled(AEFeature.AUTOCRAFT_ITEM_CRAFTED_EVENT)) {
            return;
        }
        if (this.requestingPlayerUUID == null || this.finalOutput == null) {
            return;
        }
        if (!(this.finalOutput.what() instanceof AEItemKey itemKey)) {
            // Vanilla's crafted event carries an ItemStack; a fluid or addon key type has no honest one.
            return;
        }

        final EntityPlayer player = AppEng.proxy.getPlayerByUUID(this.requestingPlayerUUID);
        if (!(player instanceof EntityPlayerMP playerMP)) {
            // Offline, or never a real player (a machine-submitted job clears requestingPlayerUUID instead
            // of pointing at one) - nobody to credit, and no fake-player fallback to credit them with.
            return;
        }

        final long amount = this.requestedAmount > 0 ? this.requestedAmount : 1;
        final ItemStack crafted = itemKey.toStack((int) Math.min(amount, Integer.MAX_VALUE));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(playerMP, crafted, new InventoryCrafting(new ContainerNull(), 1, 1)));
    }

    private void notifyRequester(boolean cancelled) {
        if (!Platform.isServer()) return;
        if (this.requestingPlayerUUID == null) return;
        if (this.finalOutput == null) return;
        if (!AEConfig.instance().isFeatureEnabled(AEFeature.CRAFTING_TOASTS)) return;

        var player = AppEng.proxy.getPlayerByUUID(this.requestingPlayerUUID);
        if (player instanceof EntityPlayerMP playerMP) {
            try {
                // What was asked for, not what is left: finalOutput has been counted down to zero by the
                // time completeJob() calls this, and wrapping a zero-amount stack produced an empty
                // ItemStack - the toast used to show "Air".
                final long amount = this.requestedAmount > 0 ? this.requestedAmount : 1;
                NetworkHandler.instance().sendTo(
                        new PacketCraftingToast(new GenericStack(this.finalOutput.what(), amount), cancelled), playerMP);
            } catch (IOException ignored) {}
        }
    }

    private void updateCPU() {
        GenericStack send = this.finalOutput;

        if (this.finalOutput != null && this.finalOutput.amount() <= 0) {
            send = null;
        }

        for (final TileCraftingMonitorTile t : this.status) {
            t.setJob(send);
        }
    }

    private TileCraftingTile getCore() {
        if (this.machineSrc == null) {
            return null;
        }
        return (TileCraftingTile) this.machineSrc.machine().get();
    }

    private IGrid getGrid() {
        for (final TileCraftingTile r : this.tiles) {
            final IGridNode gn = r.getActionableNode();
            if (gn != null) {
                final IGrid g = gn.getGrid();
                if (g != null) {
                    return r.getActionableNode().getGrid();
                }
            }
        }

        return null;
    }

    private boolean canCraft(final ICraftingPatternDetails details, final GenericStack[] condensedInputs) {
        if (!details.isCraftable()) {
            // Processing patterns are relatively easy
            for (final GenericStack input : condensedInputs) {
                if (input == null) {
                    continue;
                }
                final long extracted = this.inventory.extract(input.what(), input.amount(), Actionable.SIMULATE, this.machineSrc);

                if (extracted < input.amount()) {
                    return false;
                }
            }
        } else if (details.canSubstitute() || details.canSubstituteFluids()) {
            // When substitutions are allowed, we have to keep track of which items we've reserved
            final GenericStack[] inputs = details.getInputs();
            final Map<AEKey, Long> consumedCount = new HashMap<>();
            for (int i = 0; i < inputs.length; i++) {
                final List<GenericStack> substitutes = details.getSubstituteInputs(i);
                if (substitutes.isEmpty()) {
                    continue;
                }

                boolean found = false;
                for (final GenericStack substitute : substitutes) {
                    // A substitute's amount is per one of the encoded ingredient, so a bucket slot filled by
                    // the network asks for a bucket's worth and not for one millibucket.
                    final long needed = substitute.amount() * inputs[i].amount();

                    for (final var entry : this.inventory.getAvailableStacks().findFuzzy(substitute.what(), FuzzyMode.IGNORE_ALL)) {
                        final AEKey fuzzKey = entry.getKey();
                        final long alreadyConsumed = consumedCount.getOrDefault(fuzzKey, 0L);
                        if (entry.getLongValue() - alreadyConsumed < needed) {
                            continue; // Already fully consumed by a previous slot of this recipe
                        }

                        final long extracted = this.inventory.extract(fuzzKey, needed, Actionable.SIMULATE, this.machineSrc);

                        if (extracted >= needed) {
                            consumedCount.merge(fuzzKey, needed, Long::sum);
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        break;
                    }
                }

                if (!found) {
                    return false;
                }
            }

        } else {
            // When no substitutions can occur, we can simply check that all items are accounted since
            // each type of item should only occur once
            for (final GenericStack g : condensedInputs) {
                if (g == null) {
                    continue;
                }
                boolean found = false;
                long remaining = g.amount();

                for (final var entry : this.inventory.getAvailableStacks().findFuzzy(g.what(), FuzzyMode.IGNORE_ALL)) {
                    final long extracted = this.inventory.extract(entry.getKey(), remaining, Actionable.SIMULATE, this.machineSrc);

                    if (extracted >= remaining) {
                        found = true;
                        break;
                    } else if (extracted > 0) {
                        remaining -= extracted;
                    }
                }

                if (!found) {
                    return false;
                }
            }

        }

        return true;
    }

    public void cancel() {
        if (this.myLastLink != null) {
            this.myLastLink.cancel();
        }

        // Notify GUI listeners about everything this CPU currently holds (stored, waiting-for, pending) before
        // we clear it all out, so the container can refresh every row instead of just going stale.
        final KeyCounter changed = new KeyCounter();
        this.getListOfItem(changed, CraftingItemList.ALL);
        for (final AEKey key : changed.keySet()) {
            this.postChange(key, this.machineSrc);
        }

        this.isComplete = true;
        this.myLastLink = null;
        this.tasks.clear();
        this.pushBackoff.clear();
        this.visitedMediums.clear();

        final List<AEKey> keys = new ArrayList<>(this.waitingFor.keySet());
        this.waitingFor.clear(); // see completeJob(): reset() keeps the keys and isBusy() counts keys

        notifyRequester(true);
        this.requestingPlayerUUID = null;
        this.requestingPlayerName = null;
        this.finalOutput = null;
        this.updateCPU();

        for (final AEKey key : keys) {
            this.postCraftingStatusChange(key);
        }

        this.storeItems(); // marks dirty
    }

    public void updateCraftingLogic(final IGrid grid, final IEnergyGrid eg, final CraftingGridCache cc) {
        if (!this.getCore().isActive()) {
            return;
        }

        if (this.myLastLink != null) {
            if (this.myLastLink.isCanceled()) {
                this.myLastLink = null;
                this.cancel();
            }
        }

        if (this.isComplete) {
            if (this.inventory.getAvailableStacks().isEmpty()) {
                return;
            }

            this.storeItems();
            return;
        }

        this.waiting = false;
        if (this.waiting || this.tasks.isEmpty()) // nothing to do here...
        {
            return;
        }

        this.remainingOperations = this.accelerator + 1 - (this.usedOps[0] + this.usedOps[1] + this.usedOps[2]);
        final int started = this.remainingOperations;

        // executeCrafting runs again for as long as anything moved, so within one tick the same task can be
        // asked several times. A task that has already answered "not enough ingredients", and a medium that
        // has already answered "busy", will answer the same on every re-pass - both are remembered for the
        // tick and forgotten at the end of it, so nothing stays skipped for longer than that.
        this.workableTasks.clear();
        this.workableTasks.putAll(this.tasks);
        this.busyMediums.clear();
        this.pushBackoff.keySet().retainAll(this.tasks.keySet());

        if (this.remainingOperations > 0) {
            do {
                this.somethingChanged = false;
                this.executeCrafting(eg, cc);
            } while (this.somethingChanged && this.remainingOperations > 0);
        }

        this.workableTasks.clear();
        this.busyMediums.clear();

        this.usedOps[2] = this.usedOps[1];
        this.usedOps[1] = this.usedOps[0];
        this.usedOps[0] = started - this.remainingOperations;

        if (this.remainingOperations > 0 && !this.somethingChanged) {
            this.waiting = true;
        }
    }

    private void executeCrafting(final IEnergyGrid eg, final CraftingGridCache cc) {
        final Iterator<Entry<ICraftingPatternDetails, TaskProgress>> i = this.workableTasks.entrySet().iterator();

        while (i.hasNext()) {
            final Entry<ICraftingPatternDetails, TaskProgress> e = i.next();

            if (e.getValue().value <= 0) {
                this.tasks.remove(e.getKey());
                i.remove();
                continue;
            }

            final ICraftingPatternDetails details = e.getKey();

            if (this.canCraft(details, details.getCondensedInputs())) {
                InventoryCrafting ic = null;
                GenericStack[] extras = EMPTY_EXTRAS;
                GenericStack[] fabricated = EMPTY_EXTRAS;

                if (!visitedMediums.containsKey(details) || visitedMediums.get(details).isEmpty()) {
                    visitedMediums.put(details, new ArrayDeque<>(cc.getMediums(details).stream().filter(Objects::nonNull).collect(Collectors.toList())));
                }

                while (!visitedMediums.get(details).isEmpty()) {

                    ICraftingMedium m = visitedMediums.get(details).poll();

                    if (e.getValue().value <= 0 || m == null || this.busyMediums.contains(m)) {
                        continue;
                    }

                    if (m.isBusy()) {
                        this.busyMediums.add(m);
                        continue;
                    }

                    if (this.backedOff(details, m)) {
                        continue;
                    }

                    if (ic == null) {
                        final GenericStack[] input = details.getInputs();
                        double sum = 0;

                        for (final GenericStack anInput : input) {
                            if (anInput != null) {
                                sum += anInput.amount();
                            }
                        }

                        // power...
                        if (eg.extractAEPower(sum, Actionable.MODULATE, PowerMultiplier.CONFIG) < sum - 0.01) {
                            continue;
                        }
                        if (details.isCraftable()) {
                            ic = new InventoryCrafting(new ContainerNull(), 3, 3);
                        } else {
                            ic = new InventoryCrafting(new ContainerNull(), PatternHelper.PROCESSING_INPUT_WIDTH, PatternHelper.PROCESSING_INPUT_HEIGHT);
                        }

                        boolean found = false;
                        // Ingredients an InventoryCrafting cannot hold - a fluid, or an addon's own key
                        // type. Kept beside the table and handed to the medium with it; the two halves
                        // are all-or-nothing, and the put-back below restores both.
                        final List<GenericStack> extraInputs = new ArrayList<>();
                        // Keys drawn to assemble a container that the crafting grid then carries as an
                        // item. They travel with the table rather than beside it, so neither the table
                        // nor extraInputs can give them back - hence a list of their own.
                        final List<GenericStack> fabricatedInputs = new ArrayList<>();

                        for (int x = 0; x < input.length; x++) {
                            if (input[x] != null) {
                                found = false;

                                if (details.isCraftable() && details.isContainerFabricated(x)
                                        && input[x].what() instanceof AEItemKey containerKey) {
                                    final GenericStack supplied = details.getSubstituteInputs(x).get(0);
                                    final long needed = supplied.amount() * input[x].amount();

                                    // Simulate first: a partial draw would have to be undone by hand,
                                    // because what goes into the table is the container, not the key.
                                    if (this.inventory.extract(supplied.what(), needed, Actionable.SIMULATE, this.machineSrc) == needed) {
                                        this.inventory.extract(supplied.what(), needed, Actionable.MODULATE, this.machineSrc);
                                        this.postChange(supplied.what(), this.machineSrc);
                                        fabricatedInputs.add(new GenericStack(supplied.what(), needed));
                                        ic.setInventorySlotContents(x, containerKey.toStack((int) input[x].amount()));
                                        found = true;
                                    }
                                } else if (details.isCraftable()) {
                                    final List<GenericStack> itemList;

                                    if (details.canSubstitute()) {
                                        final List<GenericStack> substitutes = details.getSubstituteInputs(x);
                                        itemList = new ArrayList<>();

                                        for (final GenericStack sub : substitutes) {
                                            for (final var entry : this.inventory.getAvailableStacks().findFuzzy(sub.what(), FuzzyMode.IGNORE_ALL)) {
                                                itemList.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                                            }
                                        }
                                    } else {
                                        itemList = new ArrayList<>(1);

                                        final AEKey exactKey = input[x].what();
                                        if (this.inventory.getAvailableStacks().get(exactKey) > 0) {
                                            itemList.add(new GenericStack(exactKey, input[x].amount()));
                                        } else if (exactKey instanceof AEItemKey itemKey
                                                && (itemKey.getItem().isDamageable() || Platform.isGTDamageableItem(itemKey.getItem()))) {
                                            for (final var entry : this.inventory.getAvailableStacks().findFuzzy(exactKey, FuzzyMode.IGNORE_ALL)) {
                                                itemList.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                                            }
                                        }
                                    }

                                    for (final GenericStack fuzz : itemList) {
                                        if (!(fuzz.what() instanceof AEItemKey fuzzItemKey)) {
                                            continue;
                                        }
                                        final ItemStack candidate = fuzzItemKey.toStack((int) Math.min(input[x].amount(), fuzzItemKey.getMaxStackSize()));

                                        if (details.isValidItemForSlot(x, candidate, this.getWorld())) {
                                            final long extracted = this.inventory.extract(fuzzItemKey, input[x].amount(), Actionable.MODULATE, this.machineSrc);

                                            if (extracted > 0) {
                                                this.postChange(fuzzItemKey, this.machineSrc);
                                                ic.setInventorySlotContents(x, fuzzItemKey.toStack((int) extracted));
                                                found = true;
                                                break;
                                            }
                                        }
                                    }
                                } else if (input[x].what() instanceof AEItemKey itemKey) {
                                    final long extracted = this.inventory.extract(itemKey, input[x].amount(), Actionable.MODULATE, this.machineSrc);

                                    if (extracted > 0) {
                                        this.postChange(itemKey, this.machineSrc);
                                        ic.setInventorySlotContents(x, itemKey.toStack((int) extracted));
                                        if (extracted == input[x].amount()) {
                                            found = true;
                                            continue;
                                        }
                                    }
                                } else {
                                    final AEKey what = input[x].what();
                                    final long extracted = this.inventory.extract(what, input[x].amount(), Actionable.MODULATE, this.machineSrc);

                                    if (extracted > 0) {
                                        this.postChange(what, this.machineSrc);
                                        extraInputs.add(new GenericStack(what, extracted));
                                        if (extracted == input[x].amount()) {
                                            found = true;
                                            continue;
                                        }
                                    }
                                }
                                // An ingredient that is not an item cannot travel in an InventoryCrafting,
                                // so this pattern cannot be pushed until the interface can carry one
                                // (stage 3 of the fluids decomposition). Leaving `found` false breaks out
                                // below, and nothing was taken from the network.
                                //
                                // The test used to sit *after* the extraction: a fluid ingredient was
                                // pulled out of storage with MODULATE, then rejected for not being an
                                // AEItemKey, and never reached `ic` - which is the only thing the
                                // put-back loop below restores from. The fluid was simply destroyed, and
                                // only the first one, because the loop breaks on the first failure.

                                if (!found) {
                                    break;
                                }
                            }
                        }

                        if (!found) {
                            this.putBack(details, ic, extraInputs, fabricatedInputs);
                            ic = null;
                            break;
                        }

                        extras = extraInputs.toArray(new GenericStack[0]);
                        fabricated = fabricatedInputs.toArray(new GenericStack[0]);
                    }

                    final boolean pushed = m.pushPattern(details, ic, extras);
                    this.recordPush(details, m, pushed);

                    if (pushed) {
                        this.somethingChanged = true;
                        this.remainingOperations--;

                        for (final GenericStack out : details.getCondensedOutputs()) {
                            if (out == null) {
                                continue;
                            }
                            this.postChange(out.what(), this.machineSrc);
                            this.waitingFor.add(out.what(), out.amount());
                            this.postCraftingStatusChange(out.what());
                        }

                        if (details.isCraftable()) {
                            for (int x = 0; x < ic.getSizeInventory(); x++) {
                                final ItemStack output = Platform.getRemainingItem(details, x, ic.getStackInSlot(x), true);
                                if (!output.isEmpty()) {
                                    final AEItemKey key = AEItemKey.of(output);
                                    if (key != null) {
                                        this.postChange(key, this.machineSrc);
                                        this.waitingFor.add(key, output.getCount());
                                        this.postCraftingStatusChange(key);
                                    }
                                }
                            }
                        }

                        ic = null; // hand off complete!
                        this.markDirty();

                        e.getValue().value--;
                        if (e.getValue().value <= 0) {
                            continue;
                        }

                        if (this.remainingOperations == 0) {
                            return;
                        }
                    }
                }

                if (ic != null) {
                    this.putBack(details, ic, Arrays.asList(extras), Arrays.asList(fabricated));
                }
            } else {
                // Nothing that happens later in this tick can make the missing ingredients appear, so stop
                // asking until the next one. Still in this.tasks - only this tick's working copy loses it.
                i.remove();
            }
        }
    }

    /**
     * How long a medium that keeps refusing a pattern is left alone, in ticks. A second is long enough to be
     * worth having and short enough that a destination which frees up is not noticeably delayed.
     */
    private static final long MAX_PUSH_BACKOFF = 20;

    /**
     * Whether this medium is currently being left alone for this pattern.
     * <p>
     * {@link ICraftingMedium#isBusy()} only means "has something queued to send". An interface pointed at a
     * chest that is full is not busy, so it takes a whole {@code pushPattern} - building an adaptor and
     * simulating the insert of every stack on the table - to find out it will refuse, and it did that every
     * tick, for every pattern. A refusal now doubles the wait before asking again, and one success clears
     * it, so a destination that starts accepting is missed at most once.
     */
    private boolean backedOff(final ICraftingPatternDetails details, final ICraftingMedium medium) {
        final Map<ICraftingMedium, long[]> mediums = this.pushBackoff.get(details);
        if (mediums == null) {
            return false;
        }

        final long[] entry = mediums.get(medium);
        return entry != null && this.getWorld().getTotalWorldTime() < entry[1];
    }

    private void recordPush(final ICraftingPatternDetails details, final ICraftingMedium medium, final boolean pushed) {
        if (pushed) {
            final Map<ICraftingMedium, long[]> mediums = this.pushBackoff.get(details);
            if (mediums != null) {
                mediums.remove(medium);
                if (mediums.isEmpty()) {
                    this.pushBackoff.remove(details);
                }
            }
            return;
        }

        final long[] entry = this.pushBackoff.computeIfAbsent(details, d -> new HashMap<>())
                .computeIfAbsent(medium, m -> new long[2]);
        entry[0] = entry[0] <= 0 ? 1 : Math.min(entry[0] * 2, MAX_PUSH_BACKOFF);
        entry[1] = this.getWorld().getTotalWorldTime() + entry[0];
    }

    /**
     * Returns a prepared crafting table to the CPU's inventory after no medium would take it.
     * <p>
     * All three parts have to come back together, because all three were taken out together: the items on
     * the table, the ingredients carried beside it that an {@link InventoryCrafting} cannot express, and the
     * keys that were drawn to assemble a container. That last group is the reason the table itself cannot be
     * read back slot by slot - a fabricated container is not something the network ever held, and putting it
     * into storage would be conjuring an item out of a fluid.
     */
    private void putBack(final ICraftingPatternDetails details, final InventoryCrafting ic,
            final Iterable<GenericStack> beside, final Iterable<GenericStack> assembled) {
        for (int x = 0; x < ic.getSizeInventory(); x++) {
            if (details.isContainerFabricated(x)) {
                continue;
            }

            final ItemStack is = ic.getStackInSlot(x);
            if (!is.isEmpty()) {
                final AEItemKey key = AEItemKey.of(is);
                if (key != null) {
                    this.inventory.insert(key, is.getCount(), Actionable.MODULATE, this.machineSrc);
                }
            }
        }

        for (final GenericStack g : beside) {
            this.inventory.insert(g.what(), g.amount(), Actionable.MODULATE, this.machineSrc);
        }

        for (final GenericStack g : assembled) {
            this.inventory.insert(g.what(), g.amount(), Actionable.MODULATE, this.machineSrc);
        }
    }

    private void storeItems() {
        Preconditions.checkState(isComplete, "CPU should be complete to prevent re-insertion when dumping items");
        final IGrid g = this.getGrid();

        if (g == null) {
            return;
        }

        final IStorageService sg = g.getCache(IStorageService.class);
        final MEStorage networkStorage = sg.getInventory();

        final KeyCounter snapshot = this.inventory.getAvailableStacks();
        for (final AEKey what : new ArrayList<>(snapshot.keySet())) {
            final long amount = snapshot.get(what);
            if (amount <= 0) {
                continue;
            }

            this.postChange(what, this.machineSrc);

            final long extracted = this.inventory.extract(what, amount, Actionable.MODULATE, this.machineSrc);
            final long inserted = networkStorage.insert(what, extracted, Actionable.MODULATE, this.machineSrc);

            // The network was unable to receive all of the items, i.e. no or not enough storage space left
            if (inserted < extracted) {
                this.inventory.insert(what, extracted - inserted, Actionable.MODULATE, this.machineSrc);
            }
        }

        if (this.inventory.getAvailableStacks().isEmpty()) {
            this.inventory = new MECraftingInventory();
        }

        this.markDirty();
    }

    public ICraftingLink submitJob(final IGrid g, final ICraftingJob job, final IActionSource src, final ICraftingRequester requestingMachine) {
        if (!this.tasks.isEmpty() || !this.waitingFor.isEmpty()) {
            return null;
        }

        if (!(job instanceof CraftingJob)) {
            return null;
        }

        if (this.isBusy() || !this.isActive() || this.availableStorage < job.getByteTotal()) {
            return null;
        }

        final IStorageService sg = g.getCache(IStorageService.class);
        final MEStorage networkStorage = sg.getInventory();
        final MECraftingInventory ci = new MECraftingInventory(networkStorage, true, false, false);

        try {
            this.waitingFor.clear(); // see completeJob()
            ((CraftingJob) job).getTree().setJob(ci, this, src);
            if (ci.commit(src)) {
                this.finalOutput = job.getOutput();
                this.requestedAmount = this.finalOutput == null ? 0 : this.finalOutput.amount();
                this.waiting = false;
                this.isComplete = false;

                // Store the requesting player if present.
                if (src instanceof PlayerSource playerSource && playerSource.player().isPresent()) {
                    final EntityPlayer player = playerSource.player().get();
                    this.requestingPlayerUUID = player.getUniqueID();
                    this.requestingPlayerName = player.getName();
                } else {
                    this.requestingPlayerUUID = null;
                    this.requestingPlayerName = null;
                }

                this.markDirty();

                this.updateCPU();
                final String craftID = this.generateCraftingID();

                this.myLastLink = new CraftingLink(this.generateLinkData(craftID, requestingMachine == null, false), this);

                this.prepareElapsedTime();

                if (requestingMachine == null) {
                    return this.myLastLink;
                }

                final ICraftingLink whatLink = new CraftingLink(this.generateLinkData(craftID, false, true), requestingMachine);

                this.submitLink(this.myLastLink);
                this.submitLink(whatLink);

                // Let listeners know about everything now sitting in/queued for this CPU, so the GUI can
                // populate its stored/active/pending rows as soon as the job starts (mirrors the old
                // post-submit postChange(ALL) loop).
                final KeyCounter changed = new KeyCounter();
                this.getListOfItem(changed, CraftingItemList.ALL);
                for (final AEKey key : changed.keySet()) {
                    this.postChange(key, this.machineSrc);
                }

                return whatLink;
            } else {
                this.tasks.clear();
            }
        } catch (final CraftBranchFailure e) {
            this.tasks.clear();
            // AELog.error( e );
        }

        return null;
    }

    @Override
    public boolean isBusy() {

        this.tasks.entrySet().removeIf(taskProgressEntry -> taskProgressEntry.getValue().value <= 0);

        if (!this.waitingFor.isEmpty() || !this.tasks.isEmpty()) {
            this.updateElapsedTime();
        }

        return !this.tasks.isEmpty() || !this.waitingFor.isEmpty();
    }

    @Override
    public IActionSource getActionSource() {
        return this.machineSrc;
    }

    @Override
    public long getAvailableStorage() {
        return this.availableStorage;
    }

    @Override
    public int getCoProcessors() {
        return this.accelerator;
    }

    @Override
    public String getName() {
        return this.myName;
    }

    public boolean isActive() {
        final TileCraftingTile core = this.getCore();

        if (core == null) {
            return false;
        }

        final IGridNode node = core.getActionableNode();
        if (node == null) {
            return false;
        }

        return node.isActive();
    }

    private String generateCraftingID() {
        final long now = System.currentTimeMillis();
        final int hash = System.identityHashCode(this);
        final int hmm = this.finalOutput == null ? 0 : this.finalOutput.hashCode();

        return Long.toString(now, Character.MAX_RADIX) + '-' + Integer.toString(hash, Character.MAX_RADIX) + '-' + Integer.toString(hmm, Character.MAX_RADIX);
    }

    private NBTTagCompound generateLinkData(final String craftingID, final boolean standalone, final boolean req) {
        final NBTTagCompound tag = new NBTTagCompound();

        tag.setString("CraftID", craftingID);
        tag.setBoolean("canceled", false);
        tag.setBoolean("done", false);
        tag.setBoolean("standalone", standalone);
        tag.setBoolean("req", req);

        return tag;
    }

    private void submitLink(final ICraftingLink myLastLink2) {
        if (this.getGrid() != null) {
            final CraftingGridCache cc = this.getGrid().getCache(ICraftingGrid.class);
            cc.addLink((CraftingLink) myLastLink2);
        }
    }

    public void getListOfItem(final KeyCounter list, final CraftingItemList whichList) {
        switch (whichList) {
            case ACTIVE:
                for (final var entry : this.waitingFor) {
                    list.add(entry.getKey(), entry.getLongValue());
                }
                break;
            case PENDING:
                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (final GenericStack out : t.getKey().getCondensedOutputs()) {
                        if (out == null) {
                            continue;
                        }
                        list.add(out.what(), out.amount() * t.getValue().value);
                    }
                }
                break;
            case STORAGE:
                this.inventory.getAvailableStacks(list);
                break;
            default:
            case ALL:
                this.inventory.getAvailableStacks(list);

                for (final var entry : this.waitingFor) {
                    list.add(entry.getKey(), entry.getLongValue());
                }

                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (final GenericStack out : t.getKey().getCondensedOutputs()) {
                        if (out == null) {
                            continue;
                        }
                        list.add(out.what(), out.amount() * t.getValue().value);
                    }
                }
                break;
        }
    }

    public void addStorage(final AEKey what, final long amount) {
        this.inventory.insert(what, amount, Actionable.MODULATE, null);
    }

    public void addEmitable(final AEKey what, final long amount) {
        this.waitingFor.add(what, amount);
        this.postCraftingStatusChange(what);
    }

    public void addCrafting(final ICraftingPatternDetails details, final long crafts) {
        TaskProgress i = this.tasks.get(details);

        if (i == null) {
            this.tasks.put(details, i = new TaskProgress());
        }

        i.value += crafts;
    }

    public GenericStack getItemStack(final AEKey what, final CraftingItemList storage2) {
        long amount;

        switch (storage2) {
            case STORAGE:
                amount = this.inventory.getAvailableStacks().get(what);
                break;
            case ACTIVE:
                amount = this.waitingFor.get(what);
                break;
            case PENDING:
                amount = 0;
                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (final GenericStack out : t.getKey().getCondensedOutputs()) {
                        if (out != null && out.what().equals(what)) {
                            amount += out.amount() * t.getValue().value;
                        }
                    }
                }
                break;
            default:
            case ALL:
                throw new IllegalStateException("Invalid Operation");
        }

        return new GenericStack(what, amount);
    }

    public void writeToNBT(final NBTTagCompound data) {
        final NBTTagCompound finalOutputTag = new NBTTagCompound();
        GenericStack.writeTag(finalOutputTag, this.finalOutput);
        data.setTag("finalOutput", finalOutputTag);

        data.setTag("inventory", this.writeKeyCounter(this.inventory.getAvailableStacks()));
        data.setBoolean("waiting", this.waiting);
        data.setBoolean("isComplete", this.isComplete);

        if (this.myLastLink != null) {
            final NBTTagCompound link = new NBTTagCompound();
            this.myLastLink.writeToNBT(link);
            data.setTag("link", link);
        }

        final NBTTagList list = new NBTTagList();
        for (final Entry<ICraftingPatternDetails, TaskProgress> e : this.tasks.entrySet()) {
            final NBTTagCompound item = e.getKey().getPattern().writeToNBT(new NBTTagCompound());
            item.setLong("craftingProgress", e.getValue().value);
            list.appendTag(item);
        }
        data.setTag("tasks", list);

        data.setTag("waitingFor", this.writeKeyCounter(this.waitingFor));

        data.setLong("elapsedTime", this.getElapsedTime());
        data.setLong("requestedAmount", this.requestedAmount);
        data.setLong("startItemCount", this.getStartItemCount());
        data.setLong("remainingItemCount", this.getRemainingItemCount());

        if (Platform.isServer() && this.requestingPlayerUUID != null) {
            data.setUniqueId("requestingPlayerUUID", this.requestingPlayerUUID);
        }
        if (this.requestingPlayerName != null) {
            data.setString("requestingPlayerName", this.requestingPlayerName);
        }
    }

    private NBTTagList writeKeyCounter(final KeyCounter counter) {
        final NBTTagList out = new NBTTagList();

        for (final var entry : counter) {
            final NBTTagCompound tag = new NBTTagCompound();
            GenericStack.writeTag(tag, new GenericStack(entry.getKey(), entry.getLongValue()));
            out.appendTag(tag);
        }

        return out;
    }

    private KeyCounter readKeyCounter(final NBTTagList tag) {
        final KeyCounter out = new KeyCounter();

        if (tag == null) {
            return out;
        }

        for (int x = 0; x < tag.tagCount(); x++) {
            final GenericStack stack = GenericStack.readTag(tag.getCompoundTagAt(x));
            if (stack != null) {
                out.add(stack.what(), stack.amount());
            }
        }

        return out;
    }

    void done() {
        final TileCraftingTile core = this.getCore();

        core.setCoreBlock(true);

        if (core.getPreviousState() != null) {
            this.readFromNBT(core.getPreviousState());
            core.setPreviousState(null);
        }

        this.updateCPU();
        this.updateName();
    }

    public void readFromNBT(final NBTTagCompound data) {
        this.finalOutput = data.hasKey("finalOutput") ? GenericStack.readTag(data.getCompoundTag("finalOutput")) : null;

        for (final var entry : this.readKeyCounter((NBTTagList) data.getTag("inventory"))) {
            this.inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, this.machineSrc);
        }

        this.waiting = data.getBoolean("waiting");
        this.isComplete = data.getBoolean("isComplete");

        if (data.hasKey("link")) {
            final NBTTagCompound link = data.getCompoundTag("link");
            this.myLastLink = new CraftingLink(link, this);
            this.submitLink(this.myLastLink);
        }

        final NBTTagList list = data.getTagList("tasks", 10);
        for (int x = 0; x < list.tagCount(); x++) {
            final NBTTagCompound item = list.getCompoundTagAt(x);
            final ItemStack pattern = new ItemStack(item);
            if (!pattern.isEmpty() && pattern.getItem() instanceof ICraftingPatternItem) {
                final ICraftingPatternItem cpi = (ICraftingPatternItem) pattern.getItem();
                final ICraftingPatternDetails details = cpi.getPatternForItem(pattern, this.getWorld());
                if (details != null) {
                    final TaskProgress tp = new TaskProgress();
                    tp.value = item.getLong("craftingProgress");
                    this.tasks.put(details, tp);
                }
            }
        }

        this.waitingFor = this.readKeyCounter((NBTTagList) data.getTag("waitingFor"));
        for (final var entry : this.waitingFor) {
            this.postCraftingStatusChange(entry.getKey());
        }

        this.lastTime = System.nanoTime();
        this.elapsedTime = data.getLong("elapsedTime");
        this.requestedAmount = data.getLong("requestedAmount");
        this.startItemCount = data.getLong("startItemCount");
        this.remainingItemCount = data.getLong("remainingItemCount");

        if (Platform.isServer() && data.hasUniqueId("requestingPlayerUUID")) {
            this.requestingPlayerUUID = data.getUniqueId("requestingPlayerUUID");
        }
        this.requestingPlayerName = data.hasKey("requestingPlayerName")
                ? data.getString("requestingPlayerName")
                : null;
    }

    public void updateName() {
        this.myName = "";
        for (final TileCraftingTile te : this.tiles) {

            if (te.hasCustomInventoryName()) {
                if (this.myName.length() > 0) {
                    this.myName += ' ' + te.getCustomInventoryName();
                } else {
                    this.myName = te.getCustomInventoryName();
                }
            }
        }
    }

    private World getWorld() {
        return this.getCore().getWorld();
    }

    /**
     * @return how much of {@code what} this job is currently waiting to receive back.
     */
    public long making(final AEKey what) {
        return this.waitingFor.get(what);
    }

    public void breakCluster() {
        final TileCraftingTile t = this.getCore();

        if (t != null) {
            t.breakCluster();
        }
    }

    private void prepareElapsedTime() {
        this.lastTime = System.nanoTime();
        this.elapsedTime = 0;

        final KeyCounter list = new KeyCounter();

        this.getListOfItem(list, CraftingItemList.ACTIVE);
        this.getListOfItem(list, CraftingItemList.PENDING);

        long itemCount = 0;
        for (final var entry : list) {
            itemCount += entry.getLongValue();
        }

        this.startItemCount = itemCount;
        this.remainingItemCount = itemCount;
    }

    private void updateElapsedTime() {
        final long nextStartTime = System.nanoTime();
        this.elapsedTime = this.getElapsedTime() + nextStartTime - this.lastTime;
        this.lastTime = nextStartTime;
    }

    @Override
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    @Override
    public String getSourcePlayer() {
        if (this.requestingPlayerName != null) {
            return this.requestingPlayerName;
        }

        if (Platform.isServer() && this.requestingPlayerUUID != null) {
            final EntityPlayer player = AppEng.proxy.getPlayerByUUID(this.requestingPlayerUUID);
            return player == null ? null : player.getName();
        }

        return null;
    }

    @Override
    public long getRemainingItemCount() {
        return this.remainingItemCount;
    }

    @Override
    public long getStartItemCount() {
        return this.startItemCount;
    }

    private static class TaskProgress {
        private long value;
    }
}
