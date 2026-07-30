/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.storage;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;

/**
 * Assorted storage helpers.
 * <p>
 * Note this no longer registers storage channels: key types are Forge registry entries now, see
 * {@link appeng.api.stacks.AEKeyTypes}.
 */
public interface IStorageHelper {

    /**
     * Loads a crafting link from NBT.
     */
    ICraftingLink loadCraftingLink(NBTTagCompound data, ICraftingRequester req);

    /**
     * Extracts, charging the network for the work.
     *
     * @return how much was extracted.
     */
    long poweredExtraction(@Nonnull IEnergySource energy, @Nonnull MEStorage inv, @Nonnull AEKey request, long amount,
            @Nonnull IActionSource src, @Nonnull Actionable mode);

    /**
     * Inserts, charging the network for the work.
     *
     * @return how much was inserted.
     */
    long poweredInsert(@Nonnull IEnergySource energy, @Nonnull MEStorage inv, @Nonnull AEKey input, long amount,
            @Nonnull IActionSource src, @Nonnull Actionable mode);

    /**
     * Posts the difference between a removed and an added cell to the network's listeners.
     */
    void postChanges(@Nonnull IStorageService gs, @Nonnull ItemStack removedCell, @Nonnull ItemStack addedCell,
            @Nonnull IActionSource src);
}
