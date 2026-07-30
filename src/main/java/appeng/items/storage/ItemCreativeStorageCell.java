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

package appeng.items.storage;


import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.storage.cells.StorageCell;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellConfig;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import java.util.List;


/**
 * The creative storage cell. It is an {@link ICellWorkbenchItem} and deliberately <strong>not</strong> an
 * {@link appeng.api.storage.cells.IBasicCellItem}, matching upstream's {@code CreativeCellItem} and the
 * pre-port class.
 * <p/>
 * <strong>Do not widen it to {@code IBasicCellItem} again.</strong> That was tried, to let
 * {@code TileChest}/{@code TileIOPort} read {@code getKeyType()} off the item, and it crashed the client on
 * startup. {@code StorageCells.getCellInventory} returns the first handler whose {@code isCell} accepts the
 * stack, {@code BasicCellHandler} is registered before {@code CreativeCellHandler}, and
 * {@code BasicCellInventory.isCell} is exactly "is an {@code IBasicCellItem}" - so widening the interface
 * silently moved the creative cell from {@link appeng.me.storage.CreativeCellInventory} to
 * {@code BasicCellInventory}, which then dereferenced this class's null upgrades inventory.
 * <p/>
 * Nothing was gained by it either: both call sites already fall back to {@link AEKeyType#items()} for an
 * item that declares no key type, and items is the right answer for this cell. The unreachable
 * {@code getBytes}/{@code getBytesPerType}/{@code getTotalTypes}/{@code getIdleDrain} overrides that existed
 * only to satisfy the wider interface are gone with it; {@code CreativeCellInventory} never did byte or type
 * accounting.
 */
public class ItemCreativeStorageCell extends AEBaseItem implements ICellWorkbenchItem {

    public ItemCreativeStorageCell() {
        this.setMaxStackSize(1);
    }

    @Override
    public boolean isEditable(final ItemStack is) {
        return true;
    }

    @Override
    public IItemHandler getUpgradesInventory(final ItemStack is) {
        return null;
    }

    @Override
    public IItemHandler getConfigInventory(final ItemStack is) {
        return new CellConfig(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(final ItemStack is) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(final ItemStack is, final FuzzyMode fzMode) {

    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines, final ITooltipFlag advancedTooltips) {
        final StorageCell inventory = StorageCells.getCellInventory(stack, null);

        if (inventory != null) {
            final CellConfig cc = new CellConfig(stack);

            for (final ItemStack is : cc) {
                if (!is.isEmpty()) {
                    lines.add(is.getDisplayName());
                }
            }
        }
    }
}
