/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.worlddata.converter;


import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;


/**
 * How far each converter has got in this save, so old data is read once and never again.
 */
public final class ConverterMetadata extends WorldSavedData {

    /** Raise this when a change to the conversion means old data has to be read again. */
    public static final int CURRENT_VERSION = 1;

    private static final String SAVE_NAME = "ae2_converter_metadata";

    private final Map<Converters, Integer> versions = new EnumMap<>(Converters.class);

    public ConverterMetadata(final String name) {
        super(name);
    }

    public static ConverterMetadata get(final WorldServer world) {
        Objects.requireNonNull(world, "world");

        final MapStorage storage = world.getPerWorldStorage();
        ConverterMetadata metadata = (ConverterMetadata) storage.getOrLoadData(ConverterMetadata.class, SAVE_NAME);

        if (metadata == null) {
            metadata = new ConverterMetadata(SAVE_NAME);
            storage.setData(SAVE_NAME, metadata);
        }

        return metadata;
    }

    @Override
    public void readFromNBT(@NotNull final NBTTagCompound nbt) {
        for (final Converters converter : Converters.values()) {
            this.versions.put(converter, nbt.getInteger(converter.getKey()));
        }
    }

    @Override
    @NotNull
    public NBTTagCompound writeToNBT(@NotNull final NBTTagCompound nbt) {
        for (final Converters converter : Converters.values()) {
            nbt.setInteger(converter.getKey(), this.versions.getOrDefault(converter, 0));
        }
        return nbt;
    }

    public boolean isUpToDate(final World world, final Converters type) {
        // A world that has never ticked has nothing old in it to convert.
        final boolean isNewWorld = world.getWorldInfo().getWorldTotalTime() == 0;
        return isNewWorld || this.versions.getOrDefault(type, 0) >= CURRENT_VERSION;
    }

    public int getVersion(final Converters type) {
        return this.versions.getOrDefault(type, 0);
    }

    public void setVersion(final int version, final Converters type) {
        final Integer current = this.versions.get(type);
        if (current != null && current == version) {
            return;
        }

        this.versions.put(type, version);
        this.markDirty();
    }
}
