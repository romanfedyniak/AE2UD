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

package appeng.api.behaviors;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.MEStorage;

/**
 * State shared by all strategies participating in one import or export bus tick: how many
 * operations are left in the budget, who is acting, and what the bus is configured to move.
 */
public interface StackTransferContext {

    /**
     * The network side of the transfer.
     */
    MEStorage getInternalStorage();

    IActionSource getActionSource();

    /**
     * The bus' configured filter. Strategies must not move anything this rejects.
     */
    AEKeyFilter getFilter();

    int getOperationsRemaining();

    void setOperationsRemaining(int operationsRemaining);

    void reduceOperationsRemaining(long amount);

    boolean hasOperationsLeft();

    /**
     * Whether a full (non-partial) operation can still be performed this tick.
     */
    boolean hasRegularOperationsLeft();
}
