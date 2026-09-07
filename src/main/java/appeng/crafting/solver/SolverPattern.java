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
import appeng.api.stacks.GenericStack;

import java.util.Collections;
import java.util.List;


/**
 * A pattern as the solver sees it: what one craft takes and what one craft makes, and nothing else.
 * <p>
 * {@link #getSource()} is whatever the caller wants handed back in the finished plan - a real
 * {@code ICraftingPatternDetails} in the game, a name in a test. The solver only ever compares it by
 * identity.
 */
public final class SolverPattern {

    private final Object source;
    private final List<SolverIngredient> inputs;
    private final List<GenericStack> outputs;
    private final int priority;

    public SolverPattern(final Object source, final List<SolverIngredient> inputs,
            final List<GenericStack> outputs, final int priority) {
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("A pattern that makes nothing is not a pattern");
        }

        this.source = source;
        this.inputs = Collections.unmodifiableList(inputs);
        this.outputs = Collections.unmodifiableList(outputs);
        this.priority = priority;
    }

    public Object getSource() {
        return this.source;
    }

    public List<SolverIngredient> getInputs() {
        return this.inputs;
    }

    /**
     * Every key one craft makes, each appearing once. A key here that nothing asked for is a byproduct and
     * lands in the surplus ledger.
     */
    public List<GenericStack> getOutputs() {
        return this.outputs;
    }

    public int getPriority() {
        return this.priority;
    }

    /**
     * @return how much of {@code what} one craft makes, or zero when it makes none.
     */
    public long outputOf(final AEKey what) {
        for (final GenericStack out : this.outputs) {
            if (out.what().equals(what)) {
                return out.amount();
            }
        }

        return 0;
    }

    @Override
    public String toString() {
        return String.valueOf(this.source);
    }
}
