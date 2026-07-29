package appeng.fluids.parts;

import javax.annotation.Nullable;

import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.MEStorage;
import appeng.util.prioritylist.IPartitionList;

/**
 * Fluid-bus concrete implementation of the frozen {@link StackTransferContext}, mirroring
 * {@code appeng.parts.automation.StackTransferContextImpl} (package-private there, so it cannot be reused directly
 * from this package -- see that class's javadoc for why a concrete implementation needs extra, non-frozen
 * accessors). {@link #getPartitionList()}, {@link #getFuzzyMode()} and {@link #getEnergySource()} are exactly the
 * same kind of addition: nothing on the frozen {@link StackTransferContext} interface changed, this is purely an
 * implementation detail {@link FluidImportStrategy}/{@link FluidExportStrategy} need to replicate the pre-port
 * {@code PartFluidImportBus}/{@code PartFluidExportBus} filter and power-charging behaviour.
 * <p/>
 * Package-private: only the fluid strategies and bus parts in this package ever see the concrete type.
 */
final class FluidTransferContext implements StackTransferContext {

    private final MEStorage internalStorage;
    private final IEnergySource energySource;
    private final IActionSource actionSource;
    private final IPartitionList filter;
    @Nullable
    private final FuzzyMode fuzzyMode;
    private final int initialOperations;
    private int operationsRemaining;

    FluidTransferContext(MEStorage internalStorage, IEnergySource energySource, IActionSource actionSource,
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
        return what -> this.filter.isEmpty() || this.filter.isListed(what);
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
        return this.operationsRemaining > 0;
    }

    /**
     * @return true if any part of the per-tick budget was actually spent. Used by the bus parts to pick a
     *         {@code TickRateModulation}, matching the pre-port {@code doBusWork()}'s return values.
     */
    boolean hasDoneWork() {
        return this.initialOperations > this.operationsRemaining;
    }

    /**
     * The bus' configured fluids, still as an {@link IPartitionList} rather than the frozen {@link AEKeyFilter},
     * because {@link FluidImportStrategy} needs to enumerate (not just test) the configured keys.
     */
    IPartitionList getPartitionList() {
        return this.filter;
    }

    /**
     * Non-null only when the bus has a {@code Upgrades.FUZZY} card installed.
     */
    @Nullable
    FuzzyMode getFuzzyMode() {
        return this.fuzzyMode;
    }

    /**
     * Not part of the frozen contract (see class javadoc) -- needed to keep charging AE power for network access,
     * a mechanic the pre-port bus classes had via {@code Platform.poweredInsert}/{@code poweredExtraction}.
     */
    IEnergySource getEnergySource() {
        return this.energySource;
    }
}
