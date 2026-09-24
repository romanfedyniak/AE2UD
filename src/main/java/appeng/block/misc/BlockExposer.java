/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's storage exposer (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.block.misc;

import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.block.AEBaseTileBlock;
import appeng.helpers.exposer.DualityExposer;

public class BlockExposer extends AEBaseTileBlock {

    public BlockExposer() {
        super(Material.IRON);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(final ItemStack is, final World world, final List<String> lines,
            final ITooltipFlag advancedItemTooltips) {
        super.addInformation(is, world, lines, advancedItemTooltips);
        DualityExposer.addTooltip(lines);
    }
}
