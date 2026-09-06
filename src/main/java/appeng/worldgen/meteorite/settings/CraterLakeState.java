/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.worldgen.meteorite.settings;


/**
 * Whether a crater has water around it - three-valued, because it cannot be answered until a chunk near the
 * meteorite is being built, and "not asked yet" has to survive a save in between.
 */
public enum CraterLakeState {
    UNSET,
    TRUE,
    FALSE
}
