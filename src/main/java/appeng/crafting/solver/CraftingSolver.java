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
import appeng.api.stacks.KeyCounter;
import com.google.common.math.LongMath;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Works out what has to be made, and how much of it, in one walk over a graph of keys.
 * <p>
 * The order comes from {@link SolverGraph}: by the time a key comes up, every consumer of it has already had
 * its say, so the whole demand for it is known at once. That is what lets storage be handed out on the full
 * picture instead of to whoever asked first, and it is why the answer does not depend on which branch was
 * walked first - which a tree cannot promise.
 * <p>
 * Cost is the size of the graph and nothing else. Ordering ten billion of a thing does the same work as
 * ordering one.
 */
public final class CraftingSolver {

    private final ICraftingSource source;
    private final SolverLimits limits;

    public CraftingSolver(final ICraftingSource source) {
        this(source, SolverLimits.DEFAULT);
    }

    public CraftingSolver(final ICraftingSource source, final SolverLimits limits) {
        this.source = source;
        this.limits = limits;
    }

    /**
     * @param stock what the network holds. Read, never changed - the caller's snapshot stays its own.
     */
    public SolverPlan solve(final AEKey what, final long amount, final KeyCounter stock) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Nothing was ordered");
        }

        final SolverGraph graph = SolverGraph.of(what, this.source, this.limits);
        final Map<SolverPattern, Long> caps = new LinkedHashMap<>();

        Pass pass = new Pass(graph, stock).run(what, amount, caps);

        // A pattern that ran out of an ingredient has not disproved the request, only itself. Hold it to
        // what it could really do and let the next pattern for that key take the rest - which is what the
        // old tree did one craft at a time, and what a single forward pass cannot see coming.
        for (int refill = 0; refill < this.limits.getMaxRefills() && !pass.missing.isEmpty(); refill++) {
            if (!capExhaustedPattern(graph, pass, caps)) {
                break;
            }

            pass = new Pass(graph, stock).run(what, amount, caps);
        }

        return pass.toPlan(graph);
    }

    /**
     * Finds a pattern that came up short and has a successor waiting, and holds it to what it managed.
     * <p>
     * Every call lowers a cap that was not lower already, so the loop above cannot circle: there are only so
     * many patterns and each can only be brought down so far.
     *
     * @return false when nothing can be reassigned, which is when the missing list is the real answer.
     */
    private static boolean capExhaustedPattern(final SolverGraph graph, final Pass pass,
            final Map<SolverPattern, Long> caps) {
        for (final var shortfall : pass.missing) {
            for (final Map.Entry<SolverPattern, KeyCounter> entry : pass.patternInputs.entrySet()) {
                final SolverPattern pattern = entry.getKey();
                final long drawn = entry.getValue().get(shortfall.getKey());

                if (drawn <= 0 || !hasUnusedSuccessor(graph, pass, pattern)) {
                    continue;
                }

                final long runs = pass.craftsOf(pattern);
                final long perCraft = Math.max(1, drawn / Math.max(1, runs));
                final long lacking = Math.min(shortfall.getLongValue(), drawn);
                final long capped = Math.max(0, runs - Math.max(1, ceilDiv(lacking, perCraft)));
                final Long current = caps.get(pattern);

                if (current != null && current <= capped) {
                    continue;
                }

                caps.put(pattern, capped);
                return true;
            }
        }

        return false;
    }

    /**
     * Whether the key this pattern was making has another pattern behind it that has not been tried.
     */
    private static boolean hasUnusedSuccessor(final SolverGraph graph, final Pass pass,
            final SolverPattern pattern) {
        final AEKey served = pass.servedBy.get(pattern);

        if (served == null) {
            return false;
        }

        final List<SolverPattern> candidates = graph.patternsFor(served);
        final int at = candidates.indexOf(pattern);

        if (at < 0) {
            return false;
        }

        for (int x = at + 1; x < candidates.size(); x++) {
            if (pass.craftsOf(candidates.get(x)) == 0) {
                return true;
            }
        }

        return false;
    }

    private static long ceilDiv(final long amount, final long per) {
        return amount / per + (amount % per == 0 ? 0 : 1);
    }

    private static long multiply(final long amount, final long times) {
        try {
            return LongMath.checkedMultiply(amount, times);
        } catch (final ArithmeticException e) {
            throw new SolverTooLargeException("This order does not fit in a plan");
        }
    }

    /**
     * One walk down the order. Kept apart from the solver so a repeat starts from nothing but the caps.
     */
    private static final class Pass {

        private final SolverGraph graph;
        private final KeyCounter stock = new KeyCounter();
        private final KeyCounter demand = new KeyCounter();
        private final KeyCounter surplus = new KeyCounter();
        private final KeyCounter used = new KeyCounter();
        private final KeyCounter produced = new KeyCounter();
        private final KeyCounter missing = new KeyCounter();
        private final KeyCounter emitted = new KeyCounter();
        private final Map<SolverPattern, Long> crafts = new LinkedHashMap<>();
        private final Map<SolverPattern, KeyCounter> patternInputs = new LinkedHashMap<>();
        private final Map<SolverPattern, AEKey> servedBy = new LinkedHashMap<>();
        private long bytes;

        private Pass(final SolverGraph graph, final KeyCounter stock) {
            this.graph = graph;
            this.stock.addAll(stock);
        }

        private Pass run(final AEKey root, final long amount, final Map<SolverPattern, Long> caps) {
            this.demand.add(root, amount);

            for (final AEKey key : this.graph.getOrder()) {
                this.settle(key, caps);
            }

            // Anything caught in a cycle never made it into the order. It is still wanted, so it is answered
            // from what there is and whatever is left is reported rather than quietly dropped.
            for (final AEKey key : this.graph.getCyclic()) {
                this.settleWithoutCrafting(key);
            }

            return this;
        }

        private void settle(final AEKey key, final Map<SolverPattern, Long> caps) {
            long need = this.demand.get(key);

            if (need <= 0) {
                return;
            }

            final long wanted = need;
            need -= this.takeSurplus(key, need);
            need -= this.takeStock(key, need);

            if (need > 0 && this.graph.isEmitted(key)) {
                this.emitted.add(key, need);
                need = 0;
            }

            for (final SolverPattern pattern : this.graph.patternsFor(key)) {
                if (need <= 0) {
                    break;
                }

                final long per = pattern.outputOf(key);

                if (per <= 0) {
                    continue;
                }

                final long allowed = allowance(pattern, caps) - this.craftsOf(pattern);
                final long runs = Math.min(ceilDiv(need, per), allowed);

                if (runs <= 0) {
                    continue;
                }

                this.servedBy.putIfAbsent(pattern, key);
                this.expand(pattern, runs);
                need -= this.takeSurplus(key, need);
            }

            if (need > 0) {
                this.missing.add(key, need);
            }

            this.chargeBytes(key, wanted);
        }

        /** For a key in a cycle: storage and emitters answer it, its patterns are left alone. */
        private void settleWithoutCrafting(final AEKey key) {
            long need = this.demand.get(key);

            if (need <= 0) {
                return;
            }

            final long wanted = need;
            need -= this.takeSurplus(key, need);
            need -= this.takeStock(key, need);

            if (need > 0 && this.graph.isEmitted(key)) {
                this.emitted.add(key, need);
                need = 0;
            }

            if (need > 0) {
                this.missing.add(key, need);
            }

            this.chargeBytes(key, wanted);
        }

        private static long allowance(final SolverPattern pattern, final Map<SolverPattern, Long> caps) {
            final Long cap = caps.get(pattern);
            return cap == null ? Long.MAX_VALUE : cap;
        }

        /**
         * Runs a pattern: demands what it takes, credits everything it makes. A byproduct is credited the
         * same as the thing that was asked for, which is what lets the next key that wants it find it free.
         */
        private void expand(final SolverPattern pattern, final long runs) {
            this.crafts.merge(pattern, runs, Long::sum);
            final KeyCounter drawn = this.patternInputs.computeIfAbsent(pattern, p -> new KeyCounter());

            for (final SolverIngredient ingredient : pattern.getInputs()) {
                final GenericStack option = this.choose(ingredient, runs);
                final long total = multiply(option.amount(), runs);

                this.demand.add(option.what(), total);
                drawn.add(option.what(), total);
            }

            for (final GenericStack out : pattern.getOutputs()) {
                final long total = multiply(out.amount(), runs);

                this.surplus.add(out.what(), total);
                this.produced.add(out.what(), total);
            }
        }

        /**
         * Which of an ingredient's options to draw. What is already at hand comes first, and among those,
         * one that nothing else is forced to use: a slot with a choice should not take the last of what a
         * slot without one is going to need.
         */
        private GenericStack choose(final SolverIngredient ingredient, final long runs) {
            final List<GenericStack> options = ingredient.getOptions();

            if (!ingredient.hasChoice()) {
                return options.get(0);
            }

            GenericStack contested = null;

            for (final GenericStack option : options) {
                final long wanted = multiply(option.amount(), runs);
                final long atHand = this.surplus.get(option.what()) + this.stock.get(option.what());

                if (atHand < wanted) {
                    continue;
                }

                if (!this.graph.isExclusive(option.what())) {
                    return option;
                }

                if (contested == null) {
                    contested = option;
                }
            }

            if (contested != null) {
                return contested;
            }

            for (final GenericStack option : options) {
                if (!this.graph.patternsFor(option.what()).isEmpty()) {
                    return option;
                }
            }

            return options.get(0);
        }

        private long takeSurplus(final AEKey key, final long need) {
            final long taken = Math.min(this.surplus.get(key), need);

            if (taken > 0) {
                this.surplus.remove(key, taken);
            }

            return Math.max(0, taken);
        }

        private long takeStock(final AEKey key, final long need) {
            final long taken = Math.min(this.stock.get(key), need);

            if (taken > 0) {
                this.stock.remove(key, taken);
                this.used.add(key, taken);
            }

            return Math.max(0, taken);
        }

        /**
         * A job is charged per thing, and for a fluid the thing is a bucket rather than a millibucket - the
         * same rule the tree used, applied once per key instead of at six places that each had to remember.
         */
        private void chargeBytes(final AEKey key, final long amount) {
            this.bytes += amount / Math.max(1, key.getType().getAmountPerUnit());
        }

        private long craftsOf(final SolverPattern pattern) {
            final Long found = this.crafts.get(pattern);
            return found == null ? 0 : found;
        }

        private SolverPlan toPlan(final SolverGraph graph) {
            long total = this.bytes;

            for (final Map.Entry<SolverPattern, Long> entry : this.crafts.entrySet()) {
                total += multiply(8, entry.getValue());
            }

            return new SolverPlan(this.crafts, this.patternInputs, this.used, this.produced, this.missing,
                    this.emitted, total, !graph.getCyclic().isEmpty());
        }
    }
}
