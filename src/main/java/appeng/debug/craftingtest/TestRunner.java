/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.debug.craftingtest;


import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.crafting.CraftingJob;
import appeng.debug.TileCraftingTestRig;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Future;


/**
 * Walks a list of scenarios through the rig, one at a time, a tick at a time.
 * <p>
 * A tick at a time because none of this can be done in one: the crafting cache rebuilds its patterns on its
 * own tick, the planner runs on a pool thread, and a job that is actually performed takes as many ticks as it
 * takes. So this is a small state machine the rig drives, rather than a loop.
 */
public final class TestRunner {

    /** Ticks given to the crafting cache to notice a new set of patterns before a job is asked for. */
    private static final int SETTLE_TICKS = 3;

    /** Ticks a submitted job is watched before its cpu is believed to be finished rather than starting. */
    private static final int EXECUTION_GRACE_TICKS = 2;

    /**
     * How many times a scenario is planned before its time is reported. The first answer is thrown away: it
     * pays for the just-in-time compiler, and on a cold client it can be two orders out - the plain chain
     * measured 20 ms on its first run and 273 us on its second, with nothing about it changed.
     */
    private static final int TIMED_RUNS = 5;
    private static final int DISCARDED_RUNS = 1;

    /**
     * Planning microseconds after which a scenario stops repeating, however few runs it has had. A slow
     * answer needs no average to be recognised as slow, and the old planner has scenarios it takes half a
     * minute over.
     */
    private static final long REPEAT_BUDGET_MICROS = 2_000_000;

    private enum Phase {
        SETTLE,
        PLAN,
        EXECUTE
    }

    private final TileCraftingTestRig rig;
    private final TestKeys keys;
    private final List<TestScenario> scenarios;
    private final ICommandSender sender;
    @Nullable
    private final EntityPlayer player;
    private final boolean recordBaseline;

    private final Map<String, TestOutcome> outcomes = new LinkedHashMap<>();
    private final Map<String, TestOutcome> baseline;

    private int index;
    private Phase phase = Phase.SETTLE;
    private int phaseTicks;

    @Nullable
    private Future<ICraftingJob> future;
    @Nullable
    private TestOutcome current;
    private final List<Long> timings = new ArrayList<>();
    private volatile long planStartNanos;
    private volatile long planEndNanos;

    public TestRunner(final TileCraftingTestRig rig, final TestKeys keys, final List<TestScenario> scenarios,
            final ICommandSender sender, @Nullable final EntityPlayer player, final boolean recordBaseline) {
        this.rig = rig;
        this.keys = keys;
        this.scenarios = scenarios;
        this.sender = sender;
        this.player = player;
        this.recordBaseline = recordBaseline;
        this.baseline = recordBaseline ? new LinkedHashMap<>() : TestBaseline.load();

        this.beginScenario();
    }

    public String describeProgress() {
        return "Crafting test " + (this.index + 1) + "/" + this.scenarios.size() + ": "
                + this.scenarios.get(this.index).getName() + " (" + this.phase + ")";
    }

    /**
     * @return false once every scenario has been run and the report is out, after which the rig drops this.
     */
    public boolean tick() {
        this.phaseTicks++;

        try {
            switch (this.phase) {
                case SETTLE -> this.tickSettle();
                case PLAN -> this.tickPlan();
                case EXECUTE -> this.tickExecute();
            }
        } catch (final Exception e) {
            AELog.debug(e);
            this.finishScenario(this.failure(String.valueOf(e)));
        }

        return this.index < this.scenarios.size();
    }

    private TestScenario scenario() {
        return this.scenarios.get(this.index);
    }

    private void beginScenario() {
        final TestScenario scenario = this.scenario();

        this.rig.loadScenario(scenario.getPatterns(), scenario.getEmitable(), scenario.getStock());
        this.phase = Phase.SETTLE;
        this.phaseTicks = 0;
        this.future = null;
        this.current = null;
        this.timings.clear();
    }

    private void tickSettle() throws Exception {
        if (this.phaseTicks < SETTLE_TICKS) {
            return;
        }

        this.startJob();
    }

    /**
     * Asks the same question again. Planning reads a snapshot and writes nothing back, so a repeat gets the
     * same answer off the same network - only the time differs, which is the whole point of repeating.
     */
    private void startJob() throws Exception {
        final TestScenario scenario = this.scenario();
        final IGrid grid = this.rig.getProxy().getGrid();
        final ICraftingGrid cg = grid.getCache(ICraftingGrid.class);

        this.planEndNanos = 0;
        this.planStartNanos = System.nanoTime();
        // A player source on purpose: a machine's job is spread across ticks by design, and a run measured
        // that way would report the tick budget rather than anything about the planner.
        this.future = cg.beginCraftingJob(this.rig.getWorld(), grid, this.source(), scenario.getRequest(),
                scenario.getMode(), job -> this.planEndNanos = System.nanoTime());

        this.phase = Phase.PLAN;
        this.phaseTicks = 0;
    }

    private void tickPlan() throws Exception {
        if (this.future == null) {
            this.finishScenario(this.failure("the job was never started"));
            return;
        }

        if (!this.future.isDone()) {
            if (this.phaseTicks >= this.scenario().getTimeoutTicks()) {
                this.future.cancel(true);
                this.finishScenario(new TestOutcome(this.scenario().getName(), TestOutcome.Status.TIMEOUT));
            }
            return;
        }

        this.timings.add(this.planMicros());

        if (this.wantsAnotherRun()) {
            this.startJob();
            return;
        }

        final ICraftingJob job = this.future.get();
        final TestOutcome outcome = new TestOutcome(this.scenario().getName(), TestOutcome.Status.OK);
        outcome.setTiming(this.reportedMicros(), this.reportedRuns());

        if (job instanceof CraftingJob concrete) {
            final KeyCounter used = new KeyCounter();
            final KeyCounter requestable = new KeyCounter();
            final KeyCounter steps = new KeyCounter();

            concrete.populatePlan(used, requestable, steps);
            outcome.setPlan(job.isSimulation(), job.getByteTotal(), this.named(used), this.named(requestable),
                    this.named(steps));
        }

        if (!this.scenario().isExecuted() || job.isSimulation()) {
            this.finishScenario(outcome);
            return;
        }

        final ICraftingGrid cg = this.rig.getProxy().getGrid().getCache(ICraftingGrid.class);
        final ICraftingSubmitResult result = cg.submitJob(job, null, null, false, this.source());

        if (result.errorCode() != null) {
            outcome.setError("submit: " + result.errorCode());
            this.finishScenario(outcome);
            return;
        }

        this.current = outcome;
        this.phase = Phase.EXECUTE;
        this.phaseTicks = 0;
    }

    private void tickExecute() throws Exception {
        final TestOutcome outcome = this.current == null
                ? new TestOutcome(this.scenario().getName(), TestOutcome.Status.OK)
                : this.current;

        if (this.phaseTicks >= this.scenario().getTimeoutTicks()) {
            outcome.setError("execution timed out");
            outcome.setExecution(this.phaseTicks, this.named(this.rig.getStorage().getContents()));
            this.finishScenario(outcome);
            return;
        }

        if (this.phaseTicks < EXECUTION_GRACE_TICKS || this.anyCpuBusy()) {
            return;
        }

        outcome.setExecution(this.phaseTicks, this.named(this.rig.getStorage().getContents()));
        this.finishScenario(outcome);
    }

    private boolean anyCpuBusy() throws Exception {
        final ICraftingGrid cg = this.rig.getProxy().getGrid().getCache(ICraftingGrid.class);

        for (final ICraftingCPU cpu : cg.getCpus()) {
            if (cpu.isBusy()) {
                return true;
            }
        }

        return false;
    }

    private void finishScenario(final TestOutcome outcome) {
        this.outcomes.put(outcome.getScenario(), outcome);
        this.report(outcome);

        this.index++;

        if (this.index < this.scenarios.size()) {
            this.beginScenario();
        } else {
            this.finish();
        }
    }

    private void report(final TestOutcome outcome) {
        if (this.recordBaseline) {
            this.say(" " + outcome.getScenario() + ": recorded (" + timing(outcome) + ")");
            return;
        }

        final List<String> differences = outcome.diff(this.baseline.get(outcome.getScenario()));

        if (differences.isEmpty()) {
            this.say(" " + outcome.getScenario() + ": same (" + timing(outcome) + ")");
            return;
        }

        this.say(" " + outcome.getScenario() + ": " + differences.size() + " different ("
                + timing(outcome) + ")");

        for (final String line : differences) {
            this.say("   " + line);
        }
    }

    private static String timing(final TestOutcome outcome) {
        return outcome.getPlanMicros() + " us, median of " + outcome.getPlanRuns();
    }

    private void finish() {
        if (this.recordBaseline) {
            final String problem = TestBaseline.save(this.outcomes);
            this.say(problem == null
                    ? "Baseline written to " + TestBaseline.file()
                    : "Baseline could not be written: " + problem);
            return;
        }

        int differing = 0;
        for (final TestOutcome outcome : this.outcomes.values()) {
            if (!outcome.diff(this.baseline.get(outcome.getScenario())).isEmpty()) {
                differing++;
            }
        }

        this.say(differing == 0
                ? "All " + this.outcomes.size() + " scenarios match the baseline."
                : differing + " of " + this.outcomes.size() + " scenarios differ from the baseline.");
    }

    private TestOutcome failure(final String message) {
        final TestOutcome outcome = new TestOutcome(this.scenario().getName(), TestOutcome.Status.FAILED);
        outcome.setError(message);
        outcome.setTiming(this.reportedMicros(), this.reportedRuns());
        return outcome;
    }

    private boolean wantsAnotherRun() {
        if (this.timings.size() >= TIMED_RUNS) {
            return false;
        }

        long spent = 0;
        for (final long micros : this.timings) {
            spent += micros;
        }

        return spent < REPEAT_BUDGET_MICROS;
    }

    /**
     * The middle of the runs that were not thrown away, or of all of them when the budget stopped the
     * repeating early. The middle rather than the mean: one run that landed on a garbage collection should
     * not move the number a reader compares against last week's.
     */
    private long reportedMicros() {
        final List<Long> measured = this.measuredRuns();

        if (measured.isEmpty()) {
            return 0;
        }

        final List<Long> sorted = new ArrayList<>(measured);
        Collections.sort(sorted);

        return sorted.get(sorted.size() / 2);
    }

    private int reportedRuns() {
        return this.measuredRuns().size();
    }

    private List<Long> measuredRuns() {
        return this.timings.size() > DISCARDED_RUNS
                ? this.timings.subList(DISCARDED_RUNS, this.timings.size())
                : this.timings;
    }

    private long planMicros() {
        return this.planEndNanos <= this.planStartNanos ? 0 : (this.planEndNanos - this.planStartNanos) / 1000;
    }

    private IActionSource source() {
        return this.player == null ? new MachineSource(this.rig) : new PlayerSource(this.player, this.rig);
    }

    /**
     * The counter with its keys written as the names the scenario made them under, so a report says "plate"
     * rather than the metadata variant of paper that stands in for it.
     */
    private Map<String, Long> named(final KeyCounter counter) {
        final Map<String, Long> named = new TreeMap<>();

        for (final var entry : counter) {
            if (entry.getLongValue() != 0) {
                named.merge(this.keys.nameOf(entry.getKey()), entry.getLongValue(), Long::sum);
            }
        }

        return named;
    }

    private void say(final String line) {
        this.sender.sendMessage(new TextComponentString(line));
    }

    /** Only for a caller that wants the raw numbers rather than the report. */
    public List<TestOutcome> getOutcomes() {
        return new ArrayList<>(this.outcomes.values());
    }
}
