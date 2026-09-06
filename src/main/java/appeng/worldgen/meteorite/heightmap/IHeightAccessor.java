/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.worldgen.meteorite.heightmap;


import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.EnumMap;


/**
 * How high the ground is at a position, answered without necessarily having the chunk.
 */
public interface IHeightAccessor {

    /** What a position outside anything this accessor knows about is worth. */
    int SEA_LEVEL = 63;

    StructureBoundingBox getAffectedArea();

    void determineArea(StructureBoundingBox loadedBB, StructureBoundingBox centerBB);

    Long2ObjectMap<EnumMap<HeightMapType, int[]>> getHeightMaps();

    default int getHeight(final BlockPos pos, final HeightMapType heightType) {
        final EnumMap<HeightMapType, int[]> maps = this.getHeightMaps()
                .get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));

        return maps == null ? SEA_LEVEL : maps.get(heightType)[(pos.getZ() & 15) << 4 | (pos.getX() & 15)];
    }

    Type getAccessorType();

    enum Type {
        /** Samples chunks that happen to be loaded, so its answer depends on which those are. */
        LOADED,

        /** Works the terrain out from the world seed, so its answer is the same however the world is walked. */
        NOISE_BASED
    }

    enum HeightMapType {
        /** The top of everything, water included. */
        WORLD_SURFACE,

        /** The top of the ground under any water. */
        OCEAN_FLOOR
    }
}
