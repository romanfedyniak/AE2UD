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

package appeng.util.inv;


import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import net.minecraft.item.ItemStack;


/**
 * Answers "would the network take this?" for {@link appeng.util.InventoryAdaptor}, so a pull from a
 * neighbouring inventory can be skipped when the destination is full.
 * <p>
 * Nothing in the mod calls it, and nothing did before the port either - it is kept for addons.
 */
public class MEStorageDestination implements IInventoryDestination {

    private final MEStorage me;

    public MEStorageDestination(final MEStorage o) {
        this.me = o;
    }

    @Override
    public boolean canInsert(final ItemStack stack) {

        if (stack.isEmpty()) {
            return false;
        }

        final AEItemKey what = AEItemKey.of(stack);
        if (what == null) {
            return false;
        }

        final long inserted = this.me.insert(what, stack.getCount(), Actionable.SIMULATE, null);

        return inserted > 0;
    }
}
