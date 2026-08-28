package appeng.api.stacks;


import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


/**
 * The slot form is what a 16x16 slot has room for, so it is fixed in width rather than in magnitude.
 * The item table below is the one that guarded {@code ReadableNumberConverter}, whose width-limiting
 * this now owns.
 */
public final class AEKeyFormattingTest {

    private static String item(final long amount) {
        return AEKeyFormatting.format(amount, 1, "", AmountFormat.SLOT);
    }

    private static String fluid(final long amount) {
        return AEKeyFormatting.format(amount, 1000, "B", AmountFormat.SLOT);
    }

    @Test
    public void anAmountThatFitsIsWrittenOut() {
        assertThat(item(0), is("0"));
        assertThat(item(100), is("100"));
        assertThat(item(999), is("999"));
        assertThat(item(9999), is("9999"));
    }

    @Test
    public void aWiderAmountIsAbbreviatedToFourCharacters() {
        assertThat(item(10000), is("10K"));
        assertThat(item(10500), is("10K"));
        assertThat(item(155555), is("155K"));
        assertThat(item(9999999), is("9.9M"));
        assertThat(item(10000000), is("10M"));
        assertThat(item(155555555), is("155M"));
    }

    @Test
    public void anAbbreviationRoundsDown() {
        // 10500 is ten thousand and a half, and "11K" would be a promise of more than there is.
        assertThat(item(10500), is("10K"));
        assertThat(item(19999), is("19K"));
    }

    @Test
    public void aWholeUnitIsCountedInUnits() {
        assertThat(fluid(1000), is("1B"));
        assertThat(fluid(16000), is("16B"));
        assertThat(fluid(150000), is("150B"));
        assertThat(fluid(1500000), is("1KB"));
        assertThat(fluid(12345000), is("12KB"));
    }

    @Test
    public void lessThanAUnitIsCountedInBaseUnits() {
        assertThat(fluid(1), is("1mB"));
        assertThat(fluid(40), is("40mB"));
        assertThat(fluid(250), is("250mB"));
        assertThat(fluid(999), is("999mB"));
    }

    @Test
    public void aRemainderIsDroppedRatherThanShown() {
        // The slot has no room for it, and the tooltip carries the exact figure.
        assertThat(fluid(1500), is("1B"));
        assertThat(fluid(16750), is("16B"));
    }

    @Test
    public void theFullFormIsUnaffected() {
        assertThat(AEKeyFormatting.format(8128, 1, "", AmountFormat.FULL), is("8,128"));
        assertThat(AEKeyFormatting.format(1040, 1000, "B", AmountFormat.FULL_BASE), is("1,040mB"));
        assertThat(AEKeyFormatting.format(1500, 1000, "B", AmountFormat.FULL), is("1.5B"));
    }
}
