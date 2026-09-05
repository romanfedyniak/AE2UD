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
 * A pattern terminal, as the screen that asks where to send a pattern sees it.
 *
 * <p>That screen is a container of its own, so by the time it is open the terminal's own container is
 * closed, and the encoded pattern has to be reachable through whatever the terminal was opened on. For a
 * terminal part that is the part's own inventory; for a wireless one the slot lives in the container, which
 * writes it into the item on every change, so the host reads it back out of there.</p>
 */
public interface IPatternUploadHost extends ISubMenuHost, IActionHost {

    /**
     * The encoded pattern waiting in the terminal's output slot, or an empty stack.
     */
    ItemStack getEncodedPattern();

    /**
     * Takes it out of that slot, once it is somewhere else.
     */
    void setEncodedPattern(ItemStack pattern);
}
