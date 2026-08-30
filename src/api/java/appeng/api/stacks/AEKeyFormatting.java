/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.stacks;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Format;
import java.util.Locale;

/**
 * Shared amount formatting for {@link AEKeyType#formatAmount(long, AmountFormat)}.
 * <p>
 * Lives in the API source set because {@code src/api} is compiled independently of {@code src/main}
 * and therefore cannot reach the mod's own number formatting helpers.
 */
final class AEKeyFormatting {

    private static final String[] SUFFIXES = { "", "K", "M", "G", "T", "P", "E" };

    /** Prefix for the base unit of a type measured in larger ones - millibuckets for fluids. */
    private static final String MILLI_PREFIX = "m";

    /** Characters a slot gives an amount, unit included. */
    private static final int SLOT_BUDGET = 4;

    /** Keeps one fractional digit while it fits, and never rounds an amount up into one it is not. */
    private static final Format PRECISE_FORM = precisionFormat();

    private static Format precisionFormat() {
        final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setDecimalSeparator('.');
        final DecimalFormat format = new DecimalFormat(".#;0.#", symbols);
        format.setRoundingMode(RoundingMode.DOWN);
        return format;
    }

    private AEKeyFormatting() {
    }

    static String format(long amount, int amountPerUnit, String unitSymbol, AmountFormat format) {
        if (amountPerUnit <= 1) {
            return formatRaw(amount, format, slotWidth(unitSymbol)) + unitSymbol;
        }

        // Asked for the base unit outright: 1,040 mB, never rounded. Shift in a terminal tooltip uses this.
        if (format == AmountFormat.FULL_BASE) {
            return formatRaw(amount, AmountFormat.FULL, SLOT_BUDGET) + MILLI_PREFIX + unitSymbol;
        }

        // Less than one whole unit. Rounding it into units is what "0B" was: 40 mB divided by 1000 is
        // 0.04, and the one-fractional-digit format below prints that as "0". Below a unit the base unit
        // is the only honest reading, so 40 mB reads "40mB".
        if (amount != 0 && amount > -amountPerUnit && amount < amountPerUnit) {
            return formatRaw(amount, format, slotWidth(MILLI_PREFIX + unitSymbol)) + MILLI_PREFIX + unitSymbol;
        }

        // Types measured in units (fluids in buckets) keep one fractional digit unless the amount
        // divides evenly, so 1500 mB reads as "1.5B" rather than "1B".
        long whole = amount / amountPerUnit;
        long remainder = amount % amountPerUnit;
        if (remainder == 0 || format == AmountFormat.SLOT) {
            return formatRaw(whole, format, slotWidth(unitSymbol)) + unitSymbol;
        }

        double value = (double) amount / amountPerUnit;
        DecimalFormat df = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return df.format(value) + unitSymbol;
    }

    /**
     * How many characters the number itself gets in a slot: the budget, less whatever the unit takes.
     * Never below three, because two leaves nothing worth reading - a hundred of anything abbreviates to
     * "0K" at that width.
     */
    private static int slotWidth(String unitSymbol) {
        return Math.max(3, SLOT_BUDGET - unitSymbol.length());
    }

    private static String formatRaw(long amount, AmountFormat format, int slotWidth) {
        switch (format) {
            case FULL:
            // A type with only one unit - items - has nothing to convert, so the exact reading is the
            // full one. Without this it fell through to the default and lost its digit grouping, turning
            // a shift-held item tooltip from "8,128" into "8128".
            case FULL_BASE:
                return String.format(Locale.ROOT, "%,d", amount);
            case PREVIEW_LARGE:
                return abbreviate(amount, 9999);
            case PREVIEW_REGULAR:
                return abbreviate(amount, 999);
            case SLOT:
                return abbreviateToWidth(amount, slotWidth);
            default:
                return Long.toString(amount);
        }
    }

    /**
     * Abbreviates until the result is no wider than {@code width} characters, which is what a slot has to
     * spend. Upstream defines {@link AmountFormat#SLOT} the same way - as a width, not as a threshold.
     * <p>
     * The twin of this once lived in {@code appeng.util.ReadableNumberConverter}; it is here because
     * {@code src/api} is compiled on its own and cannot reach {@code src/main}.
     */
    private static String abbreviateToWidth(long amount, int width) {
        final String plain = Long.toString(amount);
        int size = plain.length();

        if (size <= width) {
            return plain;
        }

        long base = amount;
        double last = base;
        int exponent = 0;
        String postFix = "";

        while (size > width && exponent < SUFFIXES.length - 1) {
            last = base;
            base /= 1000;
            exponent++;
            // The postfix takes a character of its own.
            size = Long.toString(base).length() + 1;
            postFix = SUFFIXES[exponent];
        }

        final String withPrecision = PRECISE_FORM.format(last / 1000.0D) + postFix;
        return withPrecision.length() <= width ? withPrecision : base + postFix;
    }

    /**
     * Abbreviates once the value no longer fits below {@code threshold}, keeping one fractional
     * digit while it fits.
     */
    private static String abbreviate(long amount, long threshold) {
        if (amount <= threshold) {
            return Long.toString(amount);
        }

        double value = amount;
        int suffix = 0;
        while (value > threshold && suffix < SUFFIXES.length - 1) {
            value /= 1000.0D;
            suffix++;
        }

        DecimalFormat df = value < 10.0D
                ? new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT))
                : new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return df.format(value) + SUFFIXES[suffix];
    }
}
