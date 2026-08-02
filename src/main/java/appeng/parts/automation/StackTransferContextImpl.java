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

package appeng.parts.automation;

import javax.annotation.Nullable;

import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.MEStorage;
import appeng.util.prioritylist.IPartitionList;

/**
 * Concrete implementation of the frozen {@link StackTransferContext} shared by the import bus, the
 * export bus and (indirectly, through the strategies) crafting-triggered pushes.
 * <p>
 * AE2UD's {@code StackTransferContext} (see CONTRACT.md &sect;3) is intentionally smaller than
 * AE2-original's: it has no {@code getEnergySource()}, {@code isInFilter()} or {@code
 * isKeyTypeEnabled()}. Those are still needed by the item strategies in this package (to charge
 * power for network access and to enumerate configured filter keys), so they are exposed here as
 * package-private extras beyond the frozen interface -- exactly the pattern earlier waves used to
 * restore mechanics that don't fit the frozen shape verbatim (see CONTRACT.md &sect;10, "Restored
 * regressions"). {@link StorageImportStrategy}/{@link StorageExportStrategy} cast to this concrete
 * type to reach them; nothing outside this package uses anything beyond the constructor.
 * <p>
 * Public only so the legacy fluid bus parts in {@code appeng.fluids.parts} can build one too. They used to
 * carry a byte-for-byte copy of this class, and that duplicate was the whole reason
 * {@code FluidImportStrategy} silently moved nothing on the generic import bus: the strategy tested the
 * context's concrete type, and the generic bus builds this one. One implementation, no such test possible.
 */
public final class StackTransferContextImpl implements StackTransferContext {

    private final MEStorage internalStorage;
    private final IEnergySource energySource;
    private final IActionSource actionSource;
    private final IPartitionList filter;
    @Nullable
    private final FuzzyMode fuzzyMode;
    private final int initialOperations;
    private int operationsRemaining;
    private boolean inverted;

    public StackTransferContextImpl(MEStorage internalStorage, IEnergySource energySource, IActionSource actionSource,
            int operationsRemaining, IPartitionList filter, @Nullable FuzzyMode fuzzyMode) {
        this.internalStorage = internalStorage;
        this.energySource = energySource;
        this.actionSource = actionSource;
        this.initialOperations = operationsRemaining;
        this.operationsRemaining = operationsRemaining;
        this.filter = filter;
        this.fuzzyMode = fuzzyMode;
    }

    @Override
    public MEStorage getInternalStorage() {
        return this.internalStorage;
    }

    @Override
    public IActionSource getActionSource() {
        return this.actionSource;
    }

    @Override
    public AEKeyFilter getFilter() {
        final IncludeExclude mode = this.isInverted() ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        return what -> this.filter.matchesFilter(what, mode);
    }

    void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    boolean isInverted() {
        // Like modern AE2, an inverter card does not turn an empty filter into "import nothing".
        return !this.filter.isEmpty() && this.inverted;
    }

    @Override
    public int getOperationsRemaining() {
        return this.operationsRemaining;
    }

    @Override
    public void setOperationsRemaining(int operationsRemaining) {
        this.operationsRemaining = operationsRemaining;
    }

    @Override
    public void reduceOperationsRemaining(long amount) {
        this.operationsRemaining -= (int) amount;
    }

    @Override
    public boolean hasOperationsLeft() {
        return this.operationsRemaining > 0;
    }

    @Override
    public boolean hasRegularOperationsLeft() {
        // AE2UD's frozen interface doesn't spell out the distinction from hasOperationsLeft() any
        // further (no prior wave gave it a meaning); operations are already whole "at least one
        // amount-per-operation unit" costs, so a "regular" operation is the same as any operation.
        return this.operationsRemaining > 0;
    }

    /**
     * @return true if any operation budget was actually spent this tick. Used by the bus parts to
     *         pick a {@code TickRateModulation}, matching the pre-port {@code doBusWork()}'s
     *         {@code worked}/{@code didSomething} flags.
     */
    boolean hasDoneWork() {
        return this.initialOperations > this.operationsRemaining;
    }

    /**
     * The bus' configured keys, still as an {@link IPartitionList} rather than the frozen
     * {@link AEKeyFilter}, because the item strategies need to enumerate (not just test) the
     * configured keys to replicate the pre-port per-slot import/export loop.
     */
    IPartitionList getPartitionList() {
        return this.filter;
    }

    /**
     * Non-null only when the bus has a fuzzy card installed. Drives whether the
     * item strategies use exact or fuzzy extraction/matching.
     */
    @Nullable
    FuzzyMode getFuzzyMode() {
        return this.fuzzyMode;
    }

    /**
     * Not part of the frozen contract (see class javadoc) -- needed to keep charging AE power for
     * network access, a mechanic the pre-port bus classes had via {@code Platform.poweredInsert}/
     * {@code poweredExtraction} and that upstream AE2-original also has (through its own, richer
     * {@code StackTransferContext.getEnergySource()}).
     */
    IEnergySource getEnergySource() {
        return this.energySource;
    }
}
