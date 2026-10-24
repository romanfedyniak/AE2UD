/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's pattern multi-tool (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.patternmultiplier;

/**
 * The pattern multi-tool's two pages, in the order NAE2 saved them.
 */
public enum PatternMultiplierTab {
    MULTIPLY, REPLACE;

    public static PatternMultiplierTab of(final int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : MULTIPLY;
    }
}
