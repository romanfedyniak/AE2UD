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

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;

/**
 * Knows how to expose an adjacent block's inventory of one {@link AEKeyType} as {@link MEStorage}.
 * This is what a storage bus uses; it is the reason a single bus class can serve items, fluids and
 * anything an addon registers.
 * <p>
 * The built-in item and fluid strategies are registered through exactly this API, with no privileges
 * over an addon's.
 */
public interface ExternalStorageStrategy {

    /**
     * @param extractableOnly           only expose content that can actually be extracted.
     * @param injectOrExtractCallback   invoked when this wrapper changes the adjacent inventory, so
     *                                  the bus can refresh its view.
     * @return null if the adjacent block does not expose anything for this type.
     */
    MEStorage createWrapper(boolean extractableOnly, Runnable injectOrExtractCallback);

    @FunctionalInterface
    interface Factory {
        ExternalStorageStrategy create(World world, BlockPos fromPos, EnumFacing fromSide);
    }

    static void register(AEKeyType type, Factory factory) {
        StackWorldBehaviors.registerExternalStorageStrategy(type, factory);
    }
}
