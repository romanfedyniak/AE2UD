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

package appeng.me.storage;


import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.items.contents.CellConfig;


/**
 * A creative storage cell: reports an effectively infinite amount of whatever is configured in it and never
 * actually stores or loses anything.
 * <p/>
 * Replaces the old {@code IMEInventoryHandler}-based implementation, which wrapped itself in a
 * {@code BasicCellInventoryHandler} on construction; since {@link StorageCell} is itself an
 * {@link appeng.api.storage.MEStorage}, that wrapping is no longer needed here - whoever mounts the cell (the
 * drive, out of this package's scope) applies priority/whitelisting the same way it does for
 * {@link BasicCellInventory}.
 */
public class CreativeCellInventory implements StorageCell {

    private final Set<AEKey> configured = new HashSet<>();
    private final ItemStack stack;

    private CreativeCellInventory(final ItemStack o) {
        this.stack = o;

        final CellConfig cc = new CellConfig(o);
        for (final ItemStack is : cc) {
            if (!is.isEmpty()) {
                final AEItemKey key = AEItemKey.of(is);
                if (key != null) {
                    this.configured.add(key);
                }
            }
        }
    }

    public static StorageCell createInventory(final ItemStack o) {
        return new CreativeCellInventory(o);
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        return this.configured.contains(what) ? amount : 0;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        return this.configured.contains(what) ? amount : 0;
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        for (final AEKey key : this.configured) {
            out.add(key, Integer.MAX_VALUE);
        }
    }

    @Override
    public boolean isPreferredStorageFor(final AEKey input, final IActionSource source) {
        return this.configured.contains(input);
    }

    @Override
    public CellState getStatus() {
        return CellState.TYPES_FULL;
    }

    @Override
    public double getIdleDrain() {
        return 0;
    }

    @Override
    public boolean canFitInsideCell() {
        return this.configured.isEmpty();
    }

    @Override
    public void persist() {
        // Nothing to persist: a creative cell's contents are just its configuration.
    }

    @Override
    public ITextComponent getDescription() {
        return new TextComponentString(this.stack.getDisplayName());
    }
}
