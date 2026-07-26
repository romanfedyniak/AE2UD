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

import appeng.api.storage.MEStorage;

/**
 * The contents of a storage cell. Replaces {@code ICellInventory} and
 * {@code ICellInventoryHandler}, both of which were generic over a single storage channel; this one
 * is an {@link MEStorage} and therefore not tied to a type.
 */
public interface StorageCell extends MEStorage {

    CellState getStatus();

    /**
     * Power drawn per tick while this cell sits in a drive.
     */
    double getIdleDrain();

    /**
     * Whether this cell may be stored inside another storage cell.
     */
    default boolean canFitInsideCell() {
        return true;
    }

    /**
     * Writes pending changes back to the cell's item stack.
     */
    void persist();
}
