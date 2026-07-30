package appeng.util.inv;


import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;


/**
 * Fluid counterpart of {@link NetworkFirstItemHandler}: what a machine pumps into the interface goes to the
 * <em>network</em> first, and only what the network refuses stays in a slot.
 * <p/>
 * Draining is deliberately local-only, exactly as the deleted {@code AENetworkFluidInventory} was: an
 * interface hands out what it was configured to stock, not the whole network. The asymmetry is the point -
 * it is what makes an interface a buffer rather than a pipe into storage.
 */
public class NetworkFirstFluidHandler implements IFluidHandler {

    private final IFluidHandler local;
    private final Supplier<IStorageService> network;
    private final IActionSource source;

    public NetworkFirstFluidHandler(IFluidHandler local, Supplier<IStorageService> network, IActionSource source) {
        this.local = local;
        this.network = network;
        this.source = source;
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        return this.local.getTankProperties();
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) {
            return 0;
        }

        final AEFluidKey key = AEFluidKey.of(resource);
        final IStorageService storage = this.network.get();
        if (key == null || storage == null) {
            return this.local.fill(resource, doFill);
        }

        final long inserted = storage.getInventory().insert(key, resource.amount,
                doFill ? Actionable.MODULATE : Actionable.SIMULATE, this.source);

        if (inserted <= 0) {
            return this.local.fill(resource, doFill);
        }
        if (inserted >= resource.amount) {
            return resource.amount;
        }

        return (int) inserted + this.local.fill(key.toStack((int) (resource.amount - inserted)), doFill);
    }

    @Nullable
    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        return this.local.drain(resource, doDrain);
    }

    @Nullable
    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return this.local.drain(maxDrain, doDrain);
    }
}
