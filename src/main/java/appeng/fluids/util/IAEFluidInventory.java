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

package appeng.fluids.util;


import appeng.util.inv.InvOperation;
import net.minecraftforge.fluids.FluidStack;

@FunctionalInterface
public interface IAEFluidInventory {
    void onFluidInventoryChanged(final IAEFluidTank inv, final int slot);

    /**
     * <strong>This is the overload {@link AEFluidInventory} actually calls</strong>, so it must not stay a
     * no-op by default: an implementor that only overrode the two-argument method above would never hear
     * about a change at all. That was the case for {@code PartFluidFormationPlane},
     * {@code PartFluidStorageBus} and {@code PartFluidLevelEmitter}, whose filters and watchers therefore
     * only ever rebuilt on world load.
     * <p>
     * The mismatch predates the storage port ({@code git show 1e855f729}), where it was harmless because
     * the affected parts re-read their config inventory directly on every rebuild. Once a part caches
     * anything derived from the config, it stops being harmless.
     */
    default void onFluidInventoryChanged(final IAEFluidTank inv, final int slot, InvOperation operation, FluidStack added, FluidStack removed) {
        this.onFluidInventoryChanged(inv, slot);
    }

    default void onFluidInventoryChanged(final IAEFluidTank inv, FluidStack added, FluidStack removed) {
    }

}
