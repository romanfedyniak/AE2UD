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

package appeng.api.networking.storage;

import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;

/**
 * The network's storage. Replaces {@code IStorageGrid}.
 * <p>
 * Note there is no longer one of these per storage channel: a single {@link MEStorage} covers every
 * registered key type.
 */
public interface IStorageService extends IGridCache {

    /**
     * The network inventory, through which everything should be inserted and extracted.
     */
    MEStorage getInventory();

    /**
     * A cached snapshot of the network contents, refreshed as changes come in. Do not mutate.
     */
    KeyCounter getCachedInventory();

    /**
     * Adds a provider that is not attached to a grid node, such as a wireless terminal's cell.
     */
    void addGlobalStorageProvider(IStorageProvider provider);

    void removeGlobalStorageProvider(IStorageProvider provider);

    /**
     * Re-mounts the inventories of the provider attached to this node.
     */
    void refreshNodeStorageProvider(IGridNode node);

    void refreshGlobalStorageProvider(IStorageProvider provider);

    void invalidateCache();
}
