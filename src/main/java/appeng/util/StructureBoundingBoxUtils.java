/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.util;


import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.Collection;


public final class StructureBoundingBoxUtils {

    private StructureBoundingBoxUtils() {
    }

    /** Holds a loop inside the part of a structure this pass is allowed to write. */
    @Desugar
    public record BoundingBoxClamper(StructureBoundingBox boundingBox) {

        public int minX(final int x) {
            return clamp(x, this.boundingBox.minX, this.boundingBox.maxX);
        }

        public int maxX(final int x) {
            return clamp(x, this.boundingBox.minX, this.boundingBox.maxX);
        }

        public int minZ(final int z) {
            return clamp(z, this.boundingBox.minZ, this.boundingBox.maxZ);
        }

        public int maxZ(final int z) {
            return clamp(z, this.boundingBox.minZ, this.boundingBox.maxZ);
        }

        private static int clamp(final int value, final int min, final int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    /** The overlap, or a copy of the first box where there is none - never the box that was passed in. */
    public static StructureBoundingBox intersection(final StructureBoundingBox boundingBox,
            final StructureBoundingBox other) {
        final int minX = Math.max(boundingBox.minX, other.minX);
        final int minY = Math.max(boundingBox.minY, other.minY);
        final int minZ = Math.max(boundingBox.minZ, other.minZ);
        final int maxX = Math.min(boundingBox.maxX, other.maxX);
        final int maxY = Math.min(boundingBox.maxY, other.maxY);
        final int maxZ = Math.min(boundingBox.maxZ, other.maxZ);

        if (minX <= maxX && minY <= maxY && minZ <= maxZ) {
            return new StructureBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        // A copy, because callers offset what they get back.
        return new StructureBoundingBox(boundingBox);
    }

    public static StructureBoundingBox expandToChunkBounds(final StructureBoundingBox boundingBox) {
        final int minChunkX = boundingBox.minX >> 4;
        final int maxChunkX = boundingBox.maxX >> 4;
        final int minChunkZ = boundingBox.minZ >> 4;
        final int maxChunkZ = boundingBox.maxZ >> 4;

        return new StructureBoundingBox(
                minChunkX << 4, boundingBox.minY, minChunkZ << 4,
                (maxChunkX << 4) + 15, boundingBox.maxY, (maxChunkZ << 4) + 15);
    }

    public static Collection<ChunkPos> getChunksWithin(final StructureBoundingBox boundingBox) {
        final ArrayList<ChunkPos> positions = new ArrayList<>();

        for (int chunkX = boundingBox.minX >> 4; chunkX <= boundingBox.maxX >> 4; chunkX++) {
            for (int chunkZ = boundingBox.minZ >> 4; chunkZ <= boundingBox.maxZ >> 4; chunkZ++) {
                positions.add(new ChunkPos(chunkX, chunkZ));
            }
        }

        return positions;
    }

    /** A flat square around a position, used to ask the terrain about the ground near a meteorite. */
    public static StructureBoundingBox createCenteredBoundingBox(final BlockPos pos, final int r) {
        return new StructureBoundingBox(
                pos.getX() - r, pos.getY(), pos.getZ() - r,
                pos.getX() + r, pos.getY(), pos.getZ() + r);
    }
}
