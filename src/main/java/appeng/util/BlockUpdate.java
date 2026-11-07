/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.util;


import appeng.hooks.TickHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;


public class BlockUpdate implements IWorldCallable<Boolean> {
    /**
     * The blocks of each world with an update still in the queue. A grid splitting on a chunk unload moves every
     * P2P tunnel of a frequency, and each move tells the whole frequency again: a big base queued thousands of
     * updates for the same few blocks, and the queue ran at 50 ms a tick for minutes. One is enough, since the
     * neighbours look at the world when it runs.
     */
    private static final Map<World, Set<BlockPos>> QUEUED = new WeakHashMap<>();

    private final BlockPos pos;
    private final Set<BlockPos> queued;

    private BlockUpdate(final BlockPos pos, final Set<BlockPos> queued) {
        this.pos = pos;
        this.queued = queued;
    }

    static void queue(final World world, final BlockPos pos) {
        final Set<BlockPos> queued;
        synchronized (QUEUED) {
            queued = QUEUED.computeIfAbsent(world, w -> ConcurrentHashMap.newKeySet());
        }
        final BlockPos at = pos.toImmutable();
        if (queued.add(at)) {
            TickHandler.INSTANCE.addCallable(world, new BlockUpdate(at, queued));
        }
    }

    @Override
    public Boolean call(final World world) throws Exception {
        this.queued.remove(this.pos);
        if (world.isBlockLoaded(this.pos)) {
            world.notifyNeighborsOfStateChange(this.pos, Platform.AIR_BLOCK, true);
        }

        return true;
    }
}
