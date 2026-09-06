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


import appeng.util.StructureBoundingBoxUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.EnumMap;


public abstract class BaseChunkAccessor implements IHeightMapGeneratableAccessor {

    protected final World world;
    protected final Long2ObjectMap<EnumMap<HeightMapType, int[]>> heightMaps = new Long2ObjectOpenHashMap<>();

    protected StructureBoundingBox area;

    protected BaseChunkAccessor(final World world) {
        this.world = world;
    }

    @Override
    public StructureBoundingBox getAffectedArea() {
        return this.area;
    }

    /**
     * One of these can answer about the ground around the meteorite wherever it is; the other only about the
     * chunks that happen to be loaded, so it is asked about those instead.
     */
    @Override
    public void determineArea(final StructureBoundingBox loadedBB, final StructureBoundingBox centerBB) {
        this.area = this.getAccessorType() == Type.NOISE_BASED ? centerBB : loadedBB;
    }

    @Override
    public Long2ObjectMap<EnumMap<HeightMapType, int[]>> getHeightMaps() {
        return this.heightMaps;
    }

    @Override
    public void generateHeightMaps() {
        this.heightMaps.clear();

        for (final ChunkPos pos : StructureBoundingBoxUtils.getChunksWithin(this.getAffectedArea())) {
            this.heightMaps.put(ChunkPos.asLong(pos.x, pos.z), this.generateHeightMap(pos));
        }
    }

    protected abstract EnumMap<HeightMapType, int[]> generateHeightMap(ChunkPos pos);
}
