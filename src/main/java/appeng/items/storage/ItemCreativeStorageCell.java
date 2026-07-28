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
import appeng.api.storage.cells.IBasicCellItem;
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
 * The creative storage cell: reports as an {@link IBasicCellItem} of {@link AEKeyType#items()} - it
 * has always behaved as an item-only cell - so {@code TileChest}/{@code TileDrive}/{@code TileIOPort}
 * read its key type correctly instead of falling back to the {@code items()} default for every
 * non-{@link IBasicCellItem} item (see CONTRACT.md §9, wave 2 note "A cell's key type").
 * <p/>
 * {@link #getBytes}/{@link #getBytesPerType}/{@link #getTotalTypes}/{@link #getIdleDrain} are declared
 * to satisfy the interface but are not consulted by {@link appeng.me.storage.CreativeCellInventory},
 * which never limits itself by byte/type accounting - they are set to values that read naturally as
 * "unlimited" should any future caller (e.g. the cell workbench GUI) query them.
 */
public class ItemCreativeStorageCell extends AEBaseItem implements IBasicCellItem {

    public ItemCreativeStorageCell() {
        this.setMaxStackSize(1);
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    @Override
    public int getBytes(final ItemStack cellItem) {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getBytesPerType(final ItemStack cellItem) {
        return 8;
    }

    @Override
    public int getTotalTypes(final ItemStack cellItem) {
        return Integer.MAX_VALUE;
    }

    @Override
    public double getIdleDrain() {
        return 0;
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
