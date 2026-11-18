/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.parts.automation;


import java.util.HashMap;
import java.util.Map;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Enchantments;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/** What an annihilation plane's enchantments do to the power a broken block costs. */
public final class AnnihilationPlaneEnergyTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.register();
    }

    private static float factor(final Object... levels) {
        final Map<Enchantment, Integer> map = new HashMap<>();
        for (int i = 0; i < levels.length; i += 2) {
            map.put((Enchantment) levels[i], (Integer) levels[i + 1]);
        }
        return ItemPickupStrategy.enchantmentFactor(map);
    }

    @Test
    public void anUnenchantedPlanePaysTheBlock() {
        assertEquals(1f, factor());
    }

    @Test
    public void efficiencyAloneDoesNotMakeBreakingFree() {
        assertEquals(1f, factor(Enchantments.EFFICIENCY, 5));
    }

    @Test
    public void unbreakingOnlyGivesAChanceToPayNothing() {
        // The chance is applied on its own; the price, when paid, is what an unenchanted plane pays.
        assertEquals(1f, factor(Enchantments.UNBREAKING, 3));
    }

    @Test
    public void fortuneCostsMoreAndEfficiencyTakesSomeOfThatOff() {
        assertEquals(24f, factor(Enchantments.FORTUNE, 3), 0.001f);
        assertEquals(24f * 0.85f * 0.85f, factor(Enchantments.FORTUNE, 3, Enchantments.EFFICIENCY, 2), 0.001f);
        assertEquals(24f, factor(Enchantments.FORTUNE, 3, Enchantments.UNBREAKING, 3), 0.001f);
    }
}
