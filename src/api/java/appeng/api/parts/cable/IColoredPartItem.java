/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.parts.cable;

import net.minecraft.item.ItemStack;

import appeng.api.util.AEColor;

/**
 * An item that places a part of a colour, such as an addon's own cable. AE2 reads the colour off the item when
 * the part is built, so a cable knows what it was painted before it ever joins a network.
 */
public interface IColoredPartItem {

    AEColor getPartColor(ItemStack stack);
}
