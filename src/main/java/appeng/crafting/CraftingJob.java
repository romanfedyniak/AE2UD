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

package appeng.crafting;


import appeng.api.config.Actionable;
import appeng.api.config.CraftingMode;
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
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInformPlayer;
import appeng.crafting.solver.CraftingSolver;
import appeng.crafting.solver.SolverPattern;
import appeng.crafting.solver.SolverPlan;
import appeng.crafting.solver.SolverTooLargeException;
import appeng.hooks.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.google.common.base.Stopwatch;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * One request, worked out on a background thread.
 * <p>
 * The working out itself lives in {@link CraftingSolver}; this holds what the request was, what the network
 * looked like when it was asked, and what came back. Notably there is only one attempt: a shortfall is a
 * number the solver reports rather than a failure it throws, so the plan that says "this cannot be made and
 * here is what is missing" is the same plan as the one that would have made it. The old tree ran the whole
 * calculation twice to answer that, once in earnest and once again to find out why.
 */
public class CraftingJob implements Runnable, ICraftingJob {
    private static final String LOG_CRAFTING_JOB = "CraftingJob (%s) issued by %s requesting [%s] using %s bytes took %s us";
    private static final String LOG_MACHINE_SOURCE_DETAILS = "Machine[object=%s, %s]";

    /** What the network held when the job was asked. Kept, so the plan can be read against it afterwards. */
    private final KeyCounter stock;
    private final World world;

    private final Object monitor = new Object();
    private final Stopwatch tickSpreadingWatch = Stopwatch.createUnstarted();
    private final Stopwatch solverWatch = Stopwatch.createUnstarted();
    private final ICraftingGrid cc;
    private final GenericStack output;
    private final CraftingMode craftingMode;
    @Nullable
    private final ICraftingPatternDetails rootPattern;
    private final IActionSource actionSrc;
    private final ICraftingCallback callback;

    @Nullable
    private SolverPlan plan;
    private boolean running = false;
    private boolean done = false;
    private int time;
    private int incTime;

    public CraftingJob(final World w, final IGrid grid, final IActionSource actionSrc, final GenericStack what, final ICraftingCallback callback) {
        this(w, grid, actionSrc, what, null, CraftingMode.STANDARD, callback);
    }

    public CraftingJob(final World w, final IGrid grid, final IActionSource actionSrc, final GenericStack what, final CraftingMode mode, final ICraftingCallback callback) {
        this(w, grid, actionSrc, what, null, mode, callback);
    }

    /**
     * @param rootPattern if non-null, satisfies {@code what} directly instead of asking the network
     * for a registered pattern - see {@link appeng.crafting.VirtualPatternDetails}.
     */
    public CraftingJob(final World w, final IGrid grid, final IActionSource actionSrc, final GenericStack what, final ICraftingPatternDetails rootPattern, final ICraftingCallback callback) {
        this(w, grid, actionSrc, what, rootPattern, CraftingMode.STANDARD, callback);
    }

    public CraftingJob(final World w, final IGrid grid, final IActionSource actionSrc, final GenericStack what, final ICraftingPatternDetails rootPattern, final CraftingMode mode, final ICraftingCallback callback) {
        this.world = w;
        this.output = what;
        this.actionSrc = actionSrc;
        this.craftingMode = mode;
        this.rootPattern = rootPattern;
        this.callback = callback;

        this.cc = grid.getCache(ICraftingGrid.class);
        final IStorageService sg = grid.getCache(IStorageService.class);
        // Kept whole. What is already there does not count towards the order - the solver reserves the
        // requested key rather than spending it - but a self-feeding pattern needs one of it to start,
        // and zeroing it here would make that first one unreachable.
        this.stock = sg.getInventory().getAvailableStacks();
    }

    @Override
    public void run() {
        try {
            TickHandler.INSTANCE.registerCraftingSimulation(this.world, this);
            this.handlePausing();

            final NetworkCraftingSource source = new NetworkCraftingSource(this.cc, this.world, this.stock,
                    this.output.what(), this.rootPattern, this.isRequestedByPlayer());

            this.solverWatch.reset().start();
            this.plan = new CraftingSolver(source, appeng.crafting.solver.SolverLimits.DEFAULT,
                    this::pauseFromSolver).solve(this.output.what(), this.output.amount(), this.stock);
            this.solverWatch.stop();

            this.logCraftingJob(this.isSimulation() ? "simulated" : "real", this.solverWatch);
        } catch (final Cancelled e) {
            AELog.crafting("Crafting calculation canceled.");
        } catch (final SolverTooLargeException e) {
            AELog.crafting("Crafting calculation refused: %s", e.getMessage());
        } catch (final Throwable t) {
            this.finish();
            throw new IllegalStateException(t);
        }

        this.finish();
    }

    /**
     * The solver has no checked exceptions, so a cancelled job leaves through one of these and {@link #run}
     * unwraps it. Nothing else ever sees it.
     */
    private static final class Cancelled extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private void pauseFromSolver() {
        try {
            this.handlePausing();
        } catch (final InterruptedException e) {
            throw new Cancelled();
        }
    }

    void handlePausing() throws InterruptedException {
        if (!this.actionSrc.player().isPresent() && this.incTime > 100) {
            this.incTime = 0;
            synchronized (this.monitor) {
                if (this.tickSpreadingWatch.elapsed(TimeUnit.MICROSECONDS) > this.time) {
                    this.running = false;
                    if (this.solverWatch.isRunning()) {
                        this.solverWatch.stop();
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

        synchronized (this.monitor) {
            this.running = false;
            this.done = true;
            this.monitor.notify();
        }
    }

    @Override
    public boolean isSimulation() {
        // A shortfall is what makes a job a simulation, except when the request was to plan around one: a
        // forced start is a real job that waits for what it lacks to be brought.
        return this.plan == null
                || !this.plan.isComplete() && this.craftingMode != CraftingMode.IGNORE_MISSING;
    }

    @Override
    public CraftingMode getCraftingMode() {
        return this.craftingMode;
    }

    @Override
    public long getByteTotal() {
        return this.plan == null ? 0 : this.plan.getBytes();
    }

    @Override
    public void populatePlan(final KeyCounter plan) {
        final KeyCounter used = new KeyCounter();
        final KeyCounter requestable = new KeyCounter();
        this.populatePlan(used, requestable, new KeyCounter());

        plan.addAll(used);
        plan.addAll(requestable);
    }

    /**
     * Same information as {@link #populatePlan(KeyCounter)}, but kept as two separate counters instead of
     * merged into one. {@link ICraftingJob#populatePlan(KeyCounter)} (frozen API) only has room for a single
     * {@link KeyCounter} argument, so it cannot carry both "already have this many in storage / missing" and
     * "this many will be produced by crafting" for the same key.
     */
    public void populatePlan(final KeyCounter used, final KeyCounter requestable) {
        this.populatePlan(used, requestable, new KeyCounter());
    }

    /**
     * Adds the number of pattern executions behind every crafted output to the split plan.
     */
    public void populatePlan(final KeyCounter used, final KeyCounter requestable, final KeyCounter craftingSteps) {
        if (this.plan == null) {
            return;
        }

        used.addAll(this.plan.getUsed());
        // What is lacking is shown beside what was drawn, the way the old tree reported it: both are things
        // the network has to find, and only one of them it already has.
        used.addAll(this.plan.getMissing());

        requestable.addAll(this.plan.getProduced());
        requestable.addAll(this.plan.getEmitted());

        for (final Map.Entry<SolverPattern, Long> entry : this.plan.getCrafts().entrySet()) {
            for (final GenericStack out : entry.getKey().getOutputs()) {
                craftingSteps.add(out.what(), entry.getValue());
            }
        }
    }

    /**
     * Hands the finished plan to a crafting cpu: what it needs is taken out of the network and put into the
     * cpu, what nothing is making is promised to it, and every pattern is queued.
     *
     * @throws CraftBranchFailure when the network no longer holds something the plan counted on, which can
     *                            happen between working the plan out and starting it.
     */
    public void setJob(final MECraftingInventory storage, final CraftingCPUCluster cpu,
            final IActionSource src) throws CraftBranchFailure {
        if (this.plan == null) {
            return;
        }

        for (final var entry : this.plan.getUsed()) {
            final AEKey key = entry.getKey();
            final long amount = entry.getLongValue();

            if (amount <= 0) {
                continue;
            }

            final long extracted = storage.extract(key, amount, Actionable.MODULATE, src);

            if (extracted != amount) {
                tellThePlayer(src, key, amount, extracted);
                throw new CraftBranchFailure(key, amount);
            }

            cpu.addStorage(key, extracted);
        }

        for (final var entry : this.plan.getEmitted()) {
            cpu.addEmitable(entry.getKey(), entry.getLongValue());
        }

        // What the job was told to ignore is waited for in exactly the same way. It stays counted as missing
        // rather than as emitted, so the plan still shows it as something the network has not got.
        if (this.craftingMode == CraftingMode.IGNORE_MISSING) {
            for (final var entry : this.plan.getMissing()) {
                cpu.addEmitable(entry.getKey(), entry.getLongValue());
            }
        }

        for (final Map.Entry<SolverPattern, Long> entry : this.plan.getCrafts().entrySet()) {
            cpu.addCrafting((ICraftingPatternDetails) entry.getKey().getSource(), entry.getValue());
        }
    }

    private static void tellThePlayer(final IActionSource src, final AEKey key, final long wanted,
            final long got) {
        if (!src.player().isPresent()) {
            return;
        }

        try {
            NetworkHandler.instance().sendTo(got <= 0
                    ? new PacketInformPlayer(new GenericStack(key, wanted), null,
                            PacketInformPlayer.InfoType.NO_ITEMS_EXTRACTED)
                    : new PacketInformPlayer(new GenericStack(key, wanted), new GenericStack(key, got),
                            PacketInformPlayer.InfoType.PARTIAL_ITEM_EXTRACTION),
                    (EntityPlayerMP) src.player().get());
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    /**
     * Returns how much of a key existed when this crafting calculation started.
     */
    public long getAvailableAtStart(final AEKey what) {
        return this.stock.get(what);
    }

    @Override
    public GenericStack getOutput() {
        return this.output;
    }

    public boolean isDone() {
        return this.done;
    }

    /**
     * The finished plan, or null while the job is still being worked out or if it was cancelled.
     */
    @Nullable
    public SolverPlan getPlan() {
        return this.plan;
    }

    /**
     * Whether a player asked for this job rather than a machine. Some patterns are offered only to a player.
     */
    boolean isRequestedByPlayer() {
        return this.actionSrc.player().isPresent();
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

    /**
     * The network this job was worked out on, so a caller can ask it what a pattern would be run by.
     */
    public ICraftingGrid getCraftingGrid() {
        return this.cc;
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

            AELog.crafting(LOG_CRAFTING_JOB, type, actionSource, itemToOutput, this.getByteTotal(), elapsedTime);
        }
    }
}
