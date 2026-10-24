/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.patternmultiplier;

import javax.annotation.Nullable;

/**
 * A container that shows the carried pattern multi-tool beside its window.
 */
public interface IPatternMultiplierPanelHost {

    /**
     * The carried tool, or null when the player carries none.
     */
    @Nullable
    PatternMultiplierHost getPatternMultiplier();
}
