/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.exposer;

import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import appeng.api.behaviors.ExposerStrategy;
import appeng.api.stacks.AEKeyType;

/**
 * Items and fluids reach the storage exposer through the same public registration an addon uses.
 */
public final class InitExposerStrategies {

    private InitExposerStrategies() {
    }

    public static void register() {
        ExposerStrategy.register(AEKeyType.items(), CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, ItemExposerHandler::new);
        ExposerStrategy.register(AEKeyType.fluids(), CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, FluidExposerHandler::new);
    }
}
