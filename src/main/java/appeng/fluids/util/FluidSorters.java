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

package appeng.fluids.util;


import appeng.api.config.SortDir;
import appeng.api.stacks.AEKey;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.Comparator;


/**
 * Sorts entries of a {@code KeyCounter}, i.e. {@code Object2LongMap.Entry<AEKey>} pairs of a key and the
 * amount currently stored under it. Exactly the retyping {@code appeng.util.ItemSorters} received in
 * wave 1: the key half plays the role identity used to play, the {@code long} value plays the role the
 * stack size used to play. The sort orders themselves are unchanged - name ascending, mod then name, and
 * amount largest-first, each flipped by {@link #setDirection}.
 * <p/>
 * The only caller is {@code appeng.client.me.FluidRepo}, migrated in wave 4 and already written against
 * this shape; it adapts its {@code GridInventoryEntry} rows into entries before comparing.
 *
 * @author BrockWS
 * @version rv6 - 22/05/2018
 * @since rv6 22/05/2018
 */
public class FluidSorters {
    private static SortDir Direction = SortDir.ASCENDING;

    public static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_NAME = (o1, o2) ->
    {
        int cmp = Platform.getFluidDisplayName(o1.getKey()).compareToIgnoreCase(Platform.getFluidDisplayName(o2.getKey()));

        if (cmp == 0) {
            cmp = identityTieBreak(o1.getKey(), o2.getKey());
        }

        return applyDirection(cmp);
    };

    public static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_MOD = (o1, o2) ->
    {
        int cmp = Platform.getModId(o1.getKey()).compareToIgnoreCase(Platform.getModId(o2.getKey()));

        if (cmp == 0) {
            cmp = Platform.getFluidDisplayName(o1.getKey()).compareToIgnoreCase(Platform.getFluidDisplayName(o2.getKey()));
        }

        if (cmp == 0) {
            cmp = identityTieBreak(o1.getKey(), o2.getKey());
        }

        return applyDirection(cmp);
    };

    public static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_SIZE = (o1, o2) ->
    {
        int cmp = Long.compare(o2.getLongValue(), o1.getLongValue());

        if (cmp == 0) {
            cmp = Platform.getFluidDisplayName(o1.getKey()).compareToIgnoreCase(Platform.getFluidDisplayName(o2.getKey()));
        }

        if (cmp == 0) {
            cmp = identityTieBreak(o1.getKey(), o2.getKey());
        }

        return applyDirection(cmp);
    };

    private static SortDir getDirection() {
        return Direction;
    }

    public static void setDirection(final SortDir direction) {
        Direction = direction;
    }

    /**
     * See {@code ItemSorters.identityTieBreak}: without a total order, tying rows fall back to the backing
     * map's iteration order and visibly swap places whenever an unrelated row is added or removed.
     */
    private static int identityTieBreak(final AEKey a, final AEKey b) {
        final int cmp = String.valueOf(a.getId()).compareTo(String.valueOf(b.getId()));
        return cmp != 0 ? cmp : Integer.compare(a.hashCode(), b.hashCode());
    }

    private static int applyDirection(int cmp) {
        if (getDirection() == SortDir.ASCENDING) {
            return cmp;
        }
        return -cmp;
    }
}
