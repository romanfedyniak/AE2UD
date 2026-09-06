/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package appeng.worldgen.meteorite.fallout;


import com.google.common.collect.ImmutableList;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import java.util.List;


/**
 * What the ground thrown out of the crater is made of. Read from the biome rather than from the block under
 * the meteorite, because the biome can be asked about a chunk that has not been generated and the block
 * cannot - and reading the block is what used to make where a meteorite landed depend on chunk load order.
 * <p>
 * Written to disk by ordinal: append only, never reorder or remove.
 */
public enum FalloutMode {

    /** Nothing is thrown out, for a meteorite with no crater. */
    NONE,

    DEFAULT,

    SAND(BiomeDictionary.Type.SANDY, BiomeDictionary.Type.BEACH),

    TERRACOTTA(BiomeDictionary.Type.MESA),

    ICE_SNOW(BiomeDictionary.Type.COLD);

    private final List<BiomeDictionary.Type> biomeTypes;

    FalloutMode(final BiomeDictionary.Type... biomeTypes) {
        this.biomeTypes = ImmutableList.copyOf(biomeTypes);
    }

    public static FalloutMode fromBiome(final Biome biome) {
        for (final FalloutMode mode : values()) {
            if (mode.matches(biome)) {
                return mode;
            }
        }

        return DEFAULT;
    }

    public boolean matches(final Biome biome) {
        for (final BiomeDictionary.Type biomeType : this.biomeTypes) {
            if (BiomeDictionary.hasType(biome, biomeType)) {
                return true;
            }
        }

        return false;
    }
}
