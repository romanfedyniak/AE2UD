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
    private final Runnable onProgress;

    public CraftingSolver(final ICraftingSource source) {
        this(source, SolverLimits.DEFAULT);
    }

    public CraftingSolver(final ICraftingSource source, final SolverLimits limits) {
        this(source, limits, () -> {
        });
    }

    /**
     * @param onProgress run once for each group of keys settled. A job spread across ticks yields here, and a
     *                   cancelled one throws from here; a solve is short enough that no finer grain would
     *                   ever be reached.
     */
    public CraftingSolver(final ICraftingSource source, final SolverLimits limits, final Runnable onProgress) {
        this.source = source;
        this.limits = limits;
        this.onProgress = onProgress;
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

        Pass pass = new Pass(graph, stock, this.onProgress).run(what, amount, caps);

        // A pattern that ran out of an ingredient has not disproved the request, only itself. Hold it to
        // what it could really do and let the next pattern for that key take the rest - which is what the
        // old tree did one craft at a time, and what a single forward pass cannot see coming.
        for (int refill = 0; refill < this.limits.getMaxRefills() && !pass.missing.isEmpty(); refill++) {
            if (!capExhaustedPattern(graph, pass, caps)) {
                break;
            }

            pass = new Pass(graph, stock, this.onProgress).run(what, amount, caps);
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
        private final Runnable onProgress;
        private AEKey root;
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

        private Pass(final SolverGraph graph, final KeyCounter stock, final Runnable onProgress) {
            this.graph = graph;
            this.onProgress = onProgress;
            this.stock.addAll(stock);
        }

        private Pass run(final AEKey root, final long amount, final Map<SolverPattern, Long> caps) {
            this.root = root;
            this.demand.add(root, amount);

            for (final List<AEKey> component : this.graph.getComponents()) {
                this.onProgress.run();
                final AEKey only = component.size() == 1 ? component.get(0) : null;

                if (only != null && !this.graph.hasSelfLoop(only)) {
                    this.settle(only, caps);
                } else {
                    this.settleCycle(component, caps);
                }
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

        /**
         * A cycle. What can be answered from outside it is answered first, because a loop is a way of
         * growing something you already have and never a way of conjuring the first of it.
         * <p>
         * Only a pattern that feeds itself is then looped: it makes some of a key and eats some of the same
         * key per craft, so each craft nets the difference and the shortfall divides straight out - one
         * division, no iteration, nothing that can fail to come back. A loop running through several keys is
         * recognised and left alone; its keys are answered from storage or reported, as before.
         */
        private void settleCycle(final List<AEKey> component, final Map<SolverPattern, Long> caps) {
            for (final AEKey key : component) {
                long need = this.demand.get(key);

                if (need <= 0) {
                    continue;
                }

                final long wanted = need;
                final long holdBack = component.size() == 1 ? this.seedFor(key) : 0;

                need -= this.takeSurplus(key, need);
                need -= this.takeStock(key, Math.min(need, Math.max(0, this.stock.get(key) - holdBack)));

                if (need > 0 && this.graph.isEmitted(key)) {
                    this.emitted.add(key, need);
                    need = 0;
                }

                if (need > 0 && component.size() == 1) {
                    need = this.growFromItself(key, need, caps);
                }

                // Held back for a loop that in the end did not run, or did not need all of it.
                need -= this.takeStock(key, need);

                if (need > 0) {
                    this.missing.add(key, need);
                }

                this.chargeBytes(key, wanted);
            }
        }

        /**
         * Runs a self-feeding pattern for the shortfall, if it makes more of the key than it takes.
         *
         * @return what is still wanted afterwards.
         */
        private long growFromItself(final AEKey key, final long shortfall,
                final Map<SolverPattern, Long> caps) {
            long need = shortfall;

            for (final SolverPattern pattern : this.graph.patternsFor(key)) {
                if (need <= 0) {
                    break;
                }

                final long eaten = this.eatenPerCraft(pattern, key);
                final long net = pattern.outputOf(key) - eaten;

                // A loop that gives back no more than it took is not a source of anything, however many
                // times it is run. Neither is one that gives back less.
                if (eaten <= 0 || net <= 0) {
                    continue;
                }

                // Nothing starts without one craft's worth in hand, and that has to be there already: a loop
                // grows what you have and never conjures the first of it.
                if (this.surplus.get(key) + this.stock.get(key) < eaten) {
                    continue;
                }

                final long allowed = allowance(pattern, caps) - this.craftsOf(pattern);
                final long runs = Math.min(ceilDiv(need, net), allowed);

                if (runs <= 0) {
                    continue;
                }

                final long before = this.demand.get(key);
                this.servedBy.putIfAbsent(pattern, key);
                this.expand(pattern, runs);

                // Running it asked for the seed. This key is being settled now and nothing will come back to
                // it, so that goes on what is still wanted and is answered here with everything else - out of
                // the network's own, which is the one thing the loop is allowed to reach for.
                need += this.demand.get(key) - before;
                need -= this.takeSurplus(key, need);
                need -= this.drawStock(key, need);
            }

            return need;
        }

        /**
         * The most any self-feeding pattern for this key would need in hand at once, which is what must not
         * be spent on the demand before the loop has had its chance to start.
         */
        private long seedFor(final AEKey key) {
            long seed = 0;

            for (final SolverPattern pattern : this.graph.patternsFor(key)) {
                final long eaten = this.eatenPerCraft(pattern, key);

                if (eaten > 0 && pattern.outputOf(key) > eaten) {
                    seed = Math.max(seed, eaten);
                }
            }

            return seed;
        }

        /**
         * How much of the key one craft draws, counting the slots that were encoded with it.
         */
        private long eatenPerCraft(final SolverPattern pattern, final AEKey key) {
            long total = 0;

            for (final SolverIngredient ingredient : pattern.getInputs()) {
                if (ingredient.getOptions().get(0).what().equals(key)) {
                    total += ingredient.getOptions().get(0).amount();
                }
            }

            return total;
        }

        private static long allowance(final SolverPattern pattern, final Map<SolverPattern, Long> caps) {
            final Long cap = caps.get(pattern);
            return cap == null ? Long.MAX_VALUE : cap;
        }

        /**
         * Runs a pattern: demands what it takes, credits what it makes. A byproduct is credited the same as
         * the thing that was asked for, which is what lets the next key that wants it find it free.
         * <p>
         * An ingredient the pattern hands back is not something it consumed. Only the difference is drawn
         * per craft, and the part that comes back is asked for once - enough to have in hand at a time - and
         * is not credited as production either, or a mould would be minting copies of itself. That one rule
         * covers the catalyst that comes out untouched, the pattern that eats some of its own output, and
         * anything in between.
         */
        private void expand(final SolverPattern pattern, final long runs) {
            this.crafts.merge(pattern, runs, Long::sum);
            final KeyCounter drawn = this.patternInputs.computeIfAbsent(pattern, p -> new KeyCounter());
            final KeyCounter handedBack = new KeyCounter();

            for (final SolverIngredient ingredient : pattern.getInputs()) {
                this.drawIngredient(pattern, ingredient, runs, drawn, handedBack);
            }

            for (final GenericStack out : pattern.getOutputs()) {
                final long net = out.amount() - handedBack.get(out.what());

                if (net > 0) {
                    final long total = multiply(net, runs);

                    this.surplus.add(out.what(), total);
                    this.produced.add(out.what(), total);
                }
            }
        }

        /**
         * Draws one ingredient, which may take several things to cover.
         * <p>
         * An option that cannot serve the whole run is not thereby useless: one worn tool and nine fresh ones
         * make ten crafts, and the tree this replaced knew that. So the run is shared out - as much as each
         * option can really cover, in the order they are preferred - and whatever is left over goes on the
         * one that can be made, or failing that on the encoded one, where it shows up as missing.
         * <p>
         * A key nothing else can be fed with is left till last among the ones at hand: a slot with a choice
         * should not take the last of what a slot without one is going to need.
         */
        private void drawIngredient(final SolverPattern pattern, final SolverIngredient ingredient,
                final long runs, final KeyCounter drawn, final KeyCounter handedBack) {
            final List<GenericStack> options = ingredient.getOptions();
            final GenericStack encoded = options.get(0);

            // What the pattern hands back it did not consume, so it is asked for once - enough to have in
            // hand at a time - however the rest of the run is shared out. Asking per share would count the
            // same lent thing several times over.
            if (ingredient.getUses(0) <= 0) {
                final long back = Math.min(encoded.amount(), pattern.outputOf(encoded.what()));

                if (back > 0) {
                    handedBack.add(encoded.what(), back);
                    this.demand.add(encoded.what(), back);
                    drawn.add(encoded.what(), back);

                    // Lent and nothing more: a mould, or a pattern giving back all of what it took.
                    if (back >= encoded.amount()) {
                        return;
                    }
                }
            }

            long left = runs;

            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int x = 0; x < options.size() && left > 0; x++) {
                    final GenericStack option = options.get(x);

                    // Contested keys wait for the second time round, by which point anything else at hand
                    // has been used up.
                    if (this.graph.isExclusive(option.what()) != (pass == 1)) {
                        continue;
                    }

                    final long covers = Math.min(left, this.runsCovered(pattern, ingredient, x));

                    if (covers > 0) {
                        left -= covers;
                        this.take(pattern, covers, ingredient, x, drawn);
                    }
                }
            }

            if (left > 0) {
                this.take(pattern, left, ingredient, this.fallback(options), drawn);
            }
        }

        /**
         * How many crafts what is at hand of this option would really cover - which is not how many of it
         * there are: a tool serves as many crafts as it has left in it, and a thing handed back covers the
         * whole run on its own.
         */
        private long runsCovered(final SolverPattern pattern, final SolverIngredient ingredient,
                final int index) {
            final GenericStack option = ingredient.getOptions().get(index);
            final long atHand = this.surplus.get(option.what()) + this.stock.get(option.what());

            if (atHand <= 0) {
                return 0;
            }

            if (ingredient.getUses(index) > 0) {
                return atHand / Math.max(1, option.amount()) * ingredient.getUses(index);
            }

            final long net = option.amount() - Math.min(option.amount(), pattern.outputOf(option.what()));

            return net <= 0 ? Long.MAX_VALUE : atHand / net;
        }

        /**
         * Where the part nothing has in stock is asked for: the first option something can make, or the
         * encoded one, which is then reported missing.
         */
        private int fallback(final List<GenericStack> options) {
            for (int x = 0; x < options.size(); x++) {
                if (!this.graph.patternsFor(options.get(x).what()).isEmpty()) {
                    return x;
                }
            }

            return 0;
        }

        /**
         * Books {@code runs} crafts' worth of one option. What the pattern hands back was settled once by
         * the caller, so only what is really spent is drawn here; a thing spent gradually is drawn by how
         * many of it the run wears out.
         */
        private void take(final SolverPattern pattern, final long runs, final SolverIngredient ingredient,
                final int index, final KeyCounter drawn) {
            final GenericStack option = ingredient.getOptions().get(index);
            final long total;

            if (ingredient.getUses(index) > 0) {
                total = multiply(option.amount(), ceilDiv(runs, ingredient.getUses(index)));
            } else {
                final long back = Math.min(option.amount(), pattern.outputOf(option.what()));
                total = multiply(option.amount() - back, runs);
            }

            if (total <= 0) {
                return;
            }

            this.demand.add(option.what(), total);
            drawn.add(option.what(), total);
        }

        private long takeSurplus(final AEKey key, final long need) {
            final long taken = Math.min(this.surplus.get(key), need);

            if (taken > 0) {
                this.surplus.remove(key, taken);
            }

            return Math.max(0, taken);
        }

        /**
         * What is already in the network does not count towards the order: asking for a hundred when forty
         * are on the shelf makes a hundred more, and always has. So the requested thing is the one thing a
         * plan may not spend - with one exception, which {@link #growFromItself} makes: a loop grows what
         * you have, and the one you have is the only place its first can come from.
         */
        private long takeStock(final AEKey key, final long need) {
            if (key.equals(this.root)) {
                return 0;
            }

            return this.drawStock(key, need);
        }

        private long drawStock(final AEKey key, final long need) {
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
                    this.emitted, total, graph.isCyclic());
        }
    }
}
