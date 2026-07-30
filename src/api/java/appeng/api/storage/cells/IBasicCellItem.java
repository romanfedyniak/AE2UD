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

package appeng.api.storage.cells;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

/**
 * A standard storage cell item: bytes, types, and one {@link AEKeyType} it stores.
 * <p>
 * Replaces {@code IStorageCell<T>}. {@link #getKeyType()} is what makes "a cell for gas" possible
 * without touching the core: an addon registers its key type, ships an item returning that type
 * here, and drives, chests, the workbench and the terminal keep working unchanged.
 */
public interface IBasicCellItem extends ICellWorkbenchItem {

    /**
     * The kind of content this cell stores.
     */
    @Nonnull
    AEKeyType getKeyType();

    int getBytes(@Nonnull ItemStack cellItem);

    int getBytesPerType(@Nonnull ItemStack cellItem);

    int getTotalTypes(@Nonnull ItemStack cellItem);

    double getIdleDrain();

    default boolean isBlackListed(@Nonnull ItemStack cellItem, @Nonnull AEKey requestedAddition) {
        return false;
    }

    /**
     * Whether this cell may be stored inside another storage cell.
     */
    default boolean storableInStorageCell() {
        return false;
    }

    default boolean isStorageCell(@Nonnull ItemStack i) {
        return true;
    }
}
