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

package appeng.tile.misc;


import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;


/**
 * Exposes the condenser as a fake single-tile ME sub-network (see {@link TileCondenser.MEHandler}).
 * <p/>
 * Replaces the old pair of {@code CondenserItemInventory}/{@code CondenserVoidInventory}, one per storage channel:
 * {@link MEStorage} is not generic any more, so a single instance now handles every key type. Items are backed by
 * the condenser's actual output slot; anything else (fluids, an addon's key type) is voided, still contributing
 * power via {@link AEKey#getAmountPerOperation()} (the renamed {@code IStorageChannel#transferFactor()}) exactly
 * like the old {@code CondenserVoidInventory} did.
 * <p/>
 * No listener bookkeeping is needed any more either: whichever grid mounts this (through a storage bus) diffs its
 * {@link #getAvailableStacks(KeyCounter)} snapshot itself, the same way {@code SecurityStationInventory} does.
 */
class CondenserItemInventory implements MEStorage {

    private final TileCondenser target;

    CondenserItemInventory(final TileCondenser te) {
        this.target = te;
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource src) {
        if (amount <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            this.target.addPower(amount / (double) what.getAmountPerOperation());
        }

        return amount;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource src) {
        if (!(what instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }

        final ItemStack slotItem = this.target.getOutputSlot().getStackInSlot(0);
        if (slotItem.isEmpty() || !itemKey.matches(slotItem)) {
            return 0;
        }

        final int count = (int) Math.min(amount, Integer.MAX_VALUE);
        final ItemStack extracted = this.target.getOutputSlot().extractItem(0, count, mode == Actionable.SIMULATE);
        return extracted.getCount();
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        final ItemStack stack = this.target.getOutputSlot().getStackInSlot(0);
        if (!stack.isEmpty()) {
            final AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                out.add(key, stack.getCount());
            }
        }
    }

    @Override
    public ITextComponent getDescription() {
        return new TextComponentString("Condenser");
    }
}
