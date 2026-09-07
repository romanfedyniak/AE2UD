/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui;


import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public final class AmountEntryTest {

    @Test
    public void aNumberTypedAsItselfIsReadAsItself() {
        // Nine quadrillion and one is the first whole number a double cannot hold, and the field is long
        // enough to type it. Worked out as a double it comes back one short.
        assertThat(AmountEntry.parse("9007199254740993", 1), is(9007199254740993L));
        assertThat(AmountEntry.parse("1234567890123456789", 1), is(1234567890123456789L));
    }

    @Test
    public void aNumberTooBigForTheAmountIsHeldAtTheTop() {
        assertThat(AmountEntry.parse("99999999999999999999999", 1), is(Long.MAX_VALUE));
    }

    @Test
    public void aFractionIsReadInTheUnitTheFieldShows() {
        // A bucket is a thousand of what the network counts, so one and a half of them is 1500.
        assertThat(AmountEntry.parse("1.5", 1000), is(1500L));
        assertThat(AmountEntry.parse("0.001", 1000), is(1L));
    }

    @Test
    public void somethingToWorkOutIsStillWorkedOut() {
        assertThat(AmountEntry.parse("2*3", 1), is(6L));
        assertThat(AmountEntry.parse("64+64", 1), is(128L));
    }

    @Test
    public void aFieldHoldingNoNumberIsNothing() {
        assertThat(AmountEntry.parse("", 1), is(0L));
        assertThat(AmountEntry.parse("   ", 1), is(0L));
        assertThat(AmountEntry.parse("1.2.3", 1), is(0L));
        assertThat(AmountEntry.parse("0", 1), is(0L));
    }

    @Test
    public void spaceRoundANumberIsNotPartOfIt() {
        assertThat(AmountEntry.parse(" 42 ", 1), is(42L));
    }

    @Test
    public void aSignedFieldReadsBothWays() {
        assertThat(AmountEntry.parseSigned("-5", 1), is(-5L));
        assertThat(AmountEntry.parseSigned("5", 1), is(5L));
    }
}
