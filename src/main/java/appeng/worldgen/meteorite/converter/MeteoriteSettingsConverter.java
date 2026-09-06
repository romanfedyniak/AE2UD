/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.worldgen.meteorite.converter;


import appeng.util.Platform;
import appeng.worldgen.meteorite.MapGenMeteorite;
import appeng.worldgen.meteorite.fallout.FalloutMode;
import appeng.worldgen.meteorite.settings.CraterLakeState;
import appeng.worldgen.meteorite.settings.CraterType;
import appeng.worldgen.meteorite.settings.PlacedMeteoriteSettings;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;


/**
 * Reads what the old generator wrote about one meteorite into the settings the new one works from.
 */
public class MeteoriteSettingsConverter {

    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";
    private static final String TAG_BLOCK = "blk";
    private static final String TAG_METEORITE_RADIUS = "real_sizeOfMeteorite";
    private static final String TAG_SKY_MODE = "skyMode";

    private MeteoriteSettingsConverter() {
    }

    public static PlacedMeteoriteSettings convertOld(final NBTTagCompound oldSettings, final World world) {
        final BlockPos pos = new BlockPos(oldSettings.getInteger(TAG_X), oldSettings.getInteger(TAG_Y),
                oldSettings.getInteger(TAG_Z));
        final int skyMode = oldSettings.getInteger(TAG_SKY_MODE);

        // Lava is not carried over: the blocks of a half-built meteorite go in without waking their
        // neighbours, and a fluid placed that way does not flow.
        final CraterType craterType = skyMode <= 10 ? CraterType.NONE : CraterType.NORMAL;
        final FalloutMode falloutMode = determineFalloutMode(Block.getBlockById(oldSettings.getInteger(TAG_BLOCK)));

        return new PlacedMeteoriteSettings(generateSeed(pos, world), pos,
                (float) oldSettings.getDouble(TAG_METEORITE_RADIUS), craterType, false, CraterLakeState.FALSE,
                falloutMode, skyMode > 3);
    }

    /** The old fallout was chosen by the block underneath, which is what this reads back. */
    private static FalloutMode determineFalloutMode(final Block falloutBlock) {
        if (falloutBlock == Blocks.SAND) {
            return FalloutMode.SAND;
        }
        if (falloutBlock == Blocks.HARDENED_CLAY) {
            return FalloutMode.TERRACOTTA;
        }
        if (falloutBlock == Blocks.ICE || falloutBlock == Blocks.SNOW) {
            return FalloutMode.ICE_SNOW;
        }

        return FalloutMode.DEFAULT;
    }

    /**
     * The old meteorite had no seed of its own, so it is given the one its cell would have produced - the
     * same draw {@link MapGenMeteorite#getStructureStart} makes, after the two the position took.
     */
    public static long generateSeed(final BlockPos pos, final World world) {
        final int gridCellSize = MapGenMeteorite.gridCellSize();
        final int gridCellMargin = MapGenMeteorite.gridCellMargin(gridCellSize);
        final int gridX = Math.floorDiv((pos.getX() >> 4) << 4, gridCellSize);
        final int gridZ = Math.floorDiv((pos.getZ() >> 4) << 4, gridCellSize);

        final Random rand = new Random();
        Platform.seedFromGrid(rand, world.getSeed(), gridX, gridZ, 0);
        rand.nextInt(gridCellSize - 2 * gridCellMargin);
        rand.nextInt(gridCellSize - 2 * gridCellMargin);

        long meteorSeed = rand.nextLong();
        while (meteorSeed == 0) {
            meteorSeed = rand.nextLong();
        }

        return meteorSeed;
    }
}
