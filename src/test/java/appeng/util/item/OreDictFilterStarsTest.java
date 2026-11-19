/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.util.item;


import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Stars the bus screen lets through apart ("* *") reach the matcher together once the spaces are dropped. A rule
 * that is nothing but stars used to throw, and one that starts with two stars used to loop forever.
 */
public final class OreDictFilterStarsTest {

    private static boolean matches(final String expression, final String oreName) {
        final List<OreDictFilterMatcher.MatchRule> rules = OreDictFilterMatcher.parseExpression(expression);
        return assertTimeoutPreemptively(Duration.ofSeconds(2), () -> OreDictFilterMatcher.matches(rules, oreName));
    }

    @Test
    public void starsAloneMatchEverything() {
        assertTrue(matches("* *", "ingotIron"));
        assertTrue(matches("* *", ""));
    }

    @Test
    public void twoLeadingStarsDoNotLoop() {
        assertFalse(matches("* *a*b", "treeSapling"));
        assertFalse(matches("* *ingot*Iron", "gemDiamond"));
        assertTrue(matches("* *ingot*Iron", "ingotIron"));
        assertTrue(matches("* *a*b", "xaxb"));
    }

    @Test
    public void oneLeadingStarStillWorks() {
        assertTrue(matches("*Iron", "ingotIron"));
        assertFalse(matches("*Iron", "ingotGold"));
        assertTrue(matches("ingot*", "ingotGold"));
        assertTrue(matches("*a*b", "xaxb"));
        assertFalse(matches("*a*b", "xaxc"));
    }
}
