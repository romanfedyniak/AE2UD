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


import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static appeng.crafting.solver.SolverTestNetwork.crafts;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;


/**
 * The claims that are the whole reason for the rewrite, written down so a change that quietly gives one of
 * them up fails the build instead of being noticed a year later in a profile.
 * <p>
 * The time budgets are deliberately loose - a slow machine under load should not turn this red. They are
 * there to catch a return to the old shape, which is not ten per cent slower but thousands of times slower,
 * or a stack that does not come back at all.
 */
public final class CraftingSolverScaleTest {

    private static final long GENEROUS_MILLIS = 2000;

    /**
     * The first solve of a session pays for loading and compiling everything under it, which on a busy
     * machine is seconds and has nothing to do with the shape of the problem. These budgets are here to
     * catch a return to walking item by item, so the measurement starts after that is out of the way.
     */
    @BeforeAll
    public static void warmUp() {
        for (int x = 0; x < 200; x++) {
            chain().solve("t", 64);
        }
    }

    @Test
    public void theAmountOrderedDoesNotChangeTheCost() {
        // The one claim everything else rests on: a node is a thing, not a request for a thing.
        final long huge = 10_000_000_000L;

        final SolverPlan one = timed("one of it", () -> chain().solve("t", 1));
        final SolverPlan many = timed("ten billion of it", () -> chain().solve("t", huge));

        assertThat(one.isComplete(), is(true));
        assertThat(many.isComplete(), is(true));

        // Same plan, scaled exactly - and no overflow on the way.
        assertThat(crafts(one, "t"), is(1L));
        assertThat(crafts(many, "t"), is(huge));
        assertThat(crafts(many, "b"), is(huge));
        assertThat(one.getCrafts().size(), is(many.getCrafts().size()));
    }

    @Test
    public void aDeepChainDoesNotRunOutOfStack() {
        // The old tree recursed, so a chain this long was a StackOverflowError rather than a slow answer.
        final int depth = 10_000;
        final SolverTestNetwork network = new SolverTestNetwork();

        for (int x = 1; x <= depth; x++) {
            network.pattern("step" + x, "k" + x, "k" + (x - 1));
        }

        network.inStorage("k0", 1_000_000);

        final SolverPlan plan = timed("a chain " + depth + " long", () -> network.solve("k" + depth, 64));

        assertThat(plan.isComplete(), is(true));
        assertThat(crafts(plan, "step1"), is(64L));
        assertThat(crafts(plan, "step" + depth), is(64L));
    }

    @Test
    public void sharedBranchesAreNotWalkedTwice() {
        // Every level needs both of the level below it. A tree visits two to the power of the depth; there
        // are two nodes per level here, so twenty-four levels is a number no tree would ever come back from.
        final int levels = 24;
        final SolverTestNetwork network = new SolverTestNetwork();

        network.pattern("t", "t", "a0", "b0");

        for (int x = 0; x < levels; x++) {
            network.pattern("a" + x, "a" + x, "a" + (x + 1), "b" + (x + 1));
            network.pattern("b" + x, "b" + x, "a" + (x + 1), "b" + (x + 1));
        }

        network.inStorage("a" + levels, Long.MAX_VALUE / 4);
        network.inStorage("b" + levels, Long.MAX_VALUE / 4);

        final SolverPlan plan = timed(levels + " levels of shared branches", () -> network.solve("t", 1));

        assertThat(plan.isComplete(), is(true));
        // Doubling every level is what a tree would have had to enumerate one at a time.
        assertThat(crafts(plan, "a" + (levels - 1)), is(1L << (levels - 1)));
    }

    @Test
    public void manyPatternsCostTheirNumberAndNoMore() {
        final int branches = 3000;
        final SolverTestNetwork network = new SolverTestNetwork();
        final String[] inputs = new String[branches];

        for (int x = 0; x < branches; x++) {
            network.pattern("mid" + x, "mid" + x, "raw" + x);
            network.inStorage("raw" + x, 64);
            inputs[x] = "mid" + x;
        }

        network.pattern("t", "t", inputs);

        final SolverPlan plan = timed(branches + " patterns", () -> network.solve("t", 8));

        assertThat(plan.isComplete(), is(true));
        assertThat(plan.getCrafts().size(), is(branches + 1));
        assertThat(crafts(plan, "mid0"), is(8L));
    }

    @Test
    public void aLoopAtAnAbsurdSizeDividesOutAtOnce() {
        // The reason cycles are done by division rather than by going round: this is the shape that would
        // have hung, and the size at which it would have hung for good.
        final long huge = 10_000_000_000L;
        final SolverTestNetwork network = new SolverTestNetwork();
        network.pattern("loop", new GenericStack[] { network.stack("t", 2) },
                Arrays.asList(SolverIngredient.of(network.key("t"), 1),
                        SolverIngredient.of(network.key("base"), 1)));
        network.inStorage("t", 1);
        network.inStorage("base", Long.MAX_VALUE / 4);

        final SolverPlan plan = timed("ten billion round a loop", () -> network.solve("t", huge));

        assertThat(plan.isComplete(), is(true));
        assertThat(crafts(plan, "loop"), is(huge));
    }

    private static SolverTestNetwork chain() {
        return new SolverTestNetwork()
                .pattern("b", "b", "a")
                .pattern("t", "t", "b")
                .inStorage("a", Long.MAX_VALUE / 4);
    }

    private static SolverPlan timed(final String what, final Supplier<SolverPlan> solve) {
        final long started = System.nanoTime();
        final SolverPlan plan = solve.get();
        final long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        // Printed rather than only asserted: the number is the interesting part of this whole file.
        System.out.println("solved " + what + " in " + millis + " ms");
        assertThat(what + " took " + millis + " ms", millis, lessThan(GENEROUS_MILLIS));
        return plan;
    }
}
