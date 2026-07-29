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

package appeng.client.me;


import appeng.api.config.Settings;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.api.stacks.AEKey;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.container.me.GridInventoryEntry;
import appeng.core.AEConfig;
import appeng.fluids.util.FluidSorters;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * @author BrockWS
 * @version rv6 - 22/05/2018
 * @since rv6 22/05/2018
 *
 * NOTE for wave 5: {@code appeng.fluids.util.FluidSorters} still exposes {@code Comparator<IAEFluidStack>}
 * fields, a deleted api type (CONTRACT.md §4.1). This class is written assuming FluidSorters gets the exact
 * same treatment {@code appeng.util.ItemSorters} already got in wave 1a - {@code
 * CONFIG_BASED_SORT_BY_MOD}/{@code _BY_SIZE}/{@code _BY_NAME} retyped to {@code
 * Comparator<Object2LongMap.Entry<AEKey>>} - since the old FluidRepo used exactly those three names and
 * nothing else. No INVTWEAKS/bogosort branch existed here before and none is added now; this repo's
 * feature set (mod-prefix search, tooltip search, no term negation, no JEI bridge, no view-cell filter) is
 * unchanged from the pre-port file.
 */
public class FluidRepo {
    private final Map<AEKey, GridInventoryEntry> entries = new Object2ObjectOpenHashMap<>();
    private final ArrayList<GridInventoryEntry> view = new ArrayList<>();
    private final IScrollSource src;
    private final ISortSource sortSrc;

    private int rowSize = 9;

    private String searchString = "";
    private boolean hasPower;

    public FluidRepo(final IScrollSource src, final ISortSource sortSrc) {
        this.src = src;
        this.sortSrc = sortSrc;
    }

    public void updateView() {
        this.view.clear();

        this.view.ensureCapacity(this.entries.size());

        String innerSearch = this.searchString;

        boolean searchMod = false;
        if (innerSearch.startsWith("@")) {
            searchMod = true;
            innerSearch = innerSearch.substring(1);
        }

        Pattern m;
        try {
            m = Pattern.compile(innerSearch.toLowerCase(), Pattern.CASE_INSENSITIVE);
        } catch (final Exception ignore1) {
            try {
                m = Pattern.compile(Pattern.quote(innerSearch.toLowerCase()), Pattern.CASE_INSENSITIVE);
            } catch (final Exception ignore2) {
                return;
            }
        }

        final Enum viewMode = this.sortSrc.getSortDisplay();
        final boolean needsZeroCopy = viewMode == ViewItems.CRAFTABLE;
        final boolean terminalSearchToolTips = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_TOOLTIPS) != YesNo.NO;

        for (GridInventoryEntry entry : this.entries.values()) {
            if (viewMode == ViewItems.CRAFTABLE && !entry.isCraftable()) {
                continue;
            }

            if (viewMode == ViewItems.STORED && entry.getStoredAmount() == 0) {
                continue;
            }

            final String dspName = searchMod ? Platform.getModId(entry.getWhat()) : Platform.getFluidDisplayName(entry.getWhat());
            boolean foundMatchingFluidStack = false;
            boolean notDone = true;

            if (m.matcher(dspName.toLowerCase()).find()) {
                notDone = false;
                foundMatchingFluidStack = true;
            }

            if (terminalSearchToolTips && notDone && !searchMod) {
                final java.util.List<String> tooltip = Platform.getTooltip(entry.getWhat());

                for (final String line : tooltip) {
                    if (m.matcher(line).find()) {
                        foundMatchingFluidStack = true;
                        break;
                    }
                }
            }

            if (foundMatchingFluidStack) {
                if (needsZeroCopy) {
                    entry = entry.withStoredAmount(0);
                }

                this.view.add(entry);
            }
        }

        final Enum sortBy = this.sortSrc.getSortBy();
        final Enum sortDir = this.sortSrc.getSortDir();

        FluidSorters.setDirection((appeng.api.config.SortDir) sortDir);

        final java.util.Comparator<Object2LongMap.Entry<AEKey>> c;
        if (sortBy == SortOrder.MOD) {
            c = FluidSorters.CONFIG_BASED_SORT_BY_MOD;
        } else if (sortBy == SortOrder.AMOUNT) {
            c = FluidSorters.CONFIG_BASED_SORT_BY_SIZE;
        } else {
            c = FluidSorters.CONFIG_BASED_SORT_BY_NAME;
        }

        this.view.sort((a, b) -> c.compare(new KeyAmountEntry(a.getWhat(), a.getStoredAmount()), new KeyAmountEntry(b.getWhat(), b.getStoredAmount())));
    }

    /**
     * Replaces or removes the row for {@code entry.getWhat()} - see {@code ItemRepo.postUpdate} for why a
     * plain map put/remove is the correct translation of the old find-precise-then-mutate dance.
     */
    public void postUpdate(final GridInventoryEntry entry) {
        if (!entry.isMeaningful()) {
            this.entries.remove(entry.getWhat());
        } else {
            this.entries.put(entry.getWhat(), entry);
        }
    }

    public GridInventoryEntry getReferenceFluid(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.view.size()) {
            return null;
        }
        return this.view.get(idx);
    }

    public int size() {
        return this.view.size();
    }

    /**
     * Zeroes every row but keeps the keys - see {@code ItemRepo.clear()}.
     */
    public void clear() {
        for (final Map.Entry<AEKey, GridInventoryEntry> e : this.entries.entrySet()) {
            e.setValue(new GridInventoryEntry(e.getKey(), 0, 0, false));
        }
    }

    public boolean hasPower() {
        return this.hasPower;
    }

    public void setPower(final boolean hasPower) {
        this.hasPower = hasPower;
    }

    public int getRowSize() {
        return this.rowSize;
    }

    public void setRowSize(final int rowSize) {
        this.rowSize = rowSize;
    }

    public String getSearchString() {
        return this.searchString;
    }

    public void setSearchString(@Nonnull final String searchString) {
        this.searchString = searchString;
    }

    /**
     * Same adapter as {@code ItemRepo.KeyAmountEntry} - see there for why it exists.
     */
    private static final class KeyAmountEntry implements Object2LongMap.Entry<AEKey> {
        private final AEKey key;
        private final long amount;

        KeyAmountEntry(final AEKey key, final long amount) {
            this.key = key;
            this.amount = amount;
        }

        @Override
        public AEKey getKey() {
            return this.key;
        }

        @Override
        public long getLongValue() {
            return this.amount;
        }

        @Override
        public Long getValue() {
            // fastutil's Object2LongMap.Entry still extends Map.Entry<K, Long> in this version and does
            // not default the boxed accessors, so both have to be spelled out. Nothing calls either.
            return this.amount;
        }

        @Override
        public Long setValue(final Long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long setValue(final long value) {
            throw new UnsupportedOperationException();
        }
    }
}
