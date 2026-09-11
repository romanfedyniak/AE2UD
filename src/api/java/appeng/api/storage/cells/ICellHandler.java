/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 * Copyright (c) 2026 AE2UD contributors
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

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.api.stacks.AEItemKey;

/**
 * Turns an item into a {@link StorageCell}. Register with
 * {@link appeng.api.storage.StorageCells#addCellHandler(ICellHandler)}.
 */
public interface ICellHandler {

    /**
     * @return true if this handler can turn the given item into a cell.
     */
    boolean isCell(ItemStack is);

    /**
     * {@link #isCell(ItemStack)} for an item key. Every item put into a cell is asked this, to keep a full cell
     * out of another one, so override it when the item alone answers: the default builds a stack to ask with.
     */
    default boolean isCell(AEItemKey key) {
        return this.isCell(key.getReadOnlyStack());
    }

    /**
     * @param host may be null when the cell is inspected outside of a machine, for a tooltip.
     * @return null if {@link #isCell(ItemStack)} is false for this stack.
     */
    @Nullable
    StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host);
}
