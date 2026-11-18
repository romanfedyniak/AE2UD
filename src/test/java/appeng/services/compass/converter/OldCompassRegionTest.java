/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.services.compass.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.util.math.ChunkPos;

class OldCompassRegionTest {

    @TempDir
    Path folder;

    private File write(final String name, final int length, final int... beacons) throws IOException {
        final File file = this.folder.resolve(name).toFile();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(length);
            for (final int index : beacons) {
                raf.seek(index);
                raf.write(1);
            }
        }
        return file;
    }

    @Test
    void aFullFileGivesItsBeacons() throws IOException {
        this.write("0_1024_-1024.dat", 1024 * 1024, 5 + 3 * 1024);
        final OldCompassRegion region = new OldCompassRegion(this.folder.toFile(), "0_1024_-1024.dat");
        assertEquals(Collections.singletonList(new ChunkPos(1029, -1021)), new ArrayList<>(region.getBeacons()));
    }

    /** Cut short by a crash or a full disk while the old code was creating it. */
    @Test
    void aShortFileGivesWhatItHolds() throws IOException {
        this.write("0_0_0.dat", 100, 7);
        final List<ChunkPos> beacons = new ArrayList<>(new OldCompassRegion(this.folder.toFile(), "0_0_0.dat").getBeacons());
        assertEquals(Collections.singletonList(new ChunkPos(7, 0)), beacons);
    }

    @Test
    void anEmptyFileGivesNothing() throws IOException {
        this.write("0_0_1024.dat", 0);
        assertEquals(0, new OldCompassRegion(this.folder.toFile(), "0_0_1024.dat").getBeacons().size());
    }
}
