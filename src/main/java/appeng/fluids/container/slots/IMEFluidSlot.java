/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.fluids.container.slots;


import appeng.api.stacks.GenericStack;

import javax.annotation.Nullable;


/**
 * A slot that draws a fluid rather than an item.
 * <p/>
 * {@code IAEFluidStack getAEFluidStack()} became {@code GenericStack getGenericStack()} in wave 5's
 * prerequisites - the exact rename {@code appeng.util.inv.ItemSlot} already got in wave 1a
 * ({@code getAEItemStack()} -> {@code getGenericStack()}, CONTRACT.md §9 wave 1a). Nothing else about the
 * interface changed.
 * <p/>
 * Already committed against this shape in wave 4, do not change them: {@code appeng.client.me.SlotFluidME}
 * implements it, and {@code appeng.client.gui.AEBaseGui#drawSlot} calls it and then pattern-matches
 * {@code stack.what() instanceof AEFluidKey} to get the sprite. A {@code null} return means "empty, or
 * the terminal has no power" - the caller draws nothing.
 *
 * @author yueh
 * @version rv6
 * @since rv6
 */
public interface IMEFluidSlot {
    @Nullable
    GenericStack getGenericStack();

    default boolean shouldRenderAsFluid() {
        return true;
    }
}
