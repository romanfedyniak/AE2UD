/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers;

/** What is asked of the network about the ingredient under the cursor in JEI. */
public enum NetworkIngredientAction {
    /** Take it out of storage. Items only - a fluid has nowhere to go. */
    RETRIEVE,
    /** Order it made, which opens the amount to craft. Any key type. */
    CRAFT
}
