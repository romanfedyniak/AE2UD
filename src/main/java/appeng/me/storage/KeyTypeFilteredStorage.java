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

package appeng.me.storage;


import java.util.function.Predicate;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;


/**
 * A storage seen through a {@link appeng.api.util.KeyTypeSelection}: a key whose type is turned off is
 * neither reported nor accepted, as though the storage never held that type at all.
 * <p/>
 * Wrapping is what makes the selection mean the same thing however the storage was resolved. A storage bus
 * composes one wrapper per key type out of the strategy registry and can simply leave the disabled ones out,
 * but the two paths before that - another ME network, and a Storage Drawers repository - hand back a single
 * storage that is not keyed by type and has no such seam to leave anything out of.
 */
public class KeyTypeFilteredStorage extends DelegatingMEInventory {

    private final Predicate<AEKeyType> enabled;

    public KeyTypeFilteredStorage(final MEStorage delegate, final Predicate<AEKeyType> enabled) {
        super(delegate);
        this.enabled = enabled;
    }

    @Override
    public boolean isPreferredStorageFor(final AEKey input, final IActionSource source) {
        return this.allows(input) && super.isPreferredStorageFor(input, source);
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        return this.allows(what) ? super.insert(what, amount, mode, source) : 0;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        return this.allows(what) ? super.extract(what, amount, mode, source) : 0;
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        final KeyCounter everything = new KeyCounter();
        super.getAvailableStacks(everything);

        for (final Object2LongMap.Entry<AEKey> entry : everything) {
            if (this.allows(entry.getKey())) {
                out.add(entry.getKey(), entry.getLongValue());
            }
        }
    }

    @Override
    public KeyCounter getAvailableStacks() {
        final KeyCounter out = new KeyCounter();
        this.getAvailableStacks(out);
        return out;
    }

    private boolean allows(final AEKey what) {
        return this.enabled.test(what.getType());
    }
}
