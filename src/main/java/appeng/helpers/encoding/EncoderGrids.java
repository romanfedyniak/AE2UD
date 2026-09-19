/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.encoding;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import appeng.api.patterns.PatternEncodingMode;
import appeng.api.patterns.PatternEncodingModes;
import appeng.api.patterns.PatternGrid;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.IAEAppEngInventory;

/**
 * The grids of every mode an addon registered, for one terminal. The two built-in modes keep the inventories
 * and the save keys they always had, so they are not in here.
 */
public final class EncoderGrids {

    private final Map<ResourceLocation, Map<String, AppEngInternalInventory>> grids = new LinkedHashMap<>();

    public EncoderGrids(final IAEAppEngInventory host) {
        for (final PatternEncodingMode mode : PatternEncodingModes.getAll()) {
            if (isBuiltIn(mode.getId())) {
                continue;
            }
            final Map<String, AppEngInternalInventory> forMode = new HashMap<>();
            for (final PatternGrid grid : mode.getGrids()) {
                forMode.put(grid.getName(), new AppEngInternalInventory(host, grid.getSize()));
            }
            this.grids.put(mode.getId(), forMode);
        }
    }

    public static boolean isBuiltIn(final ResourceLocation mode) {
        return PatternEncodingModes.CRAFTING.equals(mode) || PatternEncodingModes.PROCESSING.equals(mode);
    }

    @Nullable
    public AppEngInternalInventory get(final ResourceLocation mode, final String grid) {
        final Map<String, AppEngInternalInventory> forMode = this.grids.get(mode);
        return forMode == null ? null : forMode.get(grid);
    }

    /** Grids of a mode that is no longer registered are dropped: there is nothing left that could show them. */
    public void readFromNBT(final NBTTagCompound data, final String name) {
        final NBTTagCompound all = data.getCompoundTag(name);
        for (final Map.Entry<ResourceLocation, Map<String, AppEngInternalInventory>> mode : this.grids.entrySet()) {
            final NBTTagCompound forMode = all.getCompoundTag(mode.getKey().toString());
            for (final Map.Entry<String, AppEngInternalInventory> grid : mode.getValue().entrySet()) {
                grid.getValue().readFromNBT(forMode, grid.getKey());
            }
        }
    }

    public void writeToNBT(final NBTTagCompound data, final String name) {
        final NBTTagCompound all = new NBTTagCompound();
        for (final Map.Entry<ResourceLocation, Map<String, AppEngInternalInventory>> mode : this.grids.entrySet()) {
            final NBTTagCompound forMode = new NBTTagCompound();
            for (final Map.Entry<String, AppEngInternalInventory> grid : mode.getValue().entrySet()) {
                grid.getValue().writeToNBT(forMode, grid.getKey());
            }
            all.setTag(mode.getKey().toString(), forMode);
        }
        data.setTag(name, all);
    }
}
