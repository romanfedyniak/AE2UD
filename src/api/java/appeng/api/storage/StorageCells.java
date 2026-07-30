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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;

/**
 * Registry of {@link ICellHandler} and {@link ICellGuiHandler}. Replaces {@code ICellRegistry}.
 */
public final class StorageCells {

    private static final List<ICellHandler> handlers = new ArrayList<>();

    private static final List<ICellGuiHandler> guiHandlers = new ArrayList<>();

    private StorageCells() {
    }

    public static synchronized void addCellHandler(ICellHandler handler) {
        Objects.requireNonNull(handler, "handler");
        if (handlers.contains(handler)) {
            throw new IllegalArgumentException("Handler " + handler + " is already registered.");
        }
        handlers.add(handler);
    }

    public static synchronized boolean isCellHandled(ItemStack is) {
        if (is.isEmpty()) {
            return false;
        }
        for (ICellHandler handler : handlers) {
            if (handler.isCell(is)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static synchronized ICellHandler getHandler(ItemStack is) {
        if (is.isEmpty()) {
            return null;
        }
        for (ICellHandler handler : handlers) {
            if (handler.isCell(is)) {
                return handler;
            }
        }
        return null;
    }

    /**
     * @return null if the item is not a storage cell.
     */
    @Nullable
    public static synchronized StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
        if (is.isEmpty()) {
            return null;
        }
        for (ICellHandler handler : handlers) {
            if (handler.isCell(is)) {
                return handler.getCellInventory(is, host);
            }
        }
        return null;
    }

    /**
     * Registers the screen that an ME Chest opens for cells of a given key type.
     */
    public static synchronized void addCellGuiHandler(ICellGuiHandler handler) {
        Objects.requireNonNull(handler, "handler");
        guiHandlers.add(handler);
    }

    @Nullable
    public static synchronized ICellGuiHandler getGuiHandler(AEKeyType keyType) {
        return getGuiHandler(keyType, ItemStack.EMPTY);
    }

    /**
     * Looks up the screen for one specific cell item.
     * <p>
     * A handler claiming this exact item through {@link ICellGuiHandler#isSpecializedFor(ItemStack)} wins over the
     * generic handler for the same key type. This is what lets an addon ship a cell with its own screen, and it is
     * why the lookup takes the stack and not only the type.
     */
    @Nullable
    public static synchronized ICellGuiHandler getGuiHandler(AEKeyType keyType, ItemStack cell) {
        Objects.requireNonNull(keyType, "keyType");

        ICellGuiHandler generic = null;
        for (ICellGuiHandler handler : guiHandlers) {
            if (!handler.isHandlerFor(keyType)) {
                continue;
            }
            if (!cell.isEmpty() && handler.isSpecializedFor(cell)) {
                return handler;
            }
            if (generic == null) {
                generic = handler;
            }
        }
        return generic;
    }
}
