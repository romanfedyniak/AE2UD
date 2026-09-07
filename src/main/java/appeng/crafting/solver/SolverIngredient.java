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
 * One thing a pattern consumes, already resolved: what may satisfy it, in the order it should be tried, and
 * how much of each one craft takes.
 * <p>
 * The solver never asks why an option is on the list. Substitution, a slot the network fills in and the
 * damageable-item rules are all settled before this exists, which is what keeps everything below here plain
 * arithmetic on keys and longs.
 */
public final class SolverIngredient {

    private final List<GenericStack> options;

    public SolverIngredient(final List<GenericStack> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("An ingredient nothing can satisfy is not an ingredient");
        }

        this.options = Collections.unmodifiableList(options);
    }

    public static SolverIngredient of(final AEKey what, final long amountPerCraft) {
        return new SolverIngredient(Collections.singletonList(new GenericStack(what, amountPerCraft)));
    }

    /**
     * Most preferred first. Each amount is what <em>one</em> craft consumes if that option is the one used.
     */
    public List<GenericStack> getOptions() {
        return this.options;
    }

    /**
     * Whether anything other than the first option would do. A slot with one option has no say in where
     * scarce stock goes, which is what decides who gets it first.
     */
    public boolean hasChoice() {
        return this.options.size() > 1;
    }

    @Override
    public String toString() {
        return this.options.toString();
    }
}
