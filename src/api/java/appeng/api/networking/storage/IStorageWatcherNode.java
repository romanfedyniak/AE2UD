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

import appeng.api.stacks.AEKey;

/**
 * Implemented by machines that want to be told when the amount of a key in the network changes.
 * Replaces {@code IStackWatcherHost}.
 */
public interface IStorageWatcherNode {

    /**
     * Called when the network hands out (or replaces) this node's watcher. Re-register the keys of
     * interest here.
     */
    void updateWatcher(IStackWatcher newWatcher);

    /**
     * @param amount the new total amount in the network.
     */
    void onStackChange(AEKey what, long amount);

    /**
     * How much of one key moved in the last tick, in each direction, for a node that asked to be metered
     * through {@link IStorageService#meter}. Never called with both amounts zero, and never called at all
     * for a node that did not ask.
     * <p>
     * This is not the same question as {@link #onStackChange}: a key that arrives and leaves again inside
     * one tick does not change the total by anything, and the total is all a watcher is told about. What
     * passes through a network is only visible here.
     *
     * @param inserted  how much appeared, never negative.
     * @param extracted how much went away, never negative.
     */
    default void onStackFlow(AEKey what, long inserted, long extracted) {
    }
}
