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

package appeng.client.me;


import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.container.me.GridInventoryEntry;
import appeng.container.implementations.TerminalCraftingPin;
import appeng.api.storage.IPlayerTerminalPins;
import appeng.core.AEConfig;
import appeng.integration.Integrations;
import appeng.integration.modules.bogosorter.InventoryBogoSortModule;
import appeng.items.storage.ItemViewCell;
import appeng.util.ItemSorters;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;


public class ItemRepo {

    /**
     * Keyed by {@link GridInventoryEntry#getWhat()}. Replaces the old {@code IItemList<IAEItemStack>} -
     * {@code AEKey.equals()} is already size-insensitive identity, so a plain map lookup/replace here is
     * the correct translation of the old {@code list.findPrecise(is)} dance (see {@link #postUpdate}).
     */
    private final Map<AEKey, GridInventoryEntry> entries = new Object2ObjectOpenHashMap<>();
    private List<GridInventoryEntry> view = new ArrayList<>();
    private final IScrollSource src;
    private final ISortSource sortSrc;

    private int rowSize = 9;

    private String searchString = "";
    private AEKeyFilter myPartitionList;
    private String innerSearch = "";
    private boolean hasPower;

    private Enum lastView;
    private Enum lastSearchMode;
    private Enum lastSortBy;
    private Enum lastSortDir;
    private String lastSearch = "";

    private boolean resort = true;
    private boolean changed = false;
    private final AEKey[] playerPins = new AEKey[IPlayerTerminalPins.MAX_PINS];
    private List<TerminalCraftingPin> craftingPins = Collections.emptyList();
    private int visibleCraftingPinRows;
    private int visiblePlayerPinRows;


    public ItemRepo(final IScrollSource src, final ISortSource sortSrc) {
        this.src = src;
        this.sortSrc = sortSrc;
    }

    public GridInventoryEntry getReferenceItem(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.view.size()) {
            return null;
        }
        return this.view.get(idx);
    }

    public GridInventoryEntry getCraftingPinEntry(int idx) {
        if (idx < 0 || idx >= craftingPins.size()) {
            return null;
        }
        AEKey what = craftingPins.get(idx).getWhat();
        GridInventoryEntry entry = entries.get(what);
        return entry != null ? entry : new GridInventoryEntry(what, 0, 0, false);
    }

    public GridInventoryEntry getPlayerPinEntry(int idx) {
        if (idx < 0 || idx >= playerPins.length || playerPins[idx] == null) {
            return null;
        }
        GridInventoryEntry entry = entries.get(playerPins[idx]);
        return entry != null ? entry : new GridInventoryEntry(playerPins[idx], 0, 0, false);
    }

    public TerminalCraftingPin getCraftingPinStatus(int idx) {
        return idx >= 0 && idx < craftingPins.size() ? craftingPins.get(idx) : null;
    }

    public AEKey getPlayerPin(int idx) {
        return idx >= 0 && idx < playerPins.length ? playerPins[idx] : null;
    }

    public void setPins(AEKey[] playerPins, List<TerminalCraftingPin> craftingPins,
            int visibleCraftingRows, int visiblePlayerRows) {
        System.arraycopy(playerPins, 0, this.playerPins, 0,
                Math.min(playerPins.length, this.playerPins.length));
        this.craftingPins = new ArrayList<>(craftingPins);
        this.visibleCraftingPinRows = visibleCraftingRows;
        this.visiblePlayerPinRows = visiblePlayerRows;
        this.changed = true;
    }

    void setSearch(final String search) {
        this.searchString = search == null ? "" : search;
    }

    /**
     * Replaces or removes the row for {@code entry.getWhat()}. {@link GridInventoryEntry#isMeaningful()}
     * false is the server telling us the row is gone - the old code expressed the same "gone" state as a
     * zero-and-craftable-false stack it left in the list; here we just drop the key.
     */
    public void postUpdate(final GridInventoryEntry entry) {
        if (!entry.isMeaningful()) {
            this.entries.remove(entry.getWhat());
        } else {
            this.entries.put(entry.getWhat(), entry);
        }

        changed = true;
    }

    public long getItemCount(final AEKey what) {
        final GridInventoryEntry e = this.entries.get(what);
        return e == null ? 0 : e.getStoredAmount();
    }

    /**
     * Every row the server has sent, unfiltered and unsorted — {@link #getReferenceItem(int)} walks the
     * search-filtered view instead. This is what the old {@code ContainerMEMonitorable.items} field gave
     * the JEI integration; the client-side inventory now lives here rather than on the container.
     */
    public Collection<GridInventoryEntry> getAllEntries() {
        return Collections.unmodifiableCollection(this.entries.values());
    }

    public void setViewCell(final ItemStack[] list) {
        this.myPartitionList = ItemViewCell.createFilter(list);
        this.changed = true;
    }

    public void updateView() {

        final Enum viewMode = this.sortSrc.getSortDisplay();

        if (lastView != viewMode) {
            resort = true;
            lastView = viewMode;
        }

        final Enum searchMode = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
        if (lastSearchMode != searchMode) {
            resort = true;
            lastSearchMode = searchMode;
        }

        if (searchMode == SearchBoxMode.JEI_AUTOSEARCH || searchMode == SearchBoxMode.JEI_MANUAL_SEARCH || searchMode == SearchBoxMode.JEI_AUTOSEARCH_KEEP || searchMode == SearchBoxMode.JEI_MANUAL_SEARCH_KEEP) {
            this.updateJEI(this.searchString);
        }

        if (!lastSearch.equals(searchString)) {
            resort = true;
            lastSearch = searchString;
        }

        final Enum sortBy = this.sortSrc.getSortBy();
        final Enum sortDir = this.sortSrc.getSortDir();

        if (lastSortBy != sortBy) {
            resort = true;
            lastSortBy = sortBy;
        }

        if (lastSortDir != sortDir) {
            resort = true;
            lastSortDir = sortDir;
        }

        if (changed || resort) {
            changed = false;
            resort = false;

            view = new ArrayList<>();

            ItemSorters.setDirection((appeng.api.config.SortDir) sortDir);
            ItemSorters.init();

            final Comparator<GridInventoryEntry> c = getComparator(sortBy);

            final Set<AEKey> visiblePins = new HashSet<>();
            int craftingLimit = Math.min(craftingPins.size(), visibleCraftingPinRows * IPlayerTerminalPins.SLOTS_PER_ROW);
            for (int i = 0; i < craftingLimit; i++) {
                visiblePins.add(craftingPins.get(i).getWhat());
            }
            int playerLimit = Math.min(playerPins.length, visiblePlayerPinRows * IPlayerTerminalPins.SLOTS_PER_ROW);
            for (int i = 0; i < playerLimit; i++) {
                if (playerPins[i] != null) visiblePins.add(playerPins[i]);
            }

            for (final GridInventoryEntry entry : this.entries.values()) {
                if (!visiblePins.contains(entry.getWhat())) {
                    addEntry(entry, viewMode);
                }
            }

            view.sort(c);
        }
    }

    private static Comparator<GridInventoryEntry> getComparator(Enum sortBy) {
        final Comparator<Object2LongMap.Entry<AEKey>> c;

        if (sortBy == SortOrder.MOD) {
            c = ItemSorters.CONFIG_BASED_SORT_BY_MOD;
        } else if (sortBy == SortOrder.AMOUNT) {
            c = ItemSorters.CONFIG_BASED_SORT_BY_SIZE;
        } else if (sortBy == SortOrder.INVTWEAKS) {
            if (InventoryBogoSortModule.isLoaded()) {
                c = InventoryBogoSortModule.COMPARATOR;
            } else {
                c = ItemSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS;
            }
        } else {
            c = ItemSorters.CONFIG_BASED_SORT_BY_NAME;
        }

        // ItemSorters' comparators are shaped for iterating a KeyCounter (Object2LongMap.Entry<AEKey>);
        // this repo holds GridInventoryEntry rows instead, so adapt rather than touching the wave 1 file.
        return (a, b) -> c.compare(new KeyAmountEntry(a.getWhat(), a.getStoredAmount()), new KeyAmountEntry(b.getWhat(), b.getStoredAmount()));
    }

    /**
     * Refreshes the amounts of the rows already on screen, in place, without re-filtering or re-sorting.
     * <p>
     * This is what a terminal does while the player holds shift over an ME slot: the row must not move,
     * or a series of shift-clicks lands on whatever slid under the cursor, but the count still has to
     * count down. {@link #updateView()} cannot be used for that - it rebuilds and re-sorts - and doing
     * nothing at all leaves the view holding the {@link GridInventoryEntry} objects from before the
     * change, since they are immutable and only the map behind them was updated.
     * <p>
     * A row whose key has disappeared entirely is zeroed rather than removed, for the same reason: it
     * would shift every row after it.
     */
    public void refreshViewAmounts() {
        final Enum viewMode = this.sortSrc.getSortDisplay();

        for (int i = 0; i < this.view.size(); i++) {
            final GridInventoryEntry shown = this.view.get(i);
            final GridInventoryEntry current = this.entries.get(shown.getWhat());

            if (current == null) {
                this.view.set(i, new GridInventoryEntry(shown.getWhat(), 0, 0, false));
            } else if (viewMode == ViewItems.CRAFTABLE) {
                // Matches addEntry's zero-copy: this view shows what can be made, not what is stocked.
                this.view.set(i, current.withStoredAmount(0));
            } else {
                this.view.set(i, current);
            }
        }
    }

    private void addEntry(GridInventoryEntry entry, Enum viewMode) {

        final boolean needsZeroCopy = viewMode == ViewItems.CRAFTABLE;

        final boolean terminalSearchToolTips = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_TOOLTIPS) != YesNo.NO;

        boolean searchMod = false;

        this.innerSearch = searchString.toLowerCase();
        if (this.innerSearch.startsWith("@")) {
            searchMod = true;
            this.innerSearch = this.innerSearch.substring(1);
        }

        Pattern m = null;
        try {
            m = Pattern.compile(this.innerSearch, Pattern.CASE_INSENSITIVE);
        } catch (final Throwable ignore) {
            try {
                m = Pattern.compile(Pattern.quote(this.innerSearch), Pattern.CASE_INSENSITIVE);
            } catch (final Throwable __) {
                return;
            }
        }

        if (this.myPartitionList != null) {
            if (!this.myPartitionList.matches(entry.getWhat())) {
                return;
            }
        }

        if (viewMode == ViewItems.CRAFTABLE && !entry.isCraftable()) {
            return;
        }

        if (viewMode == ViewItems.STORED && entry.getStoredAmount() == 0) {
            return;
        }

        final String dspName = (searchMod ? Platform.getModId(entry.getWhat()) : Platform.getItemDisplayName(entry.getWhat())).toLowerCase();
        boolean foundMatchingItemStack = true;

        for (String term : innerSearch.split(" ")) {
            if (term.length() > 1 && (term.startsWith("-") || term.startsWith("!"))) {
                term = term.substring(1);
                if (dspName.contains(term)) {
                    foundMatchingItemStack = false;
                    break;
                }
            } else if (!dspName.contains(term)) {
                foundMatchingItemStack = false;
                break;
            }
        }

        if (terminalSearchToolTips && !foundMatchingItemStack) {
            final List<String> tooltip = Platform.getTooltip(entry.getWhat());
            for (final String line : tooltip) {
                if (m.matcher(line).find()) {
                    foundMatchingItemStack = true;
                    break;
                }
            }
        }

        if (foundMatchingItemStack) {
            if (needsZeroCopy) {
                entry = entry.withStoredAmount(0);
            }
            this.view.add(entry);
        }
    }

    private void updateJEI(String filter) {
        Integrations.jei().setSearchText(filter);
    }

    public int size() {
        return this.view.size();
    }

    /**
     * Zeroes every row (stored/requestable amounts and the craftable flag) but keeps the keys - the exact
     * semantics of the old {@code IItemList.resetStatus()} (which called {@code IAEStack.reset()} on every
     * element in place), used by {@code GuiNetworkStatus} to blank the machine list before a full repopulate.
     * Deliberately not a full {@code Map.clear()} - see {@link #postUpdate} for the "remove the row
     * entirely" case.
     */
    public void clear() {
        for (final Map.Entry<AEKey, GridInventoryEntry> e : this.entries.entrySet()) {
            e.setValue(new GridInventoryEntry(e.getKey(), 0, 0, false));
        }
        this.changed = true;
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
     * Minimal adapter so {@link appeng.util.ItemSorters}'s {@code Object2LongMap.Entry<AEKey>}-shaped
     * comparators (built for iterating a {@link appeng.api.stacks.KeyCounter}) can also compare the
     * {@link GridInventoryEntry} rows this repo actually holds, without touching the wave 1 file that
     * defines them.
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
