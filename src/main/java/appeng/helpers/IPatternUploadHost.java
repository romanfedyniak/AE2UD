/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers;

import net.minecraft.item.ItemStack;

import appeng.api.networking.security.IActionHost;

/**
 * A pattern terminal, as the screen that asks where to send a pattern sees it. That screen is a container of
 * its own, so the terminal's is closed by then and the pattern has to be reachable through the host.
 */
public interface IPatternUploadHost extends ISubMenuHost, IActionHost {

    /** The encoded pattern waiting in the terminal's output slot, or an empty stack. */
    ItemStack getEncodedPattern();

    void setEncodedPattern(ItemStack pattern);
}
