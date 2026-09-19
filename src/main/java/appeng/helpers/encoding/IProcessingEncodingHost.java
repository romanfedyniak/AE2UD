/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.encoding;

import appeng.api.patterns.IPatternEncodingHost;

/** The one piece of terminal state only processing mode has: which of its two grids is the compact one. */
public interface IProcessingEncodingHost extends IPatternEncodingHost {

    boolean isInverted();

    void setInverted(boolean inverted);
}
