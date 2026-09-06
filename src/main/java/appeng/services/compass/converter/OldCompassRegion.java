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

package appeng.services.compass.converter;


import appeng.core.worlddata.converter.IOldFileRegion;
import appeng.core.worlddata.converter.OldDataReader;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.math.ChunkPos;
import org.apache.commons.lang3.math.NumberUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;


/**
 * One of the mapped files the compass used to keep, read only to be converted away.
 * <p>
 * A byte per chunk, non-zero where the compass had found something, at an index the old writer packed as
 * {@code (cx & 0x3FF) + (cz & 0x3FF) * 0x400}.
 */
public final class OldCompassRegion implements IOldFileRegion {

    private static final int REGION_CHUNKS = 0x400;
    private static final int CHUNK_MASK = REGION_CHUNKS - 1;
    private static final int SIGN_BIT = REGION_CHUNKS >> 1;

    private final File worldCompassFolder;
    private final int regionX;
    private final int regionZ;

    private ByteBuffer buffer;

    public OldCompassRegion(@Nonnull final File worldCompassFolder, @Nonnull final String fileName) {
        Preconditions.checkNotNull(worldCompassFolder);
        Preconditions.checkArgument(worldCompassFolder.isDirectory());

        this.worldCompassFolder = worldCompassFolder;

        final Matcher matcher = OldDataReader.REGION_NAME_FORMAT.matcher(fileName);
        // The reader matched this name already.
        Preconditions.checkArgument(matcher.matches());
        this.regionX = NumberUtils.toInt(matcher.group(2));
        this.regionZ = NumberUtils.toInt(matcher.group(3));

        this.openFile(fileName);
    }

    public Collection<ChunkPos> getBeacons() {
        if (this.buffer == null) {
            return Collections.emptyList();
        }

        final IntArrayList packed = this.getBeaconIndices();
        final ArrayList<ChunkPos> positions = new ArrayList<>(packed.size());

        for (int i = 0; i < packed.size(); i++) {
            positions.add(this.unpack(packed.getInt(i)));
        }

        return positions;
    }

    private IntArrayList getBeaconIndices() {
        final IntArrayList indices = new IntArrayList();

        for (int i = 0; i < this.buffer.limit(); i++) {
            if (this.buffer.get(i) != 0) {
                indices.add(i);
            }
        }

        return indices;
    }

    private ChunkPos unpack(final int packed) {
        return new ChunkPos(toChunk(packed, this.regionX), toChunk(packed >> 10, this.regionZ));
    }

    /** Ten bits of a chunk coordinate, sign extended, put back beside its region. */
    private static int toChunk(final int packed, final int regionCoord) {
        final int masked = packed & CHUNK_MASK;
        final int signed = (masked ^ SIGN_BIT) - SIGN_BIT;
        return regionCoord | (signed & CHUNK_MASK);
    }

    @Override
    public void openFile(final String fileName) {
        final File file = new File(this.worldCompassFolder, fileName);

        if (this.isFileExistent(file)) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                final FileChannel fc = raf.getChannel();
                this.buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, (long) REGION_CHUNKS * REGION_CHUNKS);
            } catch (final Throwable t) {
                throw new CompassException(t);
            }
        }
    }
}
