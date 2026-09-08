/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
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

package appeng.container.slot;


import net.minecraftforge.items.IItemHandler;


/**
 * A square of the processing grid. A processing pattern names a machine's ingredients rather than a recipe,
 * so anything the network can hold is a legal thing to name.
 */
public class SlotFakeProcessingGrid extends SlotFakePatternGrid {

    public SlotFakeProcessingGrid(final IItemHandler inv, final int idx, final int x, final int y) {
        super(inv, idx, x, y);
    }
}
