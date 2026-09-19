/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.util;

/**
 * The block in the middle of a cable, where its connections meet. Which one a cable wears follows from its
 * {@link AECableType}, except that a glass cable carrying a part wears the covered core instead.
 */
public enum AECableCore {
    GLASS,
    COVERED,
    DENSE
}
