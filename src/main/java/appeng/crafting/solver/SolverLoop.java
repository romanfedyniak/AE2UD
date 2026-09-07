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
import com.google.common.math.LongMath;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * A ring of things that make each other, and how many crafts one turn of it takes.
 * <p>
 * A pattern that eats what it makes is a cycle of one and divides straight out. A cycle through several
 * things is the same idea a step further: to make one of the first you run its pattern, which wants some of
 * the second, whose pattern wants some of the third, and so on until the demand comes back round to the
 * first. Multiply the ratios together and the ring either gives back more than it took or it does not.
 * <p>
 * The awkward part is that crafts are whole numbers, and rounding each step up on its own can leave the ring
 * short of what the arithmetic promised. So a turn of the ring is sized to be exact instead: the smallest
 * number of crafts of the first pattern for which every division down the ring comes out whole. Every turn
 * then nets the same amount with nothing rounded anywhere, and the shortfall is one division by that.
 * <p>
 * Only a plain ring is read - each thing on it made by a pattern that draws exactly one other thing on it.
 * A component that branches is left to the caller, which reports it rather than half-solving it.
 */
final class SolverLoop {

    private final List<AEKey> keys;
    private final List<SolverPattern> patterns;
    /** What {@code patterns[i]} takes of {@code keys[i + 1]} per craft. */
    private final long[] eaten;
    /** Crafts of each pattern in one turn of the ring. */
    private final long[] unitRuns;
    private final long netPerTurn;

    private SolverLoop(final List<AEKey> keys, final List<SolverPattern> patterns, final long[] eaten,
            final long[] unitRuns, final long netPerTurn) {
        this.keys = keys;
        this.patterns = patterns;
        this.eaten = eaten;
        this.unitRuns = unitRuns;
        this.netPerTurn = netPerTurn;
    }

    /**
     * Walks the ring that starts and ends at one key.
     *
     * @return null when there is no plain ring back to {@code from}, or when turning it would give back no
     *         more than it took. Either way the caller settles the component the way it did before.
     */
    @Nullable
    static SolverLoop trace(final AEKey from, final List<AEKey> component, final SolverGraph graph) {
        final Set<AEKey> inside = new LinkedHashSet<>(component);
        final List<AEKey> keys = new ArrayList<>();
        final List<SolverPattern> patterns = new ArrayList<>();
        final List<Long> made = new ArrayList<>();
        final List<Long> eaten = new ArrayList<>();
        AEKey at = from;

        while (keys.size() < component.size()) {
            SolverPattern step = null;
            AEKey next = null;

            for (final SolverPattern pattern : graph.patternsFor(at)) {
                next = soleIngredientInside(pattern, inside, at);

                if (next != null) {
                    step = pattern;
                    break;
                }
            }

            if (step == null) {
                return null;
            }

            final long makes = step.outputOf(at);
            final long takes = takenPerCraft(step, next);

            if (makes <= 0 || takes <= 0) {
                return null;
            }

            keys.add(at);
            patterns.add(step);
            made.add(makes);
            eaten.add(takes);

            if (next.equals(from)) {
                return size(keys, patterns, made, eaten);
            }

            // A ring was found, but not one the key being settled is on. Turning it would not answer the
            // demand that started this.
            if (keys.contains(next)) {
                return null;
            }

            at = next;
        }

        return null;
    }

    /**
     * The one thing on the ring this pattern draws, or null if it draws none, several, or the very thing it
     * is being asked to make - none of which is a plain step.
     */
    @Nullable
    private static AEKey soleIngredientInside(final SolverPattern pattern, final Set<AEKey> inside,
            final AEKey self) {
        AEKey found = null;

        for (final SolverIngredient ingredient : pattern.getInputs()) {
            final AEKey what = ingredient.getOptions().get(0).what();

            if (!inside.contains(what)) {
                continue;
            }

            if (what.equals(self) || (found != null && !found.equals(what))) {
                return null;
            }

            found = what;
        }

        return found;
    }

    private static long takenPerCraft(final SolverPattern pattern, final AEKey key) {
        long total = 0;

        for (final SolverIngredient ingredient : pattern.getInputs()) {
            if (ingredient.getOptions().get(0).what().equals(key)) {
                total += ingredient.getOptions().get(0).amount();
            }
        }

        return total;
    }

    /**
     * Sizes one turn of the ring so that no step has to round.
     * <p>
     * Crafts of the pattern that makes {@code keys[at]} come to {@code turn * num / den} of the crafts of the
     * first, in lowest terms. A turn is the least common multiple of those denominators, which is the
     * smallest count that divides evenly all the way round.
     */
    @Nullable
    private static SolverLoop size(final List<AEKey> keys, final List<SolverPattern> patterns,
            final List<Long> made, final List<Long> eaten) {
        final int ring = keys.size();
        final long[] takes = new long[ring];
        final long[] num = new long[ring];
        final long[] den = new long[ring];
        final long[] unitRuns = new long[ring];

        for (int at = 0; at < ring; at++) {
            takes[at] = eaten.get(at);
        }

        num[0] = 1;
        den[0] = 1;

        try {
            long carriedNum = 1;
            long carriedDen = 1;
            long turn = 1;

            for (int at = 1; at < ring; at++) {
                carriedNum = LongMath.checkedMultiply(carriedNum, eaten.get(at - 1));
                carriedDen = LongMath.checkedMultiply(carriedDen, made.get(at));

                final long common = LongMath.gcd(carriedNum, carriedDen);
                carriedNum /= common;
                carriedDen /= common;

                num[at] = carriedNum;
                den[at] = carriedDen;
                turn = LongMath.checkedMultiply(turn / LongMath.gcd(turn, carriedDen), carriedDen);
            }

            for (int at = 0; at < ring; at++) {
                unitRuns[at] = LongMath.checkedMultiply(turn, num[at]) / den[at];
            }

            final long forward = LongMath.checkedMultiply(unitRuns[0], made.get(0));
            final long back = LongMath.checkedMultiply(unitRuns[ring - 1], takes[ring - 1]);

            // A ring that gives back no more than it took is not a source of anything, however many times it
            // is turned. Neither is one that gives back less.
            if (forward <= back) {
                return null;
            }

            return new SolverLoop(keys, patterns, takes, unitRuns, forward - back);
        } catch (final ArithmeticException tooBig) {
            // The ratios alone do not fit in a long, which says nothing about how much was ordered. Left
            // alone, the way it was before there was any of this.
            return null;
        }
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
     * What one turn of the ring nets of {@link #keyAt(int) keyAt(0)}, over and above what it hands back to
     * the ring itself.
     */
    long getNetPerTurn() {
        return this.netPerTurn;
    }

    /**
     * How much of {@code keyAt(at)} one craft of the pattern before it on the ring takes - which is how much
     * has to be in hand for the ring to start turning there.
     */
    long getEatenBefore(final int at) {
        return this.eaten[(at + this.eaten.length - 1) % this.eaten.length];
    }
}
