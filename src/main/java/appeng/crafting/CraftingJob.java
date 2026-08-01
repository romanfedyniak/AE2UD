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

package appeng.crafting;


import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.DimensionalCoord;
import appeng.core.AELog;
import appeng.hooks.TickHandler;
import com.google.common.base.Stopwatch;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;


public class CraftingJob implements Runnable, ICraftingJob {
    private static final String LOG_CRAFTING_JOB = "CraftingJob (%s) issued by %s requesting [%s] using %s bytes took %s us";
    private static final String LOG_MACHINE_SOURCE_DETAILS = "Machine[object=%s, %s]";

    private final MECraftingInventory original;
    private final World world;
    private final KeyCounter crafting = new KeyCounter();
    private final KeyCounter missing = new KeyCounter();

    private final HashMap<String, TwoIntegers> opsAndMultiplier = new HashMap<>();
    private final Object monitor = new Object();
    private final Stopwatch tickSpreadingWatch = Stopwatch.createUnstarted();
    private final Stopwatch craftingTreeWatch = Stopwatch.createUnstarted();
    private final ICraftingGrid cc;
    private CraftingTreeNode tree;
    private final GenericStack output;
    private boolean simulate = false;
    private MECraftingInventory availableCheck;
    private long bytes = 0;
    private final IActionSource actionSrc;
    private final ICraftingCallback callback;
    private boolean running = false;
    private boolean done = false;
    private int time;
    private int incTime;

    private World wrapWorld(final World w) {
        return w;
    }

    public CraftingJob(final World w, final IGrid grid, final IActionSource actionSrc, final GenericStack what, final ICraftingCallback callback) {
        this.world = this.wrapWorld(w);
        this.output = what;
        this.actionSrc = actionSrc;

        this.callback = callback;

        this.cc = grid.getCache(ICraftingGrid.class);
        final IStorageService sg = grid.getCache(IStorageService.class);
        this.original = new MECraftingInventory(sg.getCachedInventory());

        this.setTree(this.getCraftingTree(cc, what));
        this.availableCheck = null;
    }

    private CraftingTreeNode getCraftingTree(final ICraftingGrid cc, final GenericStack what) {
        return new CraftingTreeNode(cc, this, what.what(), null, -1, 0);
    }

    void refund(final AEKey what, final long amount) {
        this.availableCheck.insert(what, amount, Actionable.MODULATE, this.actionSrc);
    }

    long checkUse(final AEKey what, final long amount) {
        return this.availableCheck.extract(what, amount, Actionable.MODULATE, this.actionSrc);
    }

    long checkAvailable(final AEKey what, final long amount) {
        return this.availableCheck.extract(what, amount, Actionable.SIMULATE, this.actionSrc);
    }

    void addTask(final AEKey what, final long amount, final ICraftingPatternDetails details, final int depth) {
        if (amount > 0) {
            this.crafting.add(what, amount);
        }
    }

    void addMissing(final AEKey what, final long amount) {
        this.missing.add(what, amount);
    }

    @Override
    public void run() {
        try {
            try {
                TickHandler.INSTANCE.registerCraftingSimulation(this.world, this);
                this.handlePausing();

                final MECraftingInventory craftingInventory = new MECraftingInventory(this.original, true, false, true);
                craftingInventory.ignore(this.output.what());

                this.availableCheck = new MECraftingInventory(this.original, false, false, false);
                craftingTreeWatch.reset().start();
                this.getTree().request(craftingInventory, this.output.amount(), this.actionSrc);
                craftingTreeWatch.stop();
                this.getTree().dive(this);

                for (final String s : this.opsAndMultiplier.keySet()) {
                    final TwoIntegers ti = this.opsAndMultiplier.get(s);
                    AELog.crafting(s + " * " + ti.times + " = " + (ti.perOp * ti.times));
                }

                if (actionSrc.player().isPresent()) {
                    this.logCraftingJob("simulated, success", craftingTreeWatch);
                } else {
                    this.logCraftingJob("real, success", craftingTreeWatch);
                }
            } catch (final CraftBranchFailure e) {
                this.simulate = true;

                try {
                    if (actionSrc.player().isPresent()) {
                        final MECraftingInventory craftingInventory = new MECraftingInventory(this.original, true, false, true);
                        craftingInventory.ignore(this.output.what());

                        this.getTree().setSimulate();
                        this.availableCheck = new MECraftingInventory(this.original, false, false, false);
                        craftingTreeWatch.reset().start();
                        this.getTree().request(craftingInventory, this.output.amount(), this.actionSrc);
                        craftingTreeWatch.stop();
                        this.getTree().dive(this);

                        for (final String s : this.opsAndMultiplier.keySet()) {
                            final TwoIntegers ti = this.opsAndMultiplier.get(s);
                            AELog.crafting(s + " * " + ti.times + " = " + (ti.perOp * ti.times));
                        }

                        this.logCraftingJob("simulated, failed", craftingTreeWatch);
                    } else {
                        this.logCraftingJob("real, failed", craftingTreeWatch);
                    }
                } catch (final CraftBranchFailure | CraftingCalculationFailure e1) {
                    AELog.debug(e1);
                } catch (final InterruptedException e1) {
                    AELog.crafting("Crafting calculation canceled.");
                    this.finish();
                    return;
                }
            } catch (final CraftingCalculationFailure f) {
                AELog.debug(f);
            } catch (final InterruptedException e1) {
                AELog.crafting("Crafting calculation canceled.");
                this.finish();
                return;
            }

            AELog.craftingDebug("crafting job now done");
        } catch (final Throwable t) {
            this.finish();
            throw new IllegalStateException(t);
        }

        this.finish();
    }

    void handlePausing() throws InterruptedException {
        if (!this.actionSrc.player().isPresent() && this.incTime > 100) {
            this.incTime = 0;
            synchronized (this.monitor) {
                if (this.tickSpreadingWatch.elapsed(TimeUnit.MICROSECONDS) > this.time) {
                    this.running = false;
                    if (this.craftingTreeWatch.isRunning()) {
                        this.craftingTreeWatch.stop();
                    }

                    if (this.tickSpreadingWatch.isRunning()) {
                        this.tickSpreadingWatch.stop();
                    }

                    this.monitor.notify();
                }

                if (!this.running) {
                    AELog.craftingDebug("crafting job will now sleep");

                    while (!this.running) {
                        this.monitor.wait();
                    }

                    AELog.craftingDebug("crafting job now active");
                }
            }
        }

        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        this.incTime++;
    }

    private void finish() {
        if (this.callback != null) {
            this.callback.calculationComplete(this);
        }

        this.availableCheck = null;

        synchronized (this.monitor) {
            this.running = false;
            this.done = true;
            this.monitor.notify();
        }
    }

    @Override
    public boolean isSimulation() {
        return this.simulate;
    }

    @Override
    public long getByteTotal() {
        return this.bytes;
    }

    @Override
    public void populatePlan(final KeyCounter plan) {
        if (this.getTree() == null) {
            return;
        }

        final KeyCounter used = new KeyCounter();
        final KeyCounter requestable = new KeyCounter();
        this.getTree().getPlan(used, requestable, new KeyCounter());

        plan.addAll(used);
        plan.addAll(requestable);
    }

    /**
     * Same information as {@link #populatePlan(KeyCounter)}, but kept as two separate counters
     * instead of merged into one. {@link ICraftingJob#populatePlan(KeyCounter)} (frozen API) only has
     * room for a single {@link KeyCounter} argument, so it cannot carry both "already have this many
     * in storage / missing" and "this many will be produced by crafting" for the same key the way the
     * old {@code IAEItemStack}, with its independent {@code stackSize}/{@code countRequestable}
     * fields, used to. Calling {@link #populatePlan(KeyCounter)} still works (it merges the two here),
     * but a caller that needs the old split - e.g. the crafting confirmation GUI, which sent the two
     * numbers to the client as two different packets - must call this overload directly on the
     * concrete {@link CraftingJob} instead of through the interface.
     */
    public void populatePlan(final KeyCounter used, final KeyCounter requestable) {
        this.populatePlan(used, requestable, new KeyCounter());
    }

    /**
     * Adds the number of pattern executions behind every crafted output to the split plan.
     */
    public void populatePlan(final KeyCounter used, final KeyCounter requestable, final KeyCounter craftingSteps) {
        if (this.getTree() != null) {
            this.getTree().getPlan(used, requestable, craftingSteps);
        }
    }

    /**
     * Returns how much of a key existed when this crafting calculation started.
     */
    public long getAvailableAtStart(final AEKey what) {
        return this.original.extract(what, Long.MAX_VALUE, Actionable.SIMULATE, this.actionSrc);
    }

    @Override
    public GenericStack getOutput() {
        return this.output;
    }

    public boolean isDone() {
        return this.done;
    }

    World getWorld() {
        return this.world;
    }

    /**
     * @return true if this needs more simulation
     */
    public boolean simulateFor(final int milli) {
        this.time = milli;

        synchronized (this.monitor) {
            if (this.done) {
                return false;
            }
            if (!this.actionSrc.player().isPresent()) {
                this.tickSpreadingWatch.reset();
                this.tickSpreadingWatch.start();
                this.monitor.notify();
            }
            this.running = true;
        }

        return true;
    }

    void addBytes(final long crafts) {
        this.bytes += crafts;
    }

    public CraftingTreeNode getTree() {
        return this.tree;
    }

    private void setTree(final CraftingTreeNode tree) {
        this.tree = tree;
    }

    private void logCraftingJob(String type, Stopwatch timer) {
        if (AELog.isCraftingLogEnabled()) {
            final String itemToOutput = this.output.toString();
            final long elapsedTime = timer.elapsed(TimeUnit.MICROSECONDS);
            final String actionSource;

            if (this.actionSrc.player().isPresent()) {
                final EntityPlayer player = this.actionSrc.player().get();

                actionSource = player.toString();
            } else if (this.actionSrc.machine().isPresent()) {
                final IActionHost machineSource = this.actionSrc.machine().get();
                final IGridNode actionableNode = machineSource.getActionableNode();
                final IGridHost machine = actionableNode.getMachine();
                final DimensionalCoord location = actionableNode.getGridBlock().getLocation();

                actionSource = String.format(LOG_MACHINE_SOURCE_DETAILS, machine, location);
            } else {
                actionSource = "[unknown source]";
            }

            AELog.crafting(LOG_CRAFTING_JOB, type, actionSource, itemToOutput, this.bytes, elapsedTime);
        }
    }

    private static class TwoIntegers {
        private final long perOp = 0;
        private final long times = 0;
    }
}
