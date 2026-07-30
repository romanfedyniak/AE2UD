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

/**
 * How an amount of a given {@link AEKey} should be rendered as text.
 */
public enum AmountFormat {
    /**
     * The full amount, with digit group separators. Used in tooltips.
     */
    FULL,

    /**
     * The full amount in the type's <em>base</em> unit, with digit group separators and no conversion:
     * "1,040mB" where {@link #FULL} would say "1B". For a type that has only one unit - items - this is
     * identical to {@link #FULL}. Used where the exact number matters more than readability, which in
     * practice means a tooltip with shift held.
     */
    FULL_BASE,

    /**
     * Abbreviated form (1.2K), used where horizontal space is limited but still readable,
     * such as terminal rows.
     */
    PREVIEW_REGULAR,

    /**
     * Like {@link #PREVIEW_REGULAR}, but keeps more significant digits.
     */
    PREVIEW_LARGE,

    /**
     * Shortest possible form, meant to be drawn on top of a 16x16 slot icon.
     */
    SLOT
}
