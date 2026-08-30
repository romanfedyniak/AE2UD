/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.container.interfaces;

import net.minecraft.item.ItemStack;

/**
 * A screen opened from a wireless terminal, which knows which terminal that is.
 *
 * <p>The mode switch needs it on the client, where the only place the list of modes exists is the item's own
 * NBT.</p>
 */
public interface IWirelessTerminalContainer {

    ItemStack getTerminal();
}
