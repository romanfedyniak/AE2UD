/*
 * The MIT License (MIT)
 *
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

package appeng.api.behaviors;

import javax.annotation.Nullable;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

/**
 * What a storage exposer shows of one key type: the keys the network holds, in an order that stays put between
 * calls, and a way to take them out. It is handed to an {@link ExposerStrategy.Factory}, which builds a
 * capability on top of it.
 * <p>
 * Everything here is empty while the exposer has no channel or no power. Amounts are the network's cached ones,
 * brought up to date once a tick and after every extraction made through this view.
 */
public interface ExposedStorage {

    int size();

    /**
     * @return null past the end.
     */
    @Nullable
    AEKey getKey(int index);

    long getAmount(AEKey key);

    /**
     * Takes from the network and pays for it in power. The key moves to the front of the order, so that a pipe
     * that walks the slots from the first finds it again at once.
     *
     * @return how much was, or would have been, taken.
     */
    long extract(AEKey key, long amount, Actionable mode);

    /**
     * Changes whenever a key or an amount does, for a handler that caches what it builds from them.
     */
    int getVersion();
}
