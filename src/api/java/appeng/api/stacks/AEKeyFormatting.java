/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Shared amount formatting for {@link AEKeyType#formatAmount(long, AmountFormat)}.
 * <p>
 * Lives in the API source set because {@code src/api} is compiled independently of {@code src/main}
 * and therefore cannot reach the mod's own number formatting helpers.
 */
final class AEKeyFormatting {

    private static final String[] SUFFIXES = { "", "K", "M", "G", "T", "P", "E" };

    private AEKeyFormatting() {
    }

    static String format(long amount, int amountPerUnit, String unitSymbol, AmountFormat format) {
        if (amountPerUnit <= 1) {
            return formatRaw(amount, format) + unitSymbol;
        }

        // Types measured in units (fluids in buckets) keep one fractional digit unless the amount
        // divides evenly, so 1500 mB reads as "1.5B" rather than "1B".
        long whole = amount / amountPerUnit;
        long remainder = amount % amountPerUnit;
        if (remainder == 0 || format == AmountFormat.SLOT) {
            return formatRaw(whole, format) + unitSymbol;
        }

        double value = (double) amount / amountPerUnit;
        DecimalFormat df = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return df.format(value) + unitSymbol;
    }

    private static String formatRaw(long amount, AmountFormat format) {
        switch (format) {
            case FULL:
                return String.format(Locale.ROOT, "%,d", amount);
            case PREVIEW_LARGE:
                return abbreviate(amount, 9999);
            case PREVIEW_REGULAR:
                return abbreviate(amount, 999);
            case SLOT:
                return abbreviate(amount, 99);
            default:
                return Long.toString(amount);
        }
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
