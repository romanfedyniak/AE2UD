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

    private static String preview(final long amount) {
        return AEKeyFormatting.format(amount, 1, "", AmountFormat.PREVIEW_LARGE);
    }

    private static String fluidPreview(final long amount) {
        return AEKeyFormatting.format(amount, 1000, "B", AmountFormat.PREVIEW_LARGE);
    }

    private static String narrowPreview(final long amount) {
        return AEKeyFormatting.format(amount, 1, "", AmountFormat.PREVIEW_REGULAR);
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
    public void aFormatDecidesOnlyWhenToAbbreviate() {
        // Up to its own threshold the number is written out...
        assertThat(preview(9999), is("9999"));
        assertThat(narrowPreview(999), is("999"));

        // ...and past it the suffix carries the magnitude, which is the same suffix either way.
        assertThat(preview(10000), is("10K"));
        assertThat(narrowPreview(1000), is("1K"));
        assertThat(preview(12315561), is("12M"));
        assertThat(narrowPreview(12315561), is("12M"));
    }

    @Test
    public void aSuffixedNumberStaysUnderAThousand() {
        // Six billion is 6G. It used to read "6000M": the search stopped as soon as the number fitted the
        // format's own width, and never reached the suffix that was sitting right there.
        assertThat(preview(6000000000L), is("6G"));
        assertThat(preview(1000000), is("1M"));
        assertThat(preview(1999999), is("1.9M"));
        assertThat(preview(9999999), is("9.9M"));
        assertThat(preview(999999), is("999K"));
        assertThat(narrowPreview(999999999), is("999M"));
    }

    @Test
    public void aPreviewNeverRoundsUpIntoAnAmountThatIsNotThere() {
        // One short of a million is not a million, at either width.
        assertThat(preview(999999), is("999K"));
        assertThat(narrowPreview(999999), is("999K"));
        assertThat(narrowPreview(9999), is("9.9K"));
        assertThat(preview(999999999999L), is("999G"));
    }

    @Test
    public void aFluidPreviewIsAbbreviatedWhetherOrNotItDividesEvenly() {
        // A remainder used to send the whole amount down a branch that ignored the format and printed
        // every digit: 12,315,561,040 mB read "12315561B".
        assertThat(fluidPreview(12315561000L), is("12MB"));
        assertThat(fluidPreview(12315561040L), is("12MB"));

        // 7,241 million buckets is 7.2G of them, not "7241MB".
        assertThat(fluidPreview(7241000000000L), is("7.2GB"));
        assertThat(fluidPreview(7241000000123L), is("7.2GB"));

        // Below the threshold the fractional digit is still worth having.
        assertThat(fluidPreview(1500), is("1.5B"));
        assertThat(fluidPreview(16750), is("16.7B"));
    }

    @Test
    public void theFullFormIsUnaffected() {
        assertThat(AEKeyFormatting.format(8128, 1, "", AmountFormat.FULL), is("8,128"));
        assertThat(AEKeyFormatting.format(1040, 1000, "B", AmountFormat.FULL_BASE), is("1,040mB"));
        assertThat(AEKeyFormatting.format(1500, 1000, "B", AmountFormat.FULL), is("1.5B"));

        // The exact reading keeps its digit grouping however large it gets.
        assertThat(AEKeyFormatting.format(12315561234L, 1000, "B", AmountFormat.FULL), is("12,315,561.2B"));
    }
}
