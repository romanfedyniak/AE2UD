/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.core.api;


import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.common.base.Preconditions;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IStorageHelper;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingLink;
import appeng.util.Platform;


/**
 * Replaces the old channel-registry role ({@code registerStorageChannel}/{@code getStorageChannel}/
 * {@code storageChannels()}) with a thin adapter over {@link Platform}'s static implementations - key types are
 * now a Forge registry ({@link appeng.api.stacks.AEKeyTypes}), not something {@link IStorageHelper} hands out.
 */
public class ApiStorage implements IStorageHelper {

    @Override
    public ICraftingLink loadCraftingLink(final NBTTagCompound data, final ICraftingRequester req) {
        Preconditions.checkNotNull(data);
        Preconditions.checkNotNull(req);

        return new CraftingLink(data, req);
    }

    @Override
    public long poweredExtraction(@Nonnull final IEnergySource energy, @Nonnull final MEStorage inv, @Nonnull final AEKey request,
            final long amount, @Nonnull final IActionSource src, @Nonnull final Actionable mode) {
        Preconditions.checkNotNull(energy);
        Preconditions.checkNotNull(inv);
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(src);
        Preconditions.checkNotNull(mode);

        return Platform.poweredExtraction(energy, inv, request, amount, src, mode);
    }

    @Override
    public long poweredInsert(@Nonnull final IEnergySource energy, @Nonnull final MEStorage inv, @Nonnull final AEKey input,
            final long amount, @Nonnull final IActionSource src, @Nonnull final Actionable mode) {
        Preconditions.checkNotNull(energy);
        Preconditions.checkNotNull(inv);
        Preconditions.checkNotNull(input);
        Preconditions.checkNotNull(src);
        Preconditions.checkNotNull(mode);

        return Platform.poweredInsert(energy, inv, input, amount, src, mode);
    }

    @Override
    public void postChanges(@Nonnull final IStorageService gs, @Nonnull final ItemStack removedCell, @Nonnull final ItemStack addedCell,
            @Nonnull final IActionSource src) {
        Preconditions.checkNotNull(gs);
        Preconditions.checkNotNull(removedCell);
        Preconditions.checkNotNull(addedCell);
        Preconditions.checkNotNull(src);

        Platform.postChanges(gs, removedCell, addedCell, src);
    }

}
