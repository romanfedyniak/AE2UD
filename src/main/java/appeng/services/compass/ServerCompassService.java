/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.services.compass;


import appeng.api.AEApi;
import appeng.block.storage.BlockSkyChest;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketClearCompassCache;
import appeng.tile.storage.TileSkyChest;
import com.github.bsideup.jabel.Desugar;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


/**
 * Where the nearest meteorite is, answered from {@link CompassRegion} rather than by reading the world.
 * <p>
 * Nothing here walks a chunk unless something in it actually changed: a chunk is scanned when a meteorite is
 * placed in it, when a natural sky stone chest is broken, and once per old chunk while a save from before
 * this is being converted.
 */
public final class ServerCompassService {

    private static final int MAX_RANGE = 174;
    private static final int CHUNK_SIZE = 16;

    /**
     * Answers do not change often and a client may ask for any chunk it likes, so the last few are kept.
     * <p>
     * Keyed by dimension and position rather than by the world itself: a key holding the world would either
     * outlive it or, made weak to avoid that, be compared by identity - and a key built fresh on every call
     * never matches by identity, so the cache would never once be hit.
     */
    private static final Cache<Query, Optional<BlockPos>> CLOSEST_METEORITE_CACHE = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();

    private ServerCompassService() {
    }

    @Desugar
    private record Query(int dimension, long chunk) {
    }

    private static Query query(final WorldServer world, final ChunkPos chunkPos) {
        return new Query(world.provider.getDimension(), ChunkPos.asLong(chunkPos.x, chunkPos.z));
    }

    public static Optional<BlockPos> getClosestMeteorite(final WorldServer world, final ChunkPos chunkPos) {
        try {
            return CLOSEST_METEORITE_CACHE.get(query(world, chunkPos),
                    () -> Optional.ofNullable(findClosestMeteoritePos(world, chunkPos)));
        } catch (final ExecutionException e) {
            return Optional.empty();
        }
    }

    @Nullable
    private static BlockPos findClosestMeteoritePos(final WorldServer world, final ChunkPos originChunkPos) {
        final ChunkPos chunkPos = findClosestMeteoriteChunk(world, originChunkPos);
        if (chunkPos == null) {
            return null;
        }

        final Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            // Never load a chunk just to sharpen an answer the middle of it is good enough for.
            return getMiddleBlockPosition(chunkPos);
        }

        final BlockPos sourcePos = getMiddleBlockPosition(originChunkPos);
        double closestDistanceSq = Double.MAX_VALUE;
        BlockPos chosenPos = getMiddleBlockPosition(chunkPos);

        for (final TileEntity tileEntity : chunk.getTileEntityMap().values()) {
            if (tileEntity instanceof TileSkyChest) {
                final BlockPos tePos = tileEntity.getPos();
                final double distSq = sourcePos.distanceSq(tePos.getX(), 0, tePos.getZ());

                if (distSq < closestDistanceSq) {
                    chosenPos = tePos;
                    closestDistanceSq = distSq;
                }
            }
        }

        return chosenPos;
    }

    private static BlockPos getMiddleBlockPosition(final ChunkPos chunk) {
        return new BlockPos(chunk.getBlock(8, 0, 8));
    }

    @Nullable
    private static ChunkPos findClosestMeteoriteChunk(final WorldServer world, final ChunkPos chunkPos) {
        final int cx = chunkPos.x;
        final int cz = chunkPos.z;

        // Am I standing on it?
        if (CompassRegion.get(world, chunkPos).hasCompassTarget(cx, cz)) {
            return chunkPos;
        }

        // spiral outward...
        for (int offset = 1; offset < MAX_RANGE; offset++) {
            final int minX = cx - offset;
            final int minZ = cz - offset;
            final int maxX = cx + offset;
            final int maxZ = cz + offset;

            int closest = Integer.MAX_VALUE;
            int chosenX = cx;
            int chosenZ = cz;

            for (int z = minZ; z <= maxZ; z++) {
                if (CompassRegion.get(world, minX, z).hasCompassTarget(minX, z)) {
                    final int closeness = dist(cx, cz, minX, z);
                    if (closeness < closest) {
                        closest = closeness;
                        chosenX = minX;
                        chosenZ = z;
                    }
                }

                if (CompassRegion.get(world, maxX, z).hasCompassTarget(maxX, z)) {
                    final int closeness = dist(cx, cz, maxX, z);
                    if (closeness < closest) {
                        closest = closeness;
                        chosenX = maxX;
                        chosenZ = z;
                    }
                }
            }

            for (int x = minX + 1; x < maxX; x++) {
                if (CompassRegion.get(world, x, minZ).hasCompassTarget(x, minZ)) {
                    final int closeness = dist(cx, cz, x, minZ);
                    if (closeness < closest) {
                        closest = closeness;
                        chosenX = x;
                        chosenZ = minZ;
                    }
                }

                if (CompassRegion.get(world, x, maxZ).hasCompassTarget(x, maxZ)) {
                    final int closeness = dist(cx, cz, x, maxZ);
                    if (closeness < closest) {
                        closest = closeness;
                        chosenX = x;
                        chosenZ = maxZ;
                    }
                }
            }

            if (closest < Integer.MAX_VALUE) {
                return new ChunkPos(chosenX, chosenZ);
            }
        }

        return null;
    }

    private static int dist(final int ax, final int az, final int bx, final int bz) {
        final int up = (bz - az) * CHUNK_SIZE;
        final int side = (bx - ax) * CHUNK_SIZE;

        return up * up + side * side;
    }

    /**
     * A compass target was placed or taken away at this position.
     */
    public static void notifyBlockChange(final WorldServer world, final BlockPos pos) {
        if (updateArea(world, world.getChunk(pos).getPos())) {
            forgetAnswers(world);
        }
    }

    /**
     * Every answer given out is suspect once a target appears or goes: an answer names the nearest one,
     * so it may well have named this target from a chunk away rather than from the chunk it changed in.
     * <p>
     * A client keeps its own answer for half a minute, so it has to be told as well - left to lapse, a
     * compass goes on pointing at a chest the player has just broken.
     */
    public static void forgetAnswers(final WorldServer world) {
        CLOSEST_METEORITE_CACHE.invalidateAll();
        NetworkHandler.instance().sendToDimension(new PacketClearCompassCache(),
                world.provider.getDimension());
    }

    public static boolean updateArea(final WorldServer world, final ChunkPos chunkPos) {
        return updateArea(world, chunkPos, true);
    }

    /**
     * Scans one chunk and records whether it holds a compass target.
     *
     * @param checkNatural false while converting a save older than the natural property, where every sky
     *                     stone chest the old compass knew about counts
     * @return true if what this chunk answers changed
     */
    public static boolean updateArea(final WorldServer world, final ChunkPos chunkPos, final boolean checkNatural) {
        final CompassRegion compassRegion = CompassRegion.get(world, chunkPos);
        final Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);

        boolean foundTarget = false;
        for (final ExtendedBlockStorage section : chunk.getBlockStorageArray()) {
            if (scanArea(section, checkNatural)) {
                foundTarget = true;
                break;
            }
        }

        return compassRegion.setHasCompassTarget(chunkPos.x, chunkPos.z, foundTarget);
    }

    /**
     * Read straight off the section rather than through {@link Chunk#getBlockState}, so the sections that hold
     * nothing at all - most of a column - are skipped whole instead of a block at a time.
     */
    private static boolean scanArea(@Nullable final ExtendedBlockStorage section, final boolean checkNatural) {
        if (section == Chunk.NULL_BLOCK_STORAGE || section.isEmpty()) {
            return false;
        }

        final Optional<Block> maybeBlock = AEApi.instance().definitions().blocks().skyStoneChest().maybeBlock();
        if (!maybeBlock.isPresent()) {
            return false;
        }

        final Block skyStoneChest = maybeBlock.get();

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    final IBlockState state = section.get(x, y, z);

                    if (state.getBlock() == skyStoneChest && (!checkNatural || isNatural(state))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isNatural(final IBlockState state) {
        return state.getPropertyKeys().contains(BlockSkyChest.NATURAL) && state.getValue(BlockSkyChest.NATURAL);
    }
}
