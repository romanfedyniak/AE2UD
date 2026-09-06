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


import appeng.core.AELog;
import appeng.core.worlddata.converter.IOldFileRegion;
import com.google.common.base.Preconditions;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;


/**
 * One of the files the old generator kept its meteorites in, read only to be converted away.
 */
public class OldMeteoriteRegion implements IOldFileRegion {

    private final File worldSpawnFolder;

    private NBTTagCompound data;

    public OldMeteoriteRegion(@Nonnull final File worldSpawnFolder, @Nonnull final String fileName) {
        Preconditions.checkNotNull(worldSpawnFolder);
        Preconditions.checkArgument(worldSpawnFolder.isDirectory());

        this.worldSpawnFolder = worldSpawnFolder;
        this.openFile(fileName);
    }

    @NotNull
    public Collection<NBTTagCompound> getSettings() {
        final int size = this.data.getInteger("num");
        final Collection<NBTTagCompound> settings = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            settings.add(this.data.getCompoundTag(String.valueOf(i)));
        }

        return settings;
    }

    @Override
    public void openFile(final String fileName) {
        final File file = new File(this.worldSpawnFolder, fileName);

        if (!this.isFileExistent(file)) {
            this.data = new NBTTagCompound();
            return;
        }

        try (FileInputStream in = new FileInputStream(file)) {
            this.data = CompressedStreamTools.readCompressed(in);
        } catch (final Throwable t) {
            // A file that cannot be read costs one meteorite, not the world.
            this.data = new NBTTagCompound();
            AELog.debug(t);
        }
    }
}
