/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.util.AEPartLocation;

/**
 * An item whose screen, opened on a block, works on one part of it: the side says which.
 */
public interface ISidedGuiItem extends IGuiItem {

    IGuiItemObject getGuiObject(ItemStack stack, World world, BlockPos pos, AEPartLocation side);
}
