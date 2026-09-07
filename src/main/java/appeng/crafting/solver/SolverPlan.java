/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.crafting.solver;


import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * What a solve worked out. Numbers only - nothing here knows what a pattern is for or where a machine is.
 * <p>
 * A plan with anything in {@link #getMissing()} is one the network cannot carry out as it stands. Whether
 * that makes it a simulation or a job that waits for the missing to be brought is the caller's decision, not
 * this one's: the two differ in what is done with the plan, never in the plan.
 */
public final class SolverPlan {

    private final Map<SolverPattern, Long> crafts;
    private final Map<SolverPattern, KeyCounter> patternInputs;
    private final KeyCounter used;
    private final KeyCounter produced;
    private final KeyCounter missing;
    private final KeyCounter emitted;
    private final long bytes;
    private final boolean cyclic;

    SolverPlan(final Map<SolverPattern, Long> crafts, final Map<SolverPattern, KeyCounter> patternInputs,
            final KeyCounter used, final KeyCounter produced, final KeyCounter missing,
            final KeyCounter emitted, final long bytes, final boolean cyclic) {
        this.crafts = Collections.unmodifiableMap(new LinkedHashMap<>(crafts));
        this.patternInputs = Collections.unmodifiableMap(new LinkedHashMap<>(patternInputs));
        this.used = used;
        this.produced = produced;
        this.missing = missing;
        this.emitted = emitted;
        this.bytes = bytes;
        this.cyclic = cyclic;
    }

    /**
     * How many times each pattern runs, in the order the solver first reached them.
     */
    public Map<SolverPattern, Long> getCrafts() {
        return this.crafts;
    }

    /**
     * What each pattern draws in total, by key - the flow along its edges, which is what a plan drawn as a
     * tree needs to say how much of a shared ingredient went which way.
     */
    public Map<SolverPattern, KeyCounter> getPatternInputs() {
        return this.patternInputs;
    }

    /** Taken out of storage. */
    public KeyCounter getUsed() {
        return this.used;
    }

    /** Made by the plan, counting a byproduct nothing asked for. */
    public KeyCounter getProduced() {
        return this.produced;
    }

    /** Wanted, and neither in storage nor makeable. */
    public KeyCounter getMissing() {
        return this.missing;
    }

    /** Promised by a level emitter. */
    public KeyCounter getEmitted() {
        return this.emitted;
    }

    public long getBytes() {
        return this.bytes;
    }

    /**
     * Whether the request reached a cycle at all - one solved by turning it as well as one left alone. It
     * says nothing about whether the plan is good: a catalyst is a cycle of one by construction, and most
     * plans that reach a cycle carry it out.
     */
    public boolean isCyclic() {
        return this.cyclic;
    }

    public long craftsOf(final SolverPattern pattern) {
        final Long found = this.crafts.get(pattern);
        return found == null ? 0 : found;
    }

    public boolean isComplete() {
        return this.missing.isEmpty();
    }

    public long producedOf(final AEKey what) {
        return this.produced.get(what);
    }
}
