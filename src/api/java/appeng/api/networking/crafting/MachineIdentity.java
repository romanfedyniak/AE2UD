/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.api.networking.crafting;


import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;


/**
 * What a machine looks like to a player: the name a terminal lists it under, and an item to draw for it.
 * <p>
 * A name on its own is enough for a list, but not for a crafting tree, where a row of names says far less
 * than a row of pictures.
 */
public final class MachineIdentity {

    public static final MachineIdentity NOTHING = new MachineIdentity("Nothing", ItemStack.EMPTY);

    private final String name;
    private final ItemStack icon;

    public MachineIdentity(@Nonnull final String name, @Nonnull final ItemStack icon) {
        this.name = name;
        this.icon = icon;
    }

    /**
     * Either a translation key or, where a mod insists on formatting its own, a finished name.
     */
    @Nonnull
    public String getName() {
        return this.name;
    }

    /**
     * Empty when nothing sensible stands for the machine, which a caller must be ready for.
     */
    @Nonnull
    public ItemStack getIcon() {
        return this.icon;
    }
}
