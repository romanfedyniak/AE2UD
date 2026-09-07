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


import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * What one scenario answered, in terms that outlive the thing that answered it.
 * <p>
 * Everything recorded here is read through {@code CraftingJob}'s public plan - the split counters, the byte
 * total and whether it came back a simulation. Nothing here reaches into the solver's own structure, because
 * a baseline that did would stop being comparable the moment the solver is replaced, which is the one
 * comparison the rig exists to make.
 */
public final class TestOutcome {

    public enum Status {
        /** The planner answered. Whether it answered <em>well</em> is what the diff is for. */
        OK,
        /** Nothing came back before the scenario's tick budget ran out. */
        TIMEOUT,
        /** The planner threw. The message is kept in {@link #error}. */
        FAILED
    }

    private String scenario;
    private Status status;
    private boolean simulation;
    private long bytes;
    private Map<String, Long> used = new TreeMap<>();
    private Map<String, Long> requestable = new TreeMap<>();
    private Map<String, Long> craftingSteps = new TreeMap<>();
    /** -1 when the scenario did not ask for the plan to be run. */
    private int executedTicks = -1;
    @Nullable
    private Map<String, Long> finalStorage;
    /**
     * Reported, never diffed. A machine that is a little slower today has not changed any answer, and a
     * baseline that failed on it would be red every time somebody else opened a browser.
     */
    private long planMicros;
    /** How many timed runs {@link #planMicros} is the middle of. */
    private int planRuns;
    @Nullable
    private String error;

    /** Gson. */
    TestOutcome() {
    }

    public TestOutcome(final String scenario, final Status status) {
        this.scenario = scenario;
        this.status = status;
    }

    public String getScenario() {
        return this.scenario;
    }

    public Status getStatus() {
        return this.status;
    }

    public long getPlanMicros() {
        return this.planMicros;
    }

    public int getPlanRuns() {
        return this.planRuns;
    }

    public void setPlan(final boolean simulation, final long bytes, final Map<String, Long> used,
            final Map<String, Long> requestable, final Map<String, Long> craftingSteps) {
        this.simulation = simulation;
        this.bytes = bytes;
        this.used = new TreeMap<>(used);
        this.requestable = new TreeMap<>(requestable);
        this.craftingSteps = new TreeMap<>(craftingSteps);
    }

    public void setExecution(final int ticks, final Map<String, Long> finalStorage) {
        this.executedTicks = ticks;
        this.finalStorage = new TreeMap<>(finalStorage);
    }

    public void setTiming(final long planMicros, final int planRuns) {
        this.planMicros = planMicros;
        this.planRuns = planRuns;
    }

    public void setError(final String error) {
        this.error = error;
    }

    /**
     * How this run differs from the one recorded as right, one line per difference. Empty means they agree.
     */
    public List<String> diff(@Nullable final TestOutcome baseline) {
        final List<String> lines = new ArrayList<>();

        if (baseline == null) {
            lines.add("no baseline recorded for this scenario");
            return lines;
        }

        if (this.status != baseline.status) {
            lines.add("status " + baseline.status + " -> " + this.status);
        }

        if (this.error != null && !this.error.equals(baseline.error)) {
            lines.add("error " + baseline.error + " -> " + this.error);
        }

        if (this.simulation != baseline.simulation) {
            lines.add("simulation " + baseline.simulation + " -> " + this.simulation);
        }

        if (this.bytes != baseline.bytes) {
            lines.add("bytes " + baseline.bytes + " -> " + this.bytes);
        }

        if (this.executedTicks != baseline.executedTicks) {
            lines.add("executed ticks " + baseline.executedTicks + " -> " + this.executedTicks);
        }

        diffCounters(lines, "used", baseline.used, this.used);
        diffCounters(lines, "requestable", baseline.requestable, this.requestable);
        diffCounters(lines, "steps", baseline.craftingSteps, this.craftingSteps);
        diffCounters(lines, "storage", baseline.finalStorage, this.finalStorage);

        return lines;
    }

    private static void diffCounters(final List<String> lines, final String what,
            @Nullable final Map<String, Long> was, @Nullable final Map<String, Long> now) {
        if (was == null && now == null) {
            return;
        }

        final Set<String> keys = new LinkedHashSet<>();
        if (was != null) {
            keys.addAll(was.keySet());
        }
        if (now != null) {
            keys.addAll(now.keySet());
        }

        for (final String key : keys) {
            final long before = was == null ? 0 : was.getOrDefault(key, 0L);
            final long after = now == null ? 0 : now.getOrDefault(key, 0L);

            if (before != after) {
                lines.add(what + " " + key + " " + before + " -> " + after);
            }
        }
    }
}
