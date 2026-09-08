/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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


import appeng.api.stacks.AEKeyType;
import appeng.api.storage.AEKeyFilter;
import net.minecraftforge.items.IItemHandler;


/**
 * A square of the three-by-three grid a crafting pattern is typed into, which stands for the same square of
 * a vanilla crafting table.
 */
public class SlotFakeCraftingMatrix extends SlotFakePatternGrid {

    private static AEKeyFilter items;

    public SlotFakeCraftingMatrix(final IItemHandler inv, final int idx, final int x, final int y) {
        super(inv, idx, x, y);
    }

    /**
     * Items, because a vanilla recipe is: {@code IRecipe} is shown an {@code InventoryCrafting}, which is
     * made of item stacks. This is not a count of the key types that happen to exist - a type an addon
     * registers tomorrow cannot reach a crafting table either.
     */
    @Override
    public AEKeyFilter acceptedKeys() {
        if (items == null) {
            // Looked up on first use rather than in a static initialiser: key types are registered during
            // startup, and this class is loaded by whatever opens a pattern terminal first.
            items = AEKeyType.items().filter();
        }

        return items;
    }
}
