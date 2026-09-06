/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 * Copyright (c) 2026 AE2UD contributors
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

package appeng.worldgen.meteorite;


import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.BlockFlags;


/**
 * Sets the blocks a meteorite is made of, leaving alone anything a meteorite has no business breaking.
 * <p>
 * Whether neighbours are told depends on which chunk is being built: a chunk being generated has neighbours
 * that may not exist yet, and waking them is how a generator ends up generating the whole world.
 */
public class MeteoriteBlockPutter {

    private boolean update;

    public MeteoriteBlockPutter(final boolean update) {
        this.update = update;
    }

    /** For a block that must not wake its neighbours whatever this putter was built for. */
    public void putSilent(final World w, final BlockPos pos, final Block blk, final IBlockState originalState) {
        final boolean wasUpdating = this.update;

        this.update = false;
        this.put(w, pos, blk, originalState);
        this.update = wasUpdating;
    }

    public void put(final World w, final BlockPos pos, final Block blk, final IBlockState originalState) {
        if (originalState.getBlock() == blk) {
            return;
        }

        this.put(w, pos, blk.getDefaultState(), originalState);
    }

    public void put(final World w, final BlockPos pos, final Block blk) {
        this.put(w, pos, blk, w.getBlockState(pos));
    }

    public void put(final World w, final BlockPos pos, final IBlockState state, final IBlockState originalState) {
        if (state == originalState || isUnbreakable(w, pos, originalState)) {
            return;
        }

        int flags = BlockFlags.DEFAULT | BlockFlags.NO_OBSERVERS;
        if (!this.update) {
            flags &= ~BlockFlags.NOTIFY_NEIGHBORS;
        }

        w.setBlockState(pos, state, flags);
    }

    public void put(final World w, final BlockPos pos, final IBlockState state) {
        this.put(w, pos, state, w.getBlockState(pos));
    }

    private boolean isUnbreakable(final World w, final BlockPos pos, final IBlockState state) {
        return state.getBlock() == Blocks.BEDROCK || state.getBlockHardness(w, pos) < 0.0F;
    }
}
