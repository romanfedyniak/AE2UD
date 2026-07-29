package appeng.fluids.util;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.Supplier;

/**
 * The fluid-side counterpart of {@code appeng.tile.inventory.AppEngNetworkInventory}: a fluid interface's
 * own tanks, which try to push an incoming fill straight into the network before falling back to storing it
 * locally. Only {@link #fill} is network-aware, exactly as before - draining stays purely local, the same
 * asymmetry {@code AppEngNetworkInventory} has (it only overrides {@code insertItem}, not extraction).
 */
public class AENetworkFluidInventory extends AEFluidInventory {

    private final Supplier<IStorageService> supplier;
    private final IActionSource source;

    public AENetworkFluidInventory(Supplier<IStorageService> networkSupplier, IActionSource source, IAEFluidInventory handler, int slots, int capcity) {
        super(handler, slots, capcity);
        this.supplier = networkSupplier;
        this.source = source;
    }

    @Override
    public int fill(final FluidStack fluid, final boolean doFill) {
        if (fluid == null || fluid.amount <= 0) {
            return 0;
        }

        final IStorageService storage = this.supplier.get();
        if (storage == null) {
            return super.fill(fluid, doFill);
        }

        final AEFluidKey key = AEFluidKey.of(fluid);
        if (key == null) {
            return super.fill(fluid, doFill);
        }

        final int originAmt = fluid.amount;
        final MEStorage dest = storage.getInventory();
        // The old IMEInventory.injectItems returned the leftover (not-inserted) stack; MEStorage.insert
        // returns the amount actually inserted instead (CONTRACT.md §8 point 10 / the Platform.poweredInsert
        // rewrite follows the same leftover -> inserted translation).
        final long inserted = dest.insert(key, originAmt, doFill ? Actionable.MODULATE : Actionable.SIMULATE, this.source);

        if (inserted <= 0) {
            return super.fill(fluid, doFill);
        } else if (inserted < originAmt) {
            if (doFill) {
                final FluidStack added = fluid.copy();
                added.amount = (int) inserted;
                this.handler.onFluidInventoryChanged(this, added, null);
            }
            return (int) inserted;
        } else {
            if (doFill) {
                this.handler.onFluidInventoryChanged(this, fluid, null);
            }
            return originAmt;
        }
    }

}
