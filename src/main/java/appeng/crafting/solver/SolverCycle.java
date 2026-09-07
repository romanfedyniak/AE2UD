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

import javax.annotation.Nullable;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * A set of things that make each other, and how many crafts one turn of it takes.
 * <p>
 * A cycle is not always a ring. Two patterns can feed each other, or five can go round in a line, but a
 * pattern can also draw <em>two</em> things that are themselves made from what it makes - and then there is
 * no single ring to walk and no chain of ratios to multiply. What there always is, whatever the shape, is a
 * question of how many times to run each pattern so that the whole thing nets what was asked for and neither
 * gains nor loses anything else on the way. That is a set of linear equations, one per thing on the cycle:
 * <p>
 * {@code (what the patterns make of it) - (what they take of it) = (what is wanted of it)}
 * <p>
 * Wanted is one for the thing being settled and nothing for the rest, since a cycle exists to be gone round
 * rather than to leave anything behind. Solving it gives how many crafts of each pattern make one of the
 * thing; multiplying that out so every count is whole gives <em>one turn</em>, which nets a fixed amount and
 * can simply be repeated. The shortfall is then one division, however tangled the cycle and however much was
 * ordered.
 * <p>
 * The arithmetic is exact - whole-number fractions throughout, never a {@code double}. A ratio worked out
 * approximately would leave a plan that is a craft short of what it promised, which is the one thing a plan
 * may not be.
 * <p>
 * A cycle that cannot be solved is refused rather than half-solved: no answer, a negative one (a pattern
 * cannot be run backwards), or one whose numbers do not fit. The caller then reports its things as missing,
 * which is what happened to every cycle before any of this existed.
 */
final class SolverCycle {

    /**
     * How many things a cycle may reach before it is left alone. Elimination is cubic in this and the
     * numbers grow as it goes, and a cycle even close to this size is not something a network has by
     * accident. Refusing is the same answer such a cycle got before it could be solved at all.
     */
    private static final int MOST_THINGS = 16;

    private final List<AEKey> keys;
    private final List<SolverPattern> patterns;
    /** Crafts of {@code patterns[i]} in one turn. */
    private final long[] unitRuns;
    /** What one turn nets of {@code keys[0]}, and of nothing else. */
    private final long netPerTurn;
    /** The most any pattern on the cycle takes of a thing in one craft. */
    private final Map<AEKey, Long> eaten;

    private SolverCycle(final List<AEKey> keys, final List<SolverPattern> patterns, final long[] unitRuns,
            final long netPerTurn, final Map<AEKey, Long> eaten) {
        this.keys = keys;
        this.patterns = patterns;
        this.unitRuns = unitRuns;
        this.netPerTurn = netPerTurn;
        this.eaten = eaten;
    }

    /**
     * Works out how to turn the cycle {@code from} sits on.
     *
     * @return null when there is nothing here to turn, or turning it would not net what was asked for.
     */
    @Nullable
    static SolverCycle of(final AEKey from, final List<AEKey> component, final SolverGraph graph) {
        final Set<AEKey> inside = new LinkedHashSet<>(component);
        final List<AEKey> keys = new ArrayList<>();
        final List<SolverPattern> patterns = new ArrayList<>();

        if (!gather(from, inside, graph, keys, patterns)) {
            return null;
        }

        final Ratio[] runs = solve(matrixOf(keys, patterns), keys.size());

        if (runs == null) {
            return null;
        }

        return size(keys, patterns, runs);
    }

    /**
     * Collects the things the cycle really joins together, starting from the one being settled.
     * <p>
     * Each is served by the first pattern that makes it and draws something else on the cycle; whatever that
     * pattern draws on the cycle is gathered too. The set is therefore closed: nothing the cycle's patterns
     * ask of the cycle falls outside it, so nothing is settled behind the answer's back. What they ask of
     * anything else lies outside the component altogether and is settled after it, like any other demand.
     *
     * @return false when something on the walk cannot be served, or the walk grows past what is worth solving.
     */
    private static boolean gather(final AEKey from, final Set<AEKey> inside, final SolverGraph graph,
            final List<AEKey> keys, final List<SolverPattern> patterns) {
        final Set<AEKey> seen = new LinkedHashSet<>();
        final Deque<AEKey> pending = new ArrayDeque<>();

        pending.add(from);
        seen.add(from);

        while (!pending.isEmpty()) {
            final AEKey at = pending.poll();
            final SolverPattern serving = servingPattern(at, inside, graph);

            if (serving == null || keys.size() >= MOST_THINGS) {
                return false;
            }

            keys.add(at);
            patterns.add(serving);

            for (final SolverIngredient ingredient : serving.getInputs()) {
                final AEKey what = ingredient.getOptions().get(0).what();

                if (inside.contains(what) && seen.add(what)) {
                    pending.add(what);
                }
            }
        }

        return keys.size() > 1;
    }

    /**
     * The pattern a thing on the cycle is made by: the first that makes it and draws something else on the
     * cycle. A pattern drawing nothing on the cycle is a way off it, which is not this class's business.
     */
    @Nullable
    private static SolverPattern servingPattern(final AEKey key, final Set<AEKey> inside,
            final SolverGraph graph) {
        for (final SolverPattern pattern : graph.patternsFor(key)) {
            if (pattern.outputOf(key) <= 0) {
                continue;
            }

            for (final SolverIngredient ingredient : pattern.getInputs()) {
                final AEKey what = ingredient.getOptions().get(0).what();

                if (inside.contains(what) && !what.equals(key)) {
                    return pattern;
                }
            }
        }

        return null;
    }

    /**
     * A row per thing, a column per pattern: what one craft of that pattern leaves the network with, of that
     * thing. Making it counts up and drawing it counts down, so a pattern that hands back what it took -
     * a mould, or one eating some of its own output - comes out at nothing, which is exactly what it is.
     */
    private static Ratio[][] matrixOf(final List<AEKey> keys, final List<SolverPattern> patterns) {
        final int things = keys.size();
        final Ratio[][] rows = new Ratio[things][things + 1];

        for (int row = 0; row < things; row++) {
            final AEKey key = keys.get(row);

            for (int column = 0; column < things; column++) {
                final SolverPattern pattern = patterns.get(column);

                rows[row][column] = Ratio.of(pattern.outputOf(key) - takenPerCraft(pattern, key));
            }

            // One of the thing being settled, and nothing of the rest: a cycle is turned to leave the first
            // behind, and to come back to where it started on everything else.
            rows[row][things] = row == 0 ? Ratio.ONE : Ratio.ZERO;
        }

        return rows;
    }

    private static long takenPerCraft(final SolverPattern pattern, final AEKey key) {
        long total = 0;

        for (final SolverIngredient ingredient : pattern.getInputs()) {
            final GenericStack encoded = ingredient.getOptions().get(0);

            if (encoded.what().equals(key)) {
                total += encoded.amount();
            }
        }

        return total;
    }

    /**
     * Gauss-Jordan on the augmented rows, in exact fractions.
     *
     * @return the crafts of each pattern that make one of the first thing, or null when the cycle has no
     *         answer at all - which is what a cycle giving back exactly what it took looks like from here.
     */
    @Nullable
    private static Ratio[] solve(final Ratio[][] rows, final int size) {
        for (int column = 0; column < size; column++) {
            int pivot = -1;

            for (int row = column; row < size && pivot < 0; row++) {
                if (!rows[row][column].isZero()) {
                    pivot = row;
                }
            }

            if (pivot < 0) {
                return null;
            }

            final Ratio[] swap = rows[column];
            rows[column] = rows[pivot];
            rows[pivot] = swap;

            final Ratio divisor = rows[column][column];

            for (int at = column; at <= size; at++) {
                rows[column][at] = rows[column][at].over(divisor);
            }

            for (int row = 0; row < size; row++) {
                if (row == column || rows[row][column].isZero()) {
                    continue;
                }

                final Ratio factor = rows[row][column];

                for (int at = column; at <= size; at++) {
                    rows[row][at] = rows[row][at].minus(factor.times(rows[column][at]));
                }
            }
        }

        final Ratio[] runs = new Ratio[size];

        for (int at = 0; at < size; at++) {
            runs[at] = rows[at][size];

            // A pattern cannot be run backwards, so an answer asking for it is no answer.
            if (runs[at].signum() < 0) {
                return null;
            }
        }

        return runs;
    }

    /**
     * Turns the answer into whole crafts.
     * <p>
     * One turn is the smallest multiple of the answer at which every pattern's count comes out whole, which
     * is the common multiple of what the fractions are over. Multiplying the equations by that says the turn
     * nets exactly that many of the first thing and nothing of the rest - no rounding anywhere, which is the
     * whole reason for doing it this way round.
     */
    @Nullable
    private static SolverCycle size(final List<AEKey> keys, final List<SolverPattern> patterns,
            final Ratio[] runs) {
        BigInteger turn = BigInteger.ONE;

        for (final Ratio ratio : runs) {
            turn = turn.divide(turn.gcd(ratio.den)).multiply(ratio.den);
        }

        final long[] unitRuns = new long[runs.length];

        for (int at = 0; at < runs.length; at++) {
            final BigInteger count = runs[at].num.multiply(turn.divide(runs[at].den));

            if (count.bitLength() >= Long.SIZE) {
                return null;
            }

            unitRuns[at] = count.longValue();
        }

        if (turn.bitLength() >= Long.SIZE || turn.signum() <= 0) {
            return null;
        }

        final Map<AEKey, Long> eaten = new LinkedHashMap<>();

        for (final AEKey key : keys) {
            long most = 0;

            for (final SolverPattern pattern : patterns) {
                most = Math.max(most, takenPerCraft(pattern, key));
            }

            eaten.put(key, most);
        }

        return new SolverCycle(keys, patterns, unitRuns, turn.longValue(), eaten);
    }

    int size() {
        return this.keys.size();
    }

    AEKey keyAt(final int at) {
        return this.keys.get(at);
    }

    SolverPattern patternAt(final int at) {
        return this.patterns.get(at);
    }

    long unitRunsAt(final int at) {
        return this.unitRuns[at];
    }

    /**
     * What one turn nets of {@link #keyAt(int) keyAt(0)}, over and above what it hands back to the cycle.
     */
    long getNetPerTurn() {
        return this.netPerTurn;
    }

    /**
     * The most one craft on the cycle takes of this thing, which is how much has to be in hand for the cycle
     * to start turning there.
     */
    long eatenOf(final AEKey key) {
        return this.eaten.getOrDefault(key, 0L);
    }

    /**
     * A fraction of whole numbers, kept in its lowest terms with the sign on top.
     */
    private static final class Ratio {

        private static final Ratio ZERO = new Ratio(BigInteger.ZERO, BigInteger.ONE);
        private static final Ratio ONE = new Ratio(BigInteger.ONE, BigInteger.ONE);

        private final BigInteger num;
        private final BigInteger den;

        private Ratio(final BigInteger num, final BigInteger den) {
            this.num = num;
            this.den = den;
        }

        private static Ratio of(final long value) {
            return value == 0 ? ZERO : new Ratio(BigInteger.valueOf(value), BigInteger.ONE);
        }

        private static Ratio reduced(final BigInteger num, final BigInteger den) {
            if (num.signum() == 0) {
                return ZERO;
            }

            final BigInteger sign = den.signum() < 0 ? BigInteger.valueOf(-1) : BigInteger.ONE;
            final BigInteger common = num.gcd(den);

            return new Ratio(num.divide(common).multiply(sign), den.divide(common).abs());
        }

        private Ratio minus(final Ratio other) {
            return reduced(this.num.multiply(other.den).subtract(other.num.multiply(this.den)),
                    this.den.multiply(other.den));
        }

        private Ratio times(final Ratio other) {
            return reduced(this.num.multiply(other.num), this.den.multiply(other.den));
        }

        private Ratio over(final Ratio other) {
            return reduced(this.num.multiply(other.den), this.den.multiply(other.num));
        }

        private boolean isZero() {
            return this.num.signum() == 0;
        }

        private int signum() {
            return this.num.signum();
        }
    }
}
