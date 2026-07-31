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

package appeng.helpers;


import appeng.core.sync.GuiBridge;
import net.minecraft.item.ItemStack;


/**
 * Something a sub-screen can return the player to.
 * <p>
 * Upstream calls this {@code ISubMenuHost} and gives it {@code returnToMainMenu} plus {@code getMainMenuIcon}.
 * Here the two halves are the screen to reopen and the icon to label the way back with, because in this
 * version returning is a matter of switching GUIs with a packet rather than of a menu object.
 * <p>
 * Not in {@code src/api} for that reason: {@link GuiBridge} is not part of the API.
 */
public interface ISubMenuHost {

    /**
     * The screen a sub-screen sends the player back to.
     */
    GuiBridge getGuiBridge();

    /**
     * What to draw on the button that goes back - normally the machine's own item.
     */
    ItemStack getItemStackRepresentation();
}
