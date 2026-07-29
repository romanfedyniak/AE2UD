package appeng.fluids.util;


import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.core.AELog;
import appeng.util.Platform;
import appeng.util.inv.InvOperation;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;


/**
 * A fixed-size array of fluid config/storage slots. Retyped from the deleted {@code IAEFluidStack} to
 * {@link GenericStack} (CONTRACT.md §9, "Wave 5 prerequisites"). An empty slot is still {@code null}.
 * <p/>
 * Constructors and {@link #readFromNBT}/{@link #writeToNBT} are pinned by already-committed callers
 * ({@code appeng.parts.AEBasePart}, {@code appeng.tile.AEBaseTile}, {@code appeng.client.me.ClientDCInternalFluidInv},
 * {@code appeng.container.implementations.ContainerFluidInterfaceConfigurationTerminal}) - do not change their
 * shape.
 */
public class AEFluidInventory implements IAEFluidTank {
    private final GenericStack[] fluids;
    protected final IAEFluidInventory handler;
    private int capacity;
    private IFluidTankProperties[] props = null;

    public AEFluidInventory(@Nullable final IAEFluidInventory handler, final int slots, final int capcity) {
        this.fluids = new GenericStack[slots];
        this.handler = handler;
        this.capacity = capcity;
    }

    public AEFluidInventory(@Nullable final IAEFluidInventory handler, final int slots) {
        this(handler, slots, Integer.MAX_VALUE);
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void setFluidInSlot(final int slot, @Nullable final GenericStack fluid) {
        if (slot < 0 || slot >= this.getSlots()) {
            return;
        }

        final GenericStack current = this.fluids[slot];

        // CONTRACT.md §9.1: the old IAEFluidStack.equals() ignored the amount ("is this the same fluid");
        // GenericStack.equals() compares the amount too, so "same fluid" has to be spelled out on the keys
        // rather than on the whole stacks, or an amount-only change would wrongly fall into the
        // remove-and-replace branch below and fire both callback parameters instead of just "added".
        final boolean sameFluid = current == null ? fluid == null : (fluid != null && current.what().equals(fluid.what()));

        if (sameFluid) {
            if (fluid != null && fluid.amount() != current.amount()) {
                this.fluids[slot] = fluid;
                this.onContentChanged(slot, InvOperation.SET, toFluidStack(fluid), null);
            }
            // else: identical key and amount (or both empty slots) - genuinely nothing changed
        } else if (fluid == null) {
            final GenericStack removed = current;
            this.fluids[slot] = null;
            this.onContentChanged(slot, InvOperation.SET, null, toFluidStack(removed));
        } else {
            final GenericStack removed = current;
            this.fluids[slot] = fluid;
            this.onContentChanged(slot, InvOperation.SET, toFluidStack(fluid), toFluidStack(removed));
        }
    }

    private void onContentChanged(final int slot, InvOperation operation, FluidStack added, FluidStack removed) {
        if (this.handler != null && Platform.isServer()) {
            this.handler.onFluidInventoryChanged(this, slot, operation, added, removed);
        }
    }

    @Override
    @Nullable
    public GenericStack getFluidInSlot(final int slot) {
        if (slot >= 0 && slot < this.getSlots()) {
            return this.fluids[slot];
        }
        return null;
    }

    @Override
    public int getSlots() {
        return this.fluids.length;
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        if (this.props == null) {
            this.props = new IFluidTankProperties[this.getSlots()];
            for (int i = 0; i < this.getSlots(); ++i) {
                this.props[i] = new FluidTankPropertiesWrapper(i);
            }

        }
        return this.props;
    }

    public int fill(final int slot, final FluidStack resource, final boolean doFill) {
        if (resource == null || resource.amount <= 0) {
            return 0;
        }

        final GenericStack fluid = this.fluids[slot];

        if (fluid != null && !AEFluidKey.matches(fluid.what(), resource)) {
            return 0;
        }

        int amountToStore = this.capacity;

        if (fluid != null) {
            amountToStore -= fluid.amount();
        }

        amountToStore = Math.min(amountToStore, resource.amount);

        if (doFill) {
            if (fluid == null) {
                final AEFluidKey key = AEFluidKey.of(resource);
                this.setFluidInSlot(slot, key == null ? null : new GenericStack(key, amountToStore));
            } else {
                this.fluids[slot] = new GenericStack(fluid.what(), fluid.amount() + amountToStore);
                this.onContentChanged(slot, InvOperation.INSERT, resource, null);
            }
        }

        return amountToStore;
    }

    public FluidStack drain(final int slot, final FluidStack resource, final boolean doDrain) {
        final GenericStack fluid = this.fluids[slot];
        if (resource == null || fluid == null || !AEFluidKey.matches(fluid.what(), resource)) {
            return null;
        }
        return this.drain(slot, resource.amount, doDrain);
    }

    public FluidStack drain(final int slot, final int maxDrain, boolean doDrain) {
        final GenericStack fluid = this.fluids[slot];
        if (fluid == null || maxDrain <= 0 || !(fluid.what() instanceof AEFluidKey fluidKey)) {
            return null;
        }

        int drained = maxDrain;
        if (fluid.amount() < drained) {
            drained = (int) fluid.amount();
        }

        final FluidStack stack = fluidKey.toStack(drained);
        if (doDrain) {
            final long remaining = fluid.amount() - drained;
            this.fluids[slot] = remaining <= 0 ? null : new GenericStack(fluidKey, remaining);
            this.onContentChanged(slot, InvOperation.EXTRACT, null, fluidKey.toStack(drained));
        }
        return stack;
    }

    @Override
    public int fill(final FluidStack fluid, final boolean doFill) {
        if (fluid == null || fluid.amount <= 0) {
            return 0;
        }

        final FluidStack insert = fluid.copy();

        int totalFillAmount = 0;
        for (int slot = 0; slot < this.getSlots(); ++slot) {
            int fillAmount = this.fill(slot, insert, doFill);
            totalFillAmount += fillAmount;
            insert.amount -= fillAmount;
            if (insert.amount <= 0) {
                break;
            }
        }
        return totalFillAmount;
    }

    @Override
    public FluidStack drain(final FluidStack fluid, final boolean doDrain) {
        if (fluid == null || fluid.amount <= 0) {
            return null;
        }

        final FluidStack resource = fluid.copy();

        FluidStack totalDrained = null;
        for (int slot = 0; slot < this.getSlots(); ++slot) {
            FluidStack drain = this.drain(slot, resource, doDrain);
            if (drain != null) {
                if (totalDrained == null) {
                    totalDrained = drain;
                } else {
                    totalDrained.amount += drain.amount;
                }

                resource.amount -= drain.amount;
                if (resource.amount <= 0) {
                    break;
                }
            }
        }
        return totalDrained;
    }

    @Override
    public FluidStack drain(final int maxDrain, final boolean doDrain) {
        if (maxDrain == 0) {
            return null;
        }

        FluidStack totalDrained = null;
        int toDrain = maxDrain;

        for (int slot = 0; slot < this.getSlots(); ++slot) {
            if (totalDrained == null) {
                totalDrained = this.drain(slot, toDrain, doDrain);
                if (totalDrained != null) {
                    toDrain -= totalDrained.amount;
                }
            } else {
                FluidStack copy = totalDrained.copy();
                copy.amount = toDrain;
                FluidStack drain = this.drain(slot, copy, doDrain);
                if (drain != null) {
                    totalDrained.amount += drain.amount;
                    toDrain -= drain.amount;
                }
            }

            if (toDrain <= 0) {
                break;
            }
        }
        return totalDrained;
    }

    public void writeToNBT(final NBTTagCompound data, final String name) {
        final NBTTagCompound c = new NBTTagCompound();
        this.writeToNBT(c);
        data.setTag(name, c);
    }

    private void writeToNBT(final NBTTagCompound target) {
        for (int x = 0; x < this.fluids.length; x++) {
            try {
                final NBTTagCompound c = new NBTTagCompound();
                GenericStack.writeTag(c, this.fluids[x]);
                target.setTag("#" + x, c);
            } catch (final Exception ignored) {
            }
        }
    }

    public void readFromNBT(final NBTTagCompound data, final String name) {
        final NBTTagCompound c = data.getCompoundTag(name);
        if (c != null) {
            this.readFromNBT(c);
        }
    }

    private void readFromNBT(final NBTTagCompound target) {
        for (int x = 0; x < this.fluids.length; x++) {
            try {
                if (target.hasKey("#" + x)) {
                    this.fluids[x] = GenericStack.readTag(target.getCompoundTag("#" + x));
                }
            } catch (final Exception e) {
                AELog.debug(e);
            }
        }
    }

    @Nullable
    private static FluidStack toFluidStack(@Nullable final GenericStack stack) {
        if (stack == null || !(stack.what() instanceof AEFluidKey fluidKey)) {
            return null;
        }
        return fluidKey.toStack((int) Math.min(stack.amount(), Integer.MAX_VALUE));
    }

    private class FluidTankPropertiesWrapper implements IFluidTankProperties {
        private final int slot;

        public FluidTankPropertiesWrapper(final int slot) {
            this.slot = slot;
        }

        @Override
        public FluidStack getContents() {
            return toFluidStack(AEFluidInventory.this.fluids[this.slot]);
        }

        @Override
        public int getCapacity() {
            return Math.min(AEFluidInventory.this.capacity, Integer.MAX_VALUE);
        }

        @Override
        public boolean canFill() {
            return true;
        }

        @Override
        public boolean canDrain() {
            return true;
        }

        @Override
        public boolean canFillFluidType(FluidStack fluidStack) {
            return true;
        }

        @Override
        public boolean canDrainFluidType(FluidStack fluidStack) {
            return fluidStack != null;
        }
    }
}
