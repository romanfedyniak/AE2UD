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

package appeng.api.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.StorageCell;

/**
 * Opens the right GUI for a cell placed in an ME chest.
 */
public interface ICellGuiHandler {

    /**
     * @return true if this handler knows how to display cells of the given key type.
     */
    boolean isHandlerFor(AEKeyType keyType);

    /**
     * Lets a handler claim one specific cell item ahead of the generic handler for its key type.
     * <p>
     * An addon shipping a cell with its own screen overrides this; the registry prefers a
     * specialized handler over a generic one for the same key type.
     */
    default boolean isSpecializedFor(ItemStack is) {
        return false;
    }

    /**
     * Opens the chest GUI for the given cell.
     */
    void openChestGui(EntityPlayer player, IChestOrDrive chest, ICellHandler cellHandler, StorageCell inv, ItemStack is,
            AEKeyType keyType);
}
