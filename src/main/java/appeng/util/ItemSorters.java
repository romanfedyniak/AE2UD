/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.util;


import appeng.api.config.SortDir;
import appeng.api.stacks.AEKey;
import appeng.integration.Integrations;
import appeng.integration.abstraction.IInvTweaks;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.Comparator;


/**
 * Sorts entries of a {@code KeyCounter}, i.e. {@code Object2LongMap.Entry<AEKey>} pairs of a key and
 * the amount currently stored under it. This replaces the old comparators over {@code IAEItemStack}:
 * the key half of the entry plays the role identity used to play, the {@code long} value plays the
 * role the stack size used to play.
 */
public class ItemSorters {

    private static SortDir Direction = SortDir.ASCENDING;

    public static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_NAME = (o1, o2) ->
    {
        int cmp = Platform.getItemDisplayName(o1.getKey()).compareToIgnoreCase(Platform.getItemDisplayName(o2.getKey()));

        if (cmp == 0) {
            cmp = identityTieBreak(o1.getKey(), o2.getKey());
        }

        return applyDirection(cmp);
    };

    public static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_MOD = (o1, o2) ->
    {
        int cmp = o1.getKey().getModId().compareToIgnoreCase(o2.getKey().getModId());

        if (cmp == 0) {
            cmp = Platform.getItemDisplayName(o1.getKey()).compareToIgnoreCase(Platform.getItemDisplayName(o2.getKey()));
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
            cmp = Platform.getItemDisplayName(o1.getKey()).compareToIgnoreCase(Platform.getItemDisplayName(o2.getKey()));
        }

        if (cmp == 0) {
            cmp = identityTieBreak(o1.getKey(), o2.getKey());
        }

        return applyDirection(cmp);
    };

    private static IInvTweaks api;

    public static final Comparator<Object2LongMap.Entry<AEKey>> CONFIG_BASED_SORT_BY_INV_TWEAKS = (o1, o2) ->
    {
        if (api == null) {
            return CONFIG_BASED_SORT_BY_NAME.compare(o1, o2);
        }

        int cmp = api.compareItems(o1.getKey().wrapForDisplayOrFilter(), o2.getKey().wrapForDisplayOrFilter());

        if (cmp == 0) {
            cmp = identityTieBreak(o1.getKey(), o2.getKey());
        }

        return applyDirection(cmp);
    };

    public static void init() {
        if (api != null) {
            return;
        }

        if (Integrations.invTweaks().isEnabled()) {
            api = Integrations.invTweaks();
        } else {
            api = null;
        }
    }

    public static int compareLong(final long a, final long b) {
        if (a == b) {
            return 0;
        }
        if (a < b) {
            return -1;
        }
        return 1;
    }

    private static SortDir getDirection() {
        return Direction;
    }

    public static void setDirection(final SortDir direction) {
        Direction = direction;
    }

    /**
     * Last-resort ordering so no two distinct keys ever compare equal.
     * <p>
     * Without it the sort is only a partial order, and the rows that tie fall back to whatever order the
     * repo's backing hash map happens to iterate in - which changes as rows are added and removed. Two
     * fluids both holding exactly one bucket would visibly swap places every time the player pulled an
     * unrelated item out of the terminal.
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
