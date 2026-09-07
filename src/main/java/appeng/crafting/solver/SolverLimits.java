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


/**
 * Where a solve gives up and says so.
 * <p>
 * Every limit here is on the <em>shape</em> of the problem - how many keys are reachable, how many patterns
 * feed them, how many times the solver is allowed to change its mind. None is on how much was ordered:
 * asking for ten billion of a thing costs exactly what asking for one costs, and a ceiling on the number of
 * crafts would take that away for nothing. What stops an absurd order is the byte total the crafting cpu
 * then refuses, which is the honest place for it.
 */
public final class SolverLimits {

    public static final SolverLimits DEFAULT = new SolverLimits(100_000, 1_000_000, 4_096);

    private final int maxNodes;
    private final int maxEdges;
    private final int maxRefills;

    public SolverLimits(final int maxNodes, final int maxEdges, final int maxRefills) {
        this.maxNodes = maxNodes;
        this.maxEdges = maxEdges;
        this.maxRefills = maxRefills;
    }

    public int getMaxNodes() {
        return this.maxNodes;
    }

    public int getMaxEdges() {
        return this.maxEdges;
    }

    /**
     * How many times the solver may cap a pattern and try again. Each one exhausts an option for good, so
     * the natural bound is the number of pattern choices in the graph; this is only the backstop.
     */
    public int getMaxRefills() {
        return this.maxRefills;
    }
}
