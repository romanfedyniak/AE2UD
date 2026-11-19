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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static appeng.crafting.solver.SolverTestNetwork.crafts;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;


public final class CraftingSolverAuditTest {

    private static SolverTestNetwork twoRoutes() {
        // r from an ingot (preferred) or from a diamond; the ingot only from ore.
        return new SolverTestNetwork()
                .pattern("viaIngot", "r", "ingot")
                .pattern("viaDiamond", "r", "diamond")
                .pattern("smelt", "ingot", "ore")
                .inStorage("diamond", 1000);
    }

    @Test
    public void aShortfallTwoLevelsDownFallsBackToTheOtherPattern() {
        final SolverTestNetwork network = twoRoutes();

        final SolverPlan plan = network.solve("r", 10);

        assertThat(plan.isComplete(), is(true));
        assertThat(crafts(plan, "viaIngot"), is(0L));
        assertThat(crafts(plan, "viaDiamond"), is(10L));
        assertThat(plan.getUsed().get(network.key("diamond")), is(10L));
    }

    @Test
    public void aShortfallTwoLevelsDownIsSharedWithTheOtherPattern() {
        final SolverTestNetwork network = twoRoutes().inStorage("ore", 4);

        final SolverPlan plan = network.solve("r", 10);

        assertThat(plan.isComplete(), is(true));
        assertThat(crafts(plan, "viaIngot"), is(4L));
        assertThat(crafts(plan, "viaDiamond"), is(6L));
        assertThat(plan.getUsed().get(network.key("ore")), is(4L));
    }

    private static SolverTestNetwork chest(final SolverTestNetwork network) {
        final List<SolverIngredient> slots = new ArrayList<>();
        for (int x = 0; x < 8; x++) {
            slots.add(new SolverIngredient(Arrays.asList(
                    new GenericStack(network.key("oak"), 1),
                    new GenericStack(network.key("spruce"), 1))));
        }
        return network.pattern("chest", new GenericStack[] { network.stack("chest", 1) }, slots);
    }

    @Test
    public void slotsOfOnePatternDoNotCountTheSameStockTwice() {
        final SolverTestNetwork network = chest(new SolverTestNetwork())
                .inStorage("oak", 4)
                .inStorage("spruce", 4);

        final SolverPlan plan = network.solve("chest", 1);

        assertThat(plan.isComplete(), is(true));
        assertThat(plan.getUsed().get(network.key("oak")), is(4L));
        assertThat(plan.getUsed().get(network.key("spruce")), is(4L));
    }

    @Test
    public void aStoredSubstituteIsUsedBeforeTheEncodedThingIsCrafted() {
        final SolverTestNetwork network = chest(new SolverTestNetwork())
                .pattern("planks", "oak", "log")
                .inStorage("oak", 4)
                .inStorage("spruce", 4)
                .inStorage("log", 100);

        final SolverPlan plan = network.solve("chest", 1);

        assertThat(plan.isComplete(), is(true));
        assertThat(crafts(plan, "planks"), is(0L));
        assertThat(plan.getUsed().get(network.key("spruce")), is(4L));
    }

    @Test
    public void twoPatternsDoNotBothCountOnTheSameStock() {
        final SolverTestNetwork network = new SolverTestNetwork();
        for (final String name : new String[] { "x", "y" }) {
            network.pattern(name, new GenericStack[] { network.stack(name, 1) },
                    Collections.singletonList(new SolverIngredient(Arrays.asList(
                            new GenericStack(network.key("copper"), 1),
                            new GenericStack(network.key("tin"), 1)))));
        }
        network.pattern("t", new GenericStack[] { network.stack("t", 1) },
                Arrays.asList(SolverIngredient.of(network.key("x"), 1), SolverIngredient.of(network.key("y"), 1)));
        network.inStorage("copper", 5).inStorage("tin", 5);

        final SolverPlan plan = network.solve("t", 5);

        assertThat(plan.isComplete(), is(true));
        assertThat(plan.getUsed().get(network.key("copper")), is(5L));
        assertThat(plan.getUsed().get(network.key("tin")), is(5L));
    }

    @Test
    public void theEncodedThingIsUsedWhenThereIsPlentyForEveryone() {
        // copper is "exclusive" (picky takes nothing else), but there is enough of it for both slots.
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
        network.inStorage("copper", 10000);
        network.inStorage("tin", 3);

        final SolverPlan plan = network.solve("t", 10);

        assertThat(plan.isComplete(), is(true));
        assertThat(plan.getUsed().get(network.key("copper")), is(20L));
        assertThat(plan.getUsed().get(network.key("tin")), is(0L));
    }

    @Test
    public void aByteTotalPastALongIsRefusedRatherThanWrapped() {
        final SolverTestNetwork network = new SolverTestNetwork().pattern("t", "t", "a");

        assertThrows(SolverTooLargeException.class, () -> network.solve("t", 1_000_000_000_000_000_000L));
    }

    @Test
    public void demandPastALongIsRefusedRatherThanSaturated() {
        final SolverTestNetwork network = new SolverTestNetwork();
        network.pattern("t", new GenericStack[] { network.stack("t", 1) },
                Arrays.asList(SolverIngredient.of(network.key("a"), 5), SolverIngredient.of(network.key("a"), 5)));

        assertThrows(SolverTooLargeException.class, () -> network.solve("t", 1_000_000_000_000_000_000L));
    }
}
