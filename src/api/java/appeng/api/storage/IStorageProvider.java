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

import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageService;

/**
 * Something that contributes storage to the network: a drive, a chest, a storage bus.
 * <p>
 * Replaces {@code ICellProvider} and {@code ICellContainer}. Where those returned a list of handlers
 * per storage channel, this one mounts any number of {@link MEStorage} instances, each of which may
 * itself serve several key types.
 */
public interface IStorageProvider {

    /**
     * Called when the network (re)builds its storage. Mount every inventory this provider offers.
     */
    void mountInventories(IStorageMounts storageMounts);

    /**
     * Asks the network to call {@link #mountInventories(IStorageMounts)} again, after the provider's
     * contents changed in a way that adds or removes inventories.
     */
    static void requestUpdate(IGridNode node) {
        if (node == null || node.getGrid() == null) {
            return;
        }
        IStorageService storage = node.getGrid().getCache(IStorageService.class);
        if (storage != null) {
            storage.refreshNodeStorageProvider(node);
        }
    }
}
