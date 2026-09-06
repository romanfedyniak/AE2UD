/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.loot;


/**
 * Counts what a loot table has already handed out while one container is being filled.
 */
public interface ILootTallyer {

    boolean canRoll(int max, String itemName, int contextId);

    void tally(String itemName, int contextId);
}
