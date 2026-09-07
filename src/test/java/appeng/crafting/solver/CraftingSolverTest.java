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
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static appeng.crafting.solver.SolverTestNetwork.crafts;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;


public final class CraftingSolverTest {

    @Test
    public void aChainIsPlannedStepByStep() {
        final SolverTestNetwork network = new SolverTestNetwork()
                .pattern("b", "b", "a")
                .pattern("c", "c", "b")
                .pattern("t", "t", "c")
                .inStorage("a", 4096);

        final SolverPlan plan = network.solve("t", 64);

        assertThat(plan.isComplete(), is(true));
        assertThat(crafts(plan, "t"), is(64L));
        assertThat(crafts(plan, "c"), is(64L));
        assertThat(crafts(plan, "b"), is(64L));
        assertThat(plan.getUsed().get(network.key("a")), is(64L));
    }

    @Test
    public void aSharedLeafIsSolvedOnce() {
        // Both branches want the same base. A tree walks it twice and can disagree with itself; here the
        // whole demand for it is known before any of it is handed out.
        final SolverTestNetwork diamond = new SolverTestNetwork();
        diamond.pattern("x", new GenericStack[] { diamond.stack("x", 1) },
                Collections.singletonList(SolverIngredient.of(diamond.key("base"), 2)));
        diamond.pattern("y", new GenericStack[] { diamond.stack("y", 1) },
                Collections.singletonList(SolverIngredient.of(diamond.key("base"), 3)));
        diamond.pattern("t", new GenericStack[] { diamond.stack("t", 1) },
                Arrays.asList(SolverIngredient.of(diamond.key("x"), 1),
                        SolverIngredient.of(diamond.key("y"), 1)));
        diamond.inStorage("base", 100000);

        final SolverPlan plan = diamond.solve("t", 100);

        assertThat(plan.isComplete(), is(true));
        assertThat(crafts(plan, "x"), is(100L));
        assertThat(crafts(plan, "y"), is(100L));
        assertThat(plan.getUsed().get(diamond.key("base")), is(500L));
    }

    @Test
    public void aPatternThatRunsOutSpillsOntoTheNext() {
        final SolverTestNetwork network = new SolverTestNetwork()
                .pattern("fast", "t", "dust")
                .pattern("slow", "t", "ore")
                .inStorage("dust", 250)
                .inStorage("ore", 1000);

        final SolverPlan plan = network.solve("t", 400);

        assertThat(plan.isComplete(), is(true));
        assertThat(crafts(plan, "fast"), is(250L));
        assertThat(crafts(plan, "slow"), is(150L));
        assertThat(plan.getUsed().get(network.key("dust")), is(250L));
        assertThat(plan.getUsed().get(network.key("ore")), is(150L));
    }

    @Test
    public void whatIsLackingIsReportedRatherThanThrown() {
        final SolverTestNetwork network = new SolverTestNetwork()
                .pattern("t", "t", "base")
                .inStorage("base", 4);

        final SolverPlan plan = network.solve("t", 10);

        assertThat(plan.isComplete(), is(false));
        assertThat(plan.getMissing().get(network.key("base")), is(6L));
        assertThat(plan.getUsed().get(network.key("base")), is(4L));
        // Still a whole plan: the four that can be made are planned, not abandoned.
        assertThat(crafts(plan, "t"), is(10L));
    }

    @Test
    public void bothPatternsRunOutAndOnlyTheRestIsMissing() {
        final SolverTestNetwork network = new SolverTestNetwork()
                .pattern("fast", "t", "dust")
                .pattern("slow", "t", "ore")
                .inStorage("dust", 250)
                .inStorage("ore", 100);

        final SolverPlan plan = network.solve("t", 400);

        assertThat(plan.isComplete(), is(false));
        assertThat(crafts(plan, "fast"), is(250L));
        assertThat(crafts(plan, "slow"), is(150L));
        assertThat(plan.getUsed().get(network.key("dust")), is(250L));
        assertThat(plan.getUsed().get(network.key("ore")), is(100L));
        assertThat(plan.getMissing().get(network.key("ore")), is(50L));
    }

    @Test
    public void anEmitterAnswersInsteadOfAPattern() {
        final SolverTestNetwork network = new SolverTestNetwork()
                .pattern("t", "t", "base")
                .emits("t");

        final SolverPlan plan = network.solve("t", 10);

        assertThat(plan.isComplete(), is(true));
        assertThat(plan.getEmitted().get(network.key("t")), is(10L));
        assertThat(crafts(plan, "t"), is(0L));
    }

    @Test
    public void aByproductFeedsTheStepThatWantsIt() {
        final SolverTestNetwork network = new SolverTestNetwork();
        network.pattern("ab", new GenericStack[] { network.stack("a", 1), network.stack("b", 1) },
                Collections.singletonList(SolverIngredient.of(network.key("base"), 1)));
        network.pattern("t", new GenericStack[] { network.stack("t", 1) },
                Arrays.asList(SolverIngredient.of(network.key("a"), 1),
                        SolverIngredient.of(network.key("b"), 1)));
        network.inStorage("base", 1000);

        final SolverPlan plan = network.solve("t", 10);

        assertThat(plan.isComplete(), is(true));
        // Ten crafts, not twenty: the b was already made alongside the a.
        assertThat(crafts(plan, "ab"), is(10L));
        assertThat(plan.getUsed().get(network.key("base")), is(10L));
    }

    @Test
    public void storageGoesToTheSlotWithNoChoice() {
        // Both patterns can be fed with copper; only "picky" insists on it. Left to the order they are
        // written in, the choosy one would take the last of it and the other would be short.
        final SolverTestNetwork network = new SolverTestNetwork();
        network.pattern("choosy", new GenericStack[] { network.stack("x", 1) },
                Collections.singletonList(new SolverIngredient(Arrays.asList(
                        new GenericStack(network.key("copper"), 1),
                        new GenericStack(network.key("tin"), 1)))));
        network.pattern("picky", new GenericStack[] { network.stack("y", 1) },
                Collections.singletonList(SolverIngredient.of(network.key("copper"), 1)));
        network.pattern("t", new GenericStack[] { network.stack("t", 1) },
                Arrays.asList(SolverIngredient.of(network.key("x"), 1),
                        SolverIngredient.of(network.key("y"), 1)));
        network.inStorage("copper", 10);
        network.inStorage("tin", 1000);

        final SolverPlan plan = network.solve("t", 10);

        assertThat(plan.isComplete(), is(true));
        assertThat(plan.getUsed().get(network.key("copper")), is(10L));
        assertThat(plan.getUsed().get(network.key("tin")), is(10L));
    }

    @Test
    public void aCycleIsLeftAloneRatherThanLoopedOver() {
        // One craft eats one t and makes two. The old tree refuses this outright; this says so plainly and,
        // above all, comes back.
        final SolverTestNetwork network = new SolverTestNetwork();
        network.pattern("t", new GenericStack[] { network.stack("t", 2) },
                Arrays.asList(SolverIngredient.of(network.key("t"), 1),
                        SolverIngredient.of(network.key("base"), 1)));
        network.inStorage("base", 1000);

        final SolverPlan plan = network.solve("t", 100);

        assertThat(plan.isCyclic(), is(true));
        assertThat(plan.isComplete(), is(false));
        assertThat(plan.getMissing().get(network.key("t")), is(100L));
    }

    @Test
    public void bytesCountWhatFlowsAndWhatIsMade() {
        final SolverTestNetwork network = new SolverTestNetwork()
                .pattern("t", "t", "a")
                .inStorage("a", 100);

        final SolverPlan plan = network.solve("t", 10);

        // Ten of the output and ten of the ingredient passed through, and ten crafts cost eight each.
        assertThat(plan.getBytes(), is(10L + 10L + 80L));
    }

    @Test
    public void aFluidIsChargedByTheBucket() {
        final SolverTestNetwork network = new SolverTestNetwork();
        network.pattern("t", new GenericStack[] { network.stack("t", 1) },
                Collections.singletonList(new SolverIngredient(
                        Collections.singletonList(new GenericStack(network.fluid("water"), 1000)))));
        network.getStock().add(network.fluid("water"), 100000);

        final SolverPlan plan = network.solve("t", 10);

        assertThat(plan.isComplete(), is(true));
        // Ten thousand millibuckets is ten buckets, not ten thousand bytes.
        assertThat(plan.getBytes(), is(10L + 10L + 80L));
    }

    @Test
    public void anOrderTooBigForALongIsRefusedRatherThanWrapped() {
        final SolverTestNetwork network = new SolverTestNetwork();
        network.pattern("t", new GenericStack[] { network.stack("t", 1) },
                Collections.singletonList(SolverIngredient.of(network.key("a"), 1_000_000_000L)));

        assertThrows(SolverTooLargeException.class, () -> network.solve("t", Long.MAX_VALUE / 2));
    }

    @Test
    public void aGraphTooBigIsRefused() {
        final SolverTestNetwork network = new SolverTestNetwork();

        for (int x = 1; x <= 50; x++) {
            network.pattern("step" + x, "k" + x, "k" + (x - 1));
        }

        network.inStorage("k0", 1000);

        assertThat(network.solve("k50", 1).isComplete(), is(true));
        assertThrows(SolverTooLargeException.class,
                () -> network.solve("k50", 1, new SolverLimits(10, 1_000_000, 16)));
    }

    @Test
    public void nothingOrderedIsARefusal() {
        final SolverTestNetwork network = new SolverTestNetwork().pattern("t", "t", "a");

        assertThrows(IllegalArgumentException.class, () -> network.solve("t", 0));
    }

    @Test
    public void aPatternMakingSeveralAtOnceRoundsUpAndKeepsTheRest() {
        final SolverTestNetwork network = new SolverTestNetwork();
        network.pattern("t", new GenericStack[] { network.stack("t", 4) },
                Collections.singletonList(SolverIngredient.of(network.key("a"), 1)));
        network.inStorage("a", 100);

        final SolverPlan plan = network.solve("t", 10);

        // Three crafts make twelve; the two left over are still counted as made.
        assertThat(crafts(plan, "t"), is(3L));
        assertThat(plan.producedOf(network.key("t")), is(12L));
        assertThat(plan.getUsed().get(network.key("a")), is(3L));
        assertThat(plan.getBytes(), greaterThan(0L));
    }
}
