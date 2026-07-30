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

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/**
 * Anything an ME network can store content in: a storage cell, an adjacent inventory exposed by a
 * storage bus, the network itself.
 * <p>
 * Replaces {@code IMEInventory}, {@code IMEInventoryHandler}, {@code IMEMonitor} and
 * {@code IStorageMonitorable}. Unlike all of those, it is <strong>not</strong> generic: a single
 * instance handles keys of any registered type, which is what lets one storage bus serve items,
 * fluids and anything an addon registers at the same time.
 */
public interface MEStorage {

    /**
     * Whether this storage should be preferred over others when inserting the given key, regardless
     * of priority. Used by storage cells that already contain the key.
     */
    default boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return false;
    }

    /**
     * Inserts content.
     *
     * @return how much was (or would have been) inserted.
     */
    default long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return 0;
    }

    /**
     * Extracts content.
     *
     * @return how much was (or would have been) extracted.
     */
    default long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return 0;
    }

    /**
     * Adds everything currently stored here to the given counter.
     */
    default void getAvailableStacks(KeyCounter out) {
    }

    default KeyCounter getAvailableStacks() {
        KeyCounter counter = new KeyCounter();
        this.getAvailableStacks(counter);
        return counter;
    }

    /**
     * Human-readable name of this storage, shown in the storage bus tooltip and the ME chest.
     */
    default ITextComponent getDescription() {
        return new TextComponentString(this.getClass().getSimpleName());
    }
}
