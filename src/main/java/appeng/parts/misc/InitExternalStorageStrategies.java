/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.parts.misc;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.stacks.AEKeyType;
import appeng.fluids.parts.FluidHandlerAdapter;

/**
 * Registers the built-in {@link ExternalStorageStrategy} implementations that {@link PartStorageBus} (and any
 * future storage bus) can pick up through
 * {@link appeng.api.behaviors.StackWorldBehaviors#createExternalStorageStrategies}.
 * <p/>
 * The item strategy was registered in wave 3b. The fluid strategy (wave 5) registers through this exact same
 * public API, with no changes required in {@link PartStorageBus} or here -- from this point on the same storage
 * bus serves items and fluids simultaneously (see CONTRACT.md §9, wave 3b entry). That is what made the
 * separate fluid storage bus redundant, and it was deleted in the fluids decomposition.
 * <p/>
 * Called once from {@code appeng.core.Registration} during mod init.
 */
public final class InitExternalStorageStrategies {

    private InitExternalStorageStrategies() {
    }

    public static void register() {
        ExternalStorageStrategy.register(AEKeyType.items(), ItemHandlerAdapter.Strategy::new);
        ExternalStorageStrategy.register(AEKeyType.fluids(), FluidHandlerAdapter.Strategy::new);
    }
}
