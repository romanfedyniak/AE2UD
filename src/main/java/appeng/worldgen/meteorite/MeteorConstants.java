/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2020, AlgorithmX2, All rights reserved.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.worldgen.meteorite;


public final class MeteorConstants {

    /** A meteorite whose height the terrain has not been asked about yet. */
    public static final int UNSET_HEIGHT = Integer.MIN_VALUE;

    /** What fills the chest, in {@code assets/appliedenergistics2/loot_tables}. */
    public static final String METEOR_LOOT_TABLE = "meteor_loot";

    public static final int MIN_METEOR_RADIUS = 2;
    public static final int MAX_METEOR_RADIUS = 8;

    private MeteorConstants() {
    }
}
