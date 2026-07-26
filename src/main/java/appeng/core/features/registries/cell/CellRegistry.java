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

package appeng.core.features.registries.cell;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.ICellGuiHandler;


/**
 * Registry of {@link ICellGuiHandler}s: picks which GUI to open when a cell is placed in an ME Chest
 * ({@code TileChest}).
 * <p/>
 * The old {@code ICellRegistry} carried both {@code ICellHandler}s and {@code ICellGuiHandler}s. Cell handlers moved
 * to the new frozen {@code appeng.api.storage.StorageCells} (CONTRACT.md §4.3), but that replacement only exposes
 * cell-handler methods - it has no counterpart for GUI handlers, and {@code IRegistryContainer.cell()} (which used to
 * expose this registry) was removed outright in wave 0. Nothing in {@code src/api} holds {@link ICellGuiHandler}
 * instances any more.
 * <p/>
 * Per CONTRACT.md rule 6 (do not cut a mechanic) this class keeps the routing mechanism alive as a plain
 * {@code src/main}-only registry - it does not add anything to the frozen API, it only mirrors
 * {@code StorageCells}' shape for the one registry that API dropped. The old {@code isSpecializedFor(ItemStack)}
 * tie-breaker has no equivalent on the new {@link ICellGuiHandler} (its only match method is
 * {@link ICellGuiHandler#isHandlerFor(AEKeyType)}), so resolution is simply "first handler that claims the type".
 * This is flagged for owner review, the same way the wave 1a {@code ICraftingGrid.getCraftables} gap was
 * (CONTRACT.md §8.3).
 */
public final class CellRegistry {

    private static final List<ICellGuiHandler> guiHandlers = new ArrayList<>();

    private CellRegistry() {
    }

    public static synchronized void addCellGuiHandler(final ICellGuiHandler handler) {
        Objects.requireNonNull(handler, "handler");
        guiHandlers.add(handler);
    }

    @Nullable
    public static synchronized ICellGuiHandler getGuiHandler(final AEKeyType keyType) {
        return getGuiHandler(keyType, ItemStack.EMPTY);
    }

    /**
     * Looks up the screen for a specific cell item.
     *
     * A handler that claims this exact item through {@link ICellGuiHandler#isSpecializedFor(ItemStack)} wins over the
     * generic handler for the same key type. This is what lets an addon ship a cell with its own screen, and it is why
     * the lookup takes the stack and not only the type.
     */
    @Nullable
    public static synchronized ICellGuiHandler getGuiHandler(final AEKeyType keyType, final ItemStack cell) {
        Objects.requireNonNull(keyType, "keyType");

        ICellGuiHandler generic = null;
        for (final ICellGuiHandler handler : guiHandlers) {
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
