/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.debug;


import appeng.block.AEBaseTileBlock;
import appeng.debug.craftingtest.TestRunner;
import appeng.util.Platform;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nullable;


public class BlockCraftingTestRig extends AEBaseTileBlock {

    public BlockCraftingTestRig() {
        super(Material.IRON);
    }

    /**
     * Right-clicking says whether a run is going and how far it has got. Starting one is the command's job:
     * a run needs to be told which scenarios and whether it is recording a baseline, and a block has nowhere
     * to say that.
     */
    @Override
    public boolean onActivated(final World w, final BlockPos pos, final EntityPlayer player,
            final EnumHand hand, final @Nullable ItemStack heldItem, final EnumFacing side, final float hitX,
            final float hitY, final float hitZ) {
        if (Platform.isClient()) {
            return true;
        }

        final TileCraftingTestRig rig = this.getTileEntity(w, pos);
        final TestRunner runner = rig == null ? null : rig.getRunner();

        player.sendMessage(new TextComponentString(runner == null
                ? "Crafting test rig idle. Run /ae2 craftingtest to use it."
                : runner.describeProgress()));
        return true;
    }
}
