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

    /** The magnet card and two energy cards. */
    int UPGRADE_SLOTS = 3;

    /**
     * Where the plate holding those slots hangs beside the window, and how far inside it the first slot
     * sits. Named here because the plate is drawn by the screen and the slots are placed by the container,
     * and the two had drifted apart once already.
     */
    int UPGRADE_PLATE_X = 198;
    int UPGRADE_PLATE_Y = 106;
    int UPGRADE_SLOT_INSET = 8;

    ItemStack getTerminal();
}
