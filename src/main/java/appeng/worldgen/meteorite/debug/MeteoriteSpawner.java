/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.worldgen.meteorite.debug;


import appeng.worldgen.meteorite.MapGenMeteorite;
import appeng.worldgen.meteorite.fallout.FalloutMode;
import appeng.worldgen.meteorite.settings.CraterLakeState;
import appeng.worldgen.meteorite.settings.CraterType;
import appeng.worldgen.meteorite.settings.PlacedMeteoriteSettings;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


/**
 * Settles a meteorite where someone asked for one rather than where the world seed put it.
 * <p>
 * Height and crater lake are settled here rather than left for the terrain to answer: the chunks are all
 * there already, which is the one thing the generator never gets to assume.
 */
public class MeteoriteSpawner {

    public PlacedMeteoriteSettings trySpawnMeteorite(final World world, final BlockPos startPos,
            final float coreRadius, final CraterType craterType, final boolean pureCrater) {
        return new PlacedMeteoriteSettings(
                MapGenMeteorite.seedFor(world.getSeed(), startPos),
                startPos,
                coreRadius,
                craterType,
                pureCrater,
                CraterLakeState.FALSE,
                FalloutMode.fromBiome(world.getBiome(startPos)));
    }
}
