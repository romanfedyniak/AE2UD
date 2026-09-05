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

import javax.annotation.Nonnull;


/**
 * A machine the player may give a picture to, the way {@link ICustomNameObject} lets them give it a name. The
 * picture stands in for the one a terminal would otherwise work out by looking at what the machine feeds.
 */
public interface ICustomIconObject {

    /** Empty when the player has set none, which leaves the machine to picture itself. */
    @Nonnull
    ItemStack getCustomIcon();

    void setCustomIcon(@Nonnull ItemStack icon);

    /** What would be drawn with none set, so a screen can show what clearing the field goes back to. */
    @Nonnull
    ItemStack getDefaultIcon();
}
