/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.util.inv;

import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import appeng.api.behaviors.GenericInventoryAdapters;

/**
 * The two views the mod itself builds over a machine's stock. An addon's key type registers its own the same
 * way, so nothing here is a special case for being built in.
 */
public final class InitGenericInventoryAdapters {

    private InitGenericInventoryAdapters() {
    }

    public static void register() {
        // Network-first in both cases, exactly as the old AppEngNetworkInventory was: what a machine pushes
        // into an interface belongs in the network, not in the interface's own slots. Draining stays local,
        // which is what makes an interface a buffer rather than a pipe into storage.
        GenericInventoryAdapters.register(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                (inv, network, source) -> new NetworkFirstItemHandler(new GenericStackItemHandler(inv), network,
                        source));

        GenericInventoryAdapters.register(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                (inv, network, source) -> new NetworkFirstFluidHandler(new GenericStackFluidHandler(inv), network,
                        source));
    }
}
