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
import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import appeng.client.me.search.RepoSearch;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.container.me.GridInventoryEntry;
import appeng.container.implementations.TerminalCraftingPin;
import appeng.api.storage.IPlayerTerminalPins;
import appeng.core.AEClientConfig;
import appeng.integration.Integrations;
import appeng.integration.modules.bogosorter.InventoryBogoSortModule;
import appeng.items.storage.ItemViewCell;
import appeng.util.KeySorters;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;


public class Repo {

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

    private final RepoSearch search = new RepoSearch();
    private AEKeyFilter myPartitionList;
    private boolean hasPower;
    private boolean anyMatches = true;

    private Enum lastView;
    private Enum lastSearchMode;
    private Enum lastSortBy;
    private Enum lastSortDir;

    private boolean resort = true;
    private boolean changed = false;
    /** Only amounts moved, and nothing about the order or the filters depends on them. */
    private boolean amountsChanged = false;
    /**
     * Each key's name, as the sort compares it. Formatting one is most of what a sort costs, and a sort compares
     * every key many times over.
     */
    private final Map<AEKey, String> names = new Object2ObjectOpenHashMap<>();
    private final AEKey[] playerPins = new AEKey[IPlayerTerminalPins.MAX_PINS];
    private List<TerminalCraftingPin> craftingPins = Collections.emptyList();
    private int visibleCraftingPinRows;
    private int visiblePlayerPinRows;


    public Repo(final IScrollSource src, final ISortSource sortSrc) {
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
        // A craft's progress comes in a fresh snapshot every time it moves; the list only cares which keys are
        // pinned where, since those rows leave it.
        final boolean samePins = this.visibleCraftingPinRows == visibleCraftingRows
                && this.visiblePlayerPinRows == visiblePlayerRows
                && Arrays.equals(this.playerPins, Arrays.copyOf(playerPins, this.playerPins.length))
                && sameKeys(this.craftingPins, craftingPins);
        System.arraycopy(playerPins, 0, this.playerPins, 0,
                Math.min(playerPins.length, this.playerPins.length));
        this.craftingPins = new ArrayList<>(craftingPins);
        this.visibleCraftingPinRows = visibleCraftingRows;
        this.visiblePlayerPinRows = visiblePlayerRows;
        if (!samePins) {
            this.changed = true;
        }
    }

    private static boolean sameKeys(final List<TerminalCraftingPin> a, final List<TerminalCraftingPin> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).getWhat(), b.get(i).getWhat())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Replaces or removes the row for {@code entry.getWhat()}. {@link GridInventoryEntry#isMeaningful()}
     * false is the server telling us the row is gone - the old code expressed the same "gone" state as a
     * zero-and-craftable-false stack it left in the list; here we just drop the key.
     */
    public void postUpdate(final GridInventoryEntry entry) {
        final GridInventoryEntry old;
        if (!entry.isMeaningful()) {
            old = this.entries.remove(entry.getWhat());
            this.names.remove(entry.getWhat());
        } else {
            old = this.entries.put(entry.getWhat(), entry);
        }

        if (this.movesRows(old, entry)) {
            this.changed = true;
        } else {
            this.amountsChanged = true;
        }
    }

    /**
     * Whether the change can move a row, or bring one in or take it out. Sorted by amount, every change can;
     * sorted otherwise, only a key that comes or goes, or one that starts or stops being stocked or craftable,
     * which is what the view modes filter on.
     */
    private boolean movesRows(@Nullable final GridInventoryEntry old, final GridInventoryEntry now) {
        if (old == null || !now.isMeaningful()) {
            return true;
        }
        final Enum sortBy = this.sortSrc.getSortBy();
        if (sortBy != SortOrder.NAME && sortBy != SortOrder.MOD) {
            return true;
        }
        return old.isCraftable() != now.isCraftable()
                || (old.getStoredAmount() == 0) != (now.getStoredAmount() == 0);
    }

    /**
     * The server's row for {@code what}, or null when it has sent none. Unfiltered: the search box and the
     * sort order shape {@link #getReferenceItem(int)}, not this.
     */
    public GridInventoryEntry getEntry(final AEKey what) {
        return this.entries.get(what);
    }

    /**
     * Whether {@code what} is craftable through this network view - for the small "+" marker on a slot
     * that merely displays a key rather than stocking it, such as a pattern's crafting grid or output.
     */
    public boolean isCraftable(final AEKey what) {
        final GridInventoryEntry e = this.getEntry(what);
        return e != null && e.isCraftable();
    }

    /**
     * Whether the only patterns making {@code what} settle their jobs themselves, so a slot showing it
     * marks the craft apart from an ordinary one.
     */
    public boolean isFakeCraftable(final AEKey what) {
        final GridInventoryEntry e = this.getEntry(what);
        return e != null && e.isFakeCraftable();
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

        final Enum searchMode = AEClientConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
        if (lastSearchMode != searchMode) {
            resort = true;
            lastSearchMode = searchMode;
        }

        if (searchMode == SearchBoxMode.JEI_AUTOSEARCH || searchMode == SearchBoxMode.JEI_MANUAL_SEARCH) {
            this.updateJEI(this.search.getSearchString());
        }

        if (this.search.refresh()) {
            resort = true;
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

        if (!changed && !resort && amountsChanged) {
            amountsChanged = false;
            this.refreshViewAmounts();
        }

        if (changed || resort) {
            changed = false;
            resort = false;
            amountsChanged = false;

            view = new ArrayList<>();

            KeySorters.setDirection((appeng.api.config.SortDir) sortDir);
            KeySorters.init();

            final Comparator<GridInventoryEntry> c = getComparator(sortBy);

            boolean matched = false;
            final Set<AEKey> visiblePins = new HashSet<>();
            int craftingLimit = Math.min(craftingPins.size(), visibleCraftingPinRows * IPlayerTerminalPins.SLOTS_PER_ROW);
            for (int i = 0; i < craftingLimit; i++) {
                visiblePins.add(craftingPins.get(i).getWhat());
            }
            int playerLimit = Math.min(playerPins.length, visiblePlayerPinRows * IPlayerTerminalPins.SLOTS_PER_ROW);
            for (int i = 0; i < playerLimit; i++) {
                if (playerPins[i] != null) visiblePins.add(playerPins[i]);
            }

            for (final AEKey pinned : visiblePins) {
                matched |= this.search.matches(pinned);
            }

            for (final GridInventoryEntry entry : this.entries.values()) {
                if (!visiblePins.contains(entry.getWhat())) {
                    addEntry(entry, viewMode);
                }
            }

            // A pinned row stays on screen whatever the search says, so it does not reach the view - but it
            // is still something the query found, and the field must not go red while one is showing.
            // An empty query refuses nothing: an empty network is not a query that failed.
            this.anyMatches = matched || !this.view.isEmpty() || this.search.getSearchString().isEmpty();

            view.sort(c);
        }
    }

    /**
     * The same orders as {@link KeySorters}, compared on the cached names. Inventory Tweaks and BogoSorter
     * still go through theirs: they compare stacks, not names.
     */
    private Comparator<GridInventoryEntry> getComparator(Enum sortBy) {
        if (sortBy == SortOrder.INVTWEAKS) {
            final Comparator<Object2LongMap.Entry<AEKey>> c = InventoryBogoSortModule.isLoaded()
                    ? InventoryBogoSortModule.COMPARATOR
                    : KeySorters.CONFIG_BASED_SORT_BY_INV_TWEAKS;
            // KeySorters' comparators are shaped for iterating a KeyCounter (Object2LongMap.Entry<AEKey>);
            // this repo holds GridInventoryEntry rows instead, so adapt rather than touching the wave 1 file.
            return (a, b) -> c.compare(new KeyAmountEntry(a.getWhat(), a.getStoredAmount()), new KeyAmountEntry(b.getWhat(), b.getStoredAmount()));
        }

        final Comparator<GridInventoryEntry> byName = (a, b) -> this.nameOf(a.getWhat())
                .compareToIgnoreCase(this.nameOf(b.getWhat()));
        final Comparator<GridInventoryEntry> c;
        if (sortBy == SortOrder.MOD) {
            c = ((Comparator<GridInventoryEntry>) (a, b) -> a.getWhat().getModId()
                    .compareToIgnoreCase(b.getWhat().getModId())).thenComparing(byName);
        } else if (sortBy == SortOrder.AMOUNT) {
            c = ((Comparator<GridInventoryEntry>) (a, b) -> Long.compare(b.getStoredAmount(), a.getStoredAmount()))
                    .thenComparing(byName);
        } else {
            c = byName;
        }

        return (a, b) -> {
            int cmp = c.compare(a, b);
            if (cmp == 0) {
                cmp = KeySorters.identityTieBreak(a.getWhat(), b.getWhat());
            }
            return KeySorters.applyDirection(cmp);
        };
    }

    private String nameOf(final AEKey what) {
        return this.names.computeIfAbsent(what, Platform::getItemDisplayName);
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

    private void addEntry(GridInventoryEntry entry, final Enum viewMode) {
        if (this.myPartitionList != null && !this.myPartitionList.matches(entry.getWhat())) {
            return;
        }

        if (viewMode == ViewItems.CRAFTABLE && !entry.isCraftable()) {
            return;
        }

        if (viewMode == ViewItems.STORED && entry.getStoredAmount() == 0) {
            return;
        }

        if (!this.search.matches(entry.getWhat())) {
            return;
        }

        if (viewMode == ViewItems.CRAFTABLE) {
            entry = entry.withStoredAmount(0);
        }

        this.view.add(entry);
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

    /**
     * Empties the list outright - keys and all - unlike {@link #clear()}, which keeps the keys and zeroes
     * them. A screen that refills the same repo from a different source needs the old keys gone, or they
     * stay on show at zero.
     */
    public void reset() {
        this.entries.clear();
        this.names.clear();
        this.view = new ArrayList<>();
        this.changed = true;
    }

    public boolean hasPower() {
        return this.hasPower;
    }

    public void setPower(final boolean hasPower) {
        this.hasPower = hasPower;
    }

    public void setRowSize(final int rowSize) {
        this.rowSize = rowSize;
    }

    public String getSearchString() {
        return this.search.getSearchString();
    }

    public void setSearchString(@Nonnull final String searchString) {
        this.search.setSearchString(searchString);
    }

    /**
     * Whether the query found anything at all, pinned rows included. False only while a query is typed that
     * nothing answers - which is what turns the search field red.
     */
    public boolean hasMatches() {
        return this.anyMatches;
    }

    /**
     * Minimal adapter so {@link appeng.util.KeySorters}'s {@code Object2LongMap.Entry<AEKey>}-shaped
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
