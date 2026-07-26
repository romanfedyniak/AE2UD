/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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


import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.config.Actionable;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;


/**
 * Adapts an adjacent {@link IFluidHandler} (a tank) so it behaves like an {@link MEStorage}. Used by fluid storage
 * buses.
 * <p/>
 * See {@link MEMonitorIInventory} for why this keeps the pre-migration tank-property scanning approach instead of
 * AE2-original's {@code ExternalStorageFacade}: 1.12.2 has no equivalent to NeoForge's unified transfer API. Only
 * {@code IAEFluidStack}/{@code IItemList} are replaced, by {@link AEFluidKey}/{@link KeyCounter}.
 */
public class MEMonitorIFluidHandler implements MEStorage, ITickingMonitor {

    private final IFluidHandler handler;
    private KeyCounter cache = new KeyCounter();
    private StorageFilter mode = StorageFilter.EXTRACTABLE_ONLY;

    public MEMonitorIFluidHandler(final IFluidHandler handler) {
        this.handler = handler;
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable type, final IActionSource source) {
        if (!(what instanceof AEFluidKey fluidKey) || amount <= 0) {
            return 0;
        }

        final int requested = (int) Math.min(amount, Integer.MAX_VALUE);
        final int filled = this.handler.fill(fluidKey.toStack(requested), type == Actionable.MODULATE);

        if (filled > 0 && type == Actionable.MODULATE) {
            this.cache.add(fluidKey, filled);
        }

        return filled;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable type, final IActionSource source) {
        if (!(what instanceof AEFluidKey fluidKey) || amount <= 0) {
            return 0;
        }

        final int requested = (int) Math.min(amount, Integer.MAX_VALUE);
        final FluidStack removed = this.handler.drain(fluidKey.toStack(requested), type == Actionable.MODULATE);

        if (removed == null || removed.amount <= 0) {
            return 0;
        }

        if (type == Actionable.MODULATE) {
            this.cache.remove(fluidKey, removed.amount);
        }

        return removed.amount;
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        out.addAll(this.cache);
    }

    @Override
    public TickRateModulation onTick() {
        final KeyCounter next = new KeyCounter();
        final IFluidTankProperties[] tanks = this.handler.getTankProperties();

        for (final IFluidTankProperties tank : tanks) {
            if (this.mode == StorageFilter.EXTRACTABLE_ONLY && this.handler.drain(1, false) == null) {
                continue;
            }

            final FluidStack contents = tank.getContents();
            final AEFluidKey key = AEFluidKey.of(contents);
            if (key != null) {
                next.add(key, contents.amount);
            }
        }

        final boolean changed = this.hasChanged(next);
        this.cache = next;

        return changed ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    private boolean hasChanged(final KeyCounter next) {
        for (final var entry : next) {
            if (this.cache.get(entry.getKey()) != entry.getLongValue()) {
                return true;
            }
        }
        for (final var entry : this.cache) {
            if (next.get(entry.getKey()) != entry.getLongValue()) {
                return true;
            }
        }
        return false;
    }

    public void setMode(final StorageFilter mode) {
        this.mode = mode;
    }

    @Override
    public ITextComponent getDescription() {
        return new TextComponentString("Fluid Tank");
    }
}
