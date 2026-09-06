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


import appeng.core.AELog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Objects;


/**
 * Which chunks of a region hold something a meteorite compass can point at, one bit each.
 * <p>
 * Kept as the world's own saved data rather than a file AE2 opens itself, so it is written, backed up and
 * copied along with the rest of the save.
 */
public class CompassRegion extends WorldSavedData {

    /** Chunks per region on each axis. */
    private static final int CHUNKS_PER_REGION = 1024;

    private static final int BITS_PER_POS = Integer.numberOfTrailingZeros(CHUNKS_PER_REGION);
    private static final int BITMAP_LENGTH = CHUNKS_PER_REGION * CHUNKS_PER_REGION;
    private static final String NBT_KEY = "data";

    private BitSet data = new BitSet(BITMAP_LENGTH);

    public CompassRegion(final String name) {
        super(name);
    }

    private static String getRegionSaveName(final int regionX, final int regionZ) {
        return "ae2_compass_" + regionX + "_" + regionZ;
    }

    public static CompassRegion get(final WorldServer world, final ChunkPos chunkPos) {
        Objects.requireNonNull(chunkPos, "chunkPos");

        return get(world, chunkPos.x, chunkPos.z);
    }

    public static CompassRegion get(final WorldServer world, final int chunkX, final int chunkZ) {
        Objects.requireNonNull(world, "world");

        final int regionX = (chunkX >> BITS_PER_POS) << BITS_PER_POS;
        final int regionZ = (chunkZ >> BITS_PER_POS) << BITS_PER_POS;

        return getByRegion(world, regionX, regionZ);
    }

    private static CompassRegion getByRegion(final WorldServer world, final int regionX, final int regionZ) {
        final String name = getRegionSaveName(regionX, regionZ);

        final MapStorage storage = world.getPerWorldStorage();
        CompassRegion region = (CompassRegion) storage.getOrLoadData(CompassRegion.class, name);

        if (region == null) {
            region = new CompassRegion(name);
            storage.setData(name, region);
        }

        return region;
    }

    @Override
    public void readFromNBT(@NotNull final NBTTagCompound nbt) {
        for (final String key : nbt.getKeySet()) {
            if (key.equals(NBT_KEY)) {
                this.data = BitSet.valueOf(nbt.getByteArray(key));
            } else {
                AELog.warn("Compass region contains unknown NBT tag %s", key);
            }
        }
    }

    @Override
    @NotNull
    public NBTTagCompound writeToNBT(@NotNull final NBTTagCompound compound) {
        compound.setByteArray(NBT_KEY, this.data.toByteArray());
        return compound;
    }

    public boolean hasCompassTarget(final int cx, final int cz) {
        return this.data.get(getBitmapIndex(cx, cz));
    }

    /** @return true if this chunk's answer changed */
    public boolean setHasCompassTarget(final int cx, final int cz, final boolean hasTarget) {
        final int index = getBitmapIndex(cx, cz);

        if (this.data.get(index) == hasTarget) {
            return false;
        }

        this.data.set(index, hasTarget);
        this.markDirty();
        return true;
    }

    private static int getBitmapIndex(int cx, int cz) {
        cx &= CHUNKS_PER_REGION - 1;
        cz &= CHUNKS_PER_REGION - 1;
        return cx | (cz << BITS_PER_POS);
    }
}
