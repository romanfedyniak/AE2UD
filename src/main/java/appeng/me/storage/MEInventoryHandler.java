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


import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.util.prioritylist.DefaultPriorityList;
import appeng.util.prioritylist.IPartitionList;


/**
 * Wraps a {@link MEStorage} with a priority, an insertion/extraction whitelist or blacklist and a few other
 * per-mount options (storage buses, drive cells).
 * <p/>
 * Replaces the generic {@code IMEInventoryHandler<T>}/{@code MEInventoryHandler<T>} pair. Priority itself is no
 * longer tracked here - {@link NetworkStorage#mount(int, MEStorage)} takes it directly.
 * <p/>
 * <b>Sticky Card (AE2UD-specific, not in AE2-original — see CONTRACT.md §10):</b> {@link #isSticky()}/
 * {@link #setSticky(boolean)} restore the flag that used to live on the old generic {@code MEInventoryHandler<T>}.
 * It has no equivalent on the {@link MEStorage} interface itself (deliberately - it is not part of the frozen
 * {@code src/api} contract), so callers that need to special-case sticky mounts do an
 * {@code instanceof MEInventoryHandler} check; see {@link NetworkStorage#insert} for the one place that matters.
 */
public class MEInventoryHandler extends DelegatingMEInventory {

    private IPartitionList partitionList = DefaultPriorityList.INSTANCE;
    private IncludeExclude partitionListMode = IncludeExclude.WHITELIST;
    private boolean filterOnExtraction;
    private boolean filterAvailableContents;
    private boolean allowExtraction = true;
    private boolean allowInsertion = true;
    private boolean voidOverflow;
    private boolean sticky;

    private boolean gettingAvailableContent = false;

    public MEInventoryHandler(final MEStorage inventory) {
        super(inventory);
    }

    public void setAllowExtraction(final boolean allowExtraction) {
        this.allowExtraction = allowExtraction;
    }

    public void setAllowInsertion(final boolean allowInsertion) {
        this.allowInsertion = allowInsertion;
    }

    protected IncludeExclude getWhitelist() {
        return this.partitionListMode;
    }

    public void setWhitelist(final IncludeExclude myWhitelist) {
        this.partitionListMode = myWhitelist;
    }

    protected IPartitionList getPartitionList() {
        return this.partitionList;
    }

    public void setPartitionList(final IPartitionList myPartitionList) {
        this.partitionList = myPartitionList;
    }

    public void setExtractFiltering(final boolean filterOnExtraction, final boolean filterAvailableContents) {
        this.filterOnExtraction = filterOnExtraction;
        this.filterAvailableContents = filterAvailableContents;
    }

    public void setVoidOverflow(final boolean voidOverflow) {
        this.voidOverflow = voidOverflow;
    }

    /**
     * @return true if this mount has the Sticky Card installed (or otherwise opted into sticky semantics). See
     *         {@link NetworkStorage#insert} for what this actually changes.
     */
    public boolean isSticky() {
        return this.sticky;
    }

    public void setSticky(final boolean sticky) {
        this.sticky = sticky;
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (!this.allowInsertion || !this.passesBlackOrWhitelist(what)) {
            return 0;
        }

        final long inserted = super.insert(what, amount, mode, source);
        return this.voidOverflow ? amount : inserted;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (this.filterOnExtraction && !this.canExtract(what)) {
            return 0;
        }

        return super.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        if (this.gettingAvailableContent) {
            // Prevent recursion in case the internal inventory somehow calls this when the available items are
            // queried. This is normally handled by NetworkStorage when the initial query comes from the network,
            // but this method might also be called directly (e.g. by storage bus code), so guard here as well.
            return;
        }

        this.gettingAvailableContent = true;
        try {
            if (!this.filterAvailableContents) {
                super.getAvailableStacks(out);
            } else {
                if (!this.allowExtraction) {
                    return;
                }

                for (final var entry : this.getDelegate().getAvailableStacks()) {
                    if (this.canExtract(entry.getKey())) {
                        out.add(entry.getKey(), entry.getLongValue());
                    }
                }
            }
        } finally {
            this.gettingAvailableContent = false;
        }
    }

    @Override
    public boolean isPreferredStorageFor(final AEKey input, final IActionSource source) {
        if (this.partitionListMode == IncludeExclude.WHITELIST && this.partitionList.isListed(input)) {
            return true;
        }

        // Inventories that already contain some equal stack are also preferred; a simulated extraction of size 1
        // is enough to check, and avoids forcing sub-inventories to compute a larger simulated extraction.
        if (super.extract(input, 1, Actionable.SIMULATE, source) > 0) {
            return true;
        }

        return super.isPreferredStorageFor(input, source);
    }

    protected boolean canExtract(final AEKey request) {
        return this.allowExtraction && this.passesBlackOrWhitelist(request);
    }

    // Applies the black/whitelist, but only if any item is listed at all.
    private boolean passesBlackOrWhitelist(final AEKey input) {
        return this.partitionList.matchesFilter(input, this.partitionListMode);
    }
}
