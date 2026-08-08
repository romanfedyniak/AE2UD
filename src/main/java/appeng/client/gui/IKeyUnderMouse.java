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

package appeng.client.gui;


import appeng.api.stacks.AEKey;

import javax.annotation.Nullable;


/**
 * A screen that shows things somewhere other than in slots, and can say what the cursor is over. HEI asks
 * through one choke point, so answering here is enough for recipe lookups, the recipe keybinds, bookmarks
 * and tooltips at once - the screen does not have to know about any of them.
 */
public interface IKeyUnderMouse {

    @Nullable
    AEKey getKeyUnderMouse(int mouseX, int mouseY);
}
