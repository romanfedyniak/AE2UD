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

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

/**
 * Knows how to put content of one {@link AEKeyType} into the world: placing a block, an entity, or a
 * fluid source. Used by the formation plane.
 */
public interface PlacementStrategy {

    static PlacementStrategy noop() {
        return NoopPlacementStrategy.INSTANCE;
    }

    /**
     * Forgets which adjacent positions were found to be blocked, so they are retried.
     */
    void clearBlocked();

    /**
     * @return how much was (or would have been) placed.
     */
    long placeInWorld(AEKey what, long amount, Actionable type, boolean placeAsEntity);

    interface Factory {
        PlacementStrategy create(World world, BlockPos fromPos, EnumFacing fromSide, TileEntity host,
                @Nullable UUID owningPlayerId);
    }

    static void register(AEKeyType type, Factory factory) {
        StackWorldBehaviors.registerPlacementStrategy(type, factory);
    }
}
