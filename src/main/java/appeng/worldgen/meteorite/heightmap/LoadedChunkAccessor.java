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


import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.EnumMap;


/**
 * Reads the heights off chunks that are already there. The fallback, for a dimension whose terrain this mod
 * has no way of working out on its own - and the reason a meteorite in such a dimension is not placed the
 * same way twice, since which chunks are loaded is not the same on every visit.
 */
public class LoadedChunkAccessor extends BaseChunkAccessor {

    private static final int CHUNK_SIZE = 16;

    public LoadedChunkAccessor(final World world) {
        super(world);
    }

    @Override
    protected EnumMap<HeightMapType, int[]> generateHeightMap(final ChunkPos chunkPos) {
        final Chunk chunk = this.world.getChunk(chunkPos.x, chunkPos.z);
        final int[] surface = chunk.heightMap.clone();
        final int[] oceanFloor = new int[surface.length];
        final int seaLevel = this.world.getSeaLevel();

        final MutableBlockPos pos = new MutableBlockPos();

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                final int index = (z << 4) | x;
                int h = surface[index];

                if (h <= seaLevel) {
                    // World coordinates: the chunk has to be found from them further down.
                    pos.setPos(chunkPos.getXStart() + x, h, chunkPos.getZStart() + z);
                    h = this.getSurfaceLevel(chunk, pos);
                }

                oceanFloor[index] = h;
            }
        }

        final EnumMap<HeightMapType, int[]> result = new EnumMap<>(HeightMapType.class);
        result.put(HeightMapType.WORLD_SURFACE, surface);
        result.put(HeightMapType.OCEAN_FLOOR, oceanFloor);
        return result;
    }

    /** The top-most block that is neither liquid nor something growing out of the ground. */
    private int getSurfaceLevel(final Chunk chunk, final MutableBlockPos pos) {
        for (; pos.getY() >= 0; pos.move(EnumFacing.DOWN)) {
            final IBlockState state = chunk.getBlockState(pos);

            if (state.getMaterial().blocksMovement()
                    && !state.getBlock().isLeaves(state, this.world, pos)
                    && !state.getBlock().isFoliage(this.world, pos)) {
                break;
            }
        }

        return pos.getY();
    }

    @Override
    public Type getAccessorType() {
        return Type.LOADED;
    }
}
