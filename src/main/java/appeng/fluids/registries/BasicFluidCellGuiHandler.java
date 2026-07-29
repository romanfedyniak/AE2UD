/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.fluids.registries;


import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.ICellGuiHandler;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.StorageCell;
import appeng.api.util.AEPartLocation;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;


/**
 * Registers through {@link appeng.api.storage.StorageCells#addCellGuiHandler}, the api-level registry that
 * replaced the deleted {@code ICellRegistry} (see CONTRACT.md, "Amendments made to the frozen API").
 */
public class BasicFluidCellGuiHandler implements ICellGuiHandler {

    @Override
    public boolean isHandlerFor(final AEKeyType keyType) {
        return keyType == AEKeyType.fluids();
    }

    @Override
    public void openChestGui(final EntityPlayer player, final IChestOrDrive chest, final ICellHandler cellHandler,
            final StorageCell inv, final ItemStack is, final AEKeyType keyType) {
        Platform.openGUI(player, (TileEntity) chest, AEPartLocation.fromFacing(chest.getUp()), GuiBridge.GUI_FLUID_TERMINAL);
    }
}
