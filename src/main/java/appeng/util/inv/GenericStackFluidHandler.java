package appeng.util.inv;


import javax.annotation.Nullable;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;


/**
 * The {@code IFluidHandler} face of a {@link GenericStackInv}: the slots that hold fluids, as tanks.
 * <p/>
 * Sibling of {@link GenericStackItemHandler} and the same rule applies in reverse - a slot holding anything
 * that is not a fluid reads as an empty tank and refuses everything, so a pipe cannot drain an interface's
 * item slot.
 */
public class GenericStackFluidHandler implements IFluidHandler {

    private final GenericStackInv inv;

    public GenericStackFluidHandler(GenericStackInv inv) {
        this.inv = inv;
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        final IFluidTankProperties[] properties = new IFluidTankProperties[this.inv.size()];
        for (int slot = 0; slot < properties.length; slot++) {
            properties[slot] = new SlotProperties(slot);
        }
        return properties;
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) {
            return 0;
        }

        final AEFluidKey key = AEFluidKey.of(resource);
        if (key == null) {
            return 0;
        }

        final Actionable mode = doFill ? Actionable.MODULATE : Actionable.SIMULATE;

        long filled = 0;
        for (int slot = 0; slot < this.inv.size() && filled < resource.amount; slot++) {
            if (!this.acceptsFluidIn(slot)) {
                continue;
            }
            filled += this.inv.insert(slot, key, resource.amount - filled, mode);
        }
        return (int) filled;
    }

    @Nullable
    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) {
            return null;
        }

        final AEFluidKey key = AEFluidKey.of(resource);
        if (key == null) {
            return null;
        }

        final long drained = this.inv.extract(key, resource.amount, doDrain ? Actionable.MODULATE : Actionable.SIMULATE);
        return drained <= 0 ? null : key.toStack((int) drained);
    }

    @Nullable
    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) {
            return null;
        }

        // Untyped drain: hand back whichever fluid comes first, the way a multi-tank block does.
        for (int slot = 0; slot < this.inv.size(); slot++) {
            if (this.inv.getKey(slot) instanceof AEFluidKey key) {
                return this.drain(key.toStack(maxDrain), doDrain);
            }
        }
        return null;
    }

    /**
     * @return false when the slot already holds a non-fluid key, so a fluid cannot displace an item.
     */
    private boolean acceptsFluidIn(int slot) {
        final AEKey key = this.inv.getKey(slot);
        return key == null || key instanceof AEFluidKey;
    }

    private final class SlotProperties implements IFluidTankProperties {

        private final int slot;

        private SlotProperties(int slot) {
            this.slot = slot;
        }

        @Nullable
        @Override
        public FluidStack getContents() {
            final GenericStack stack = GenericStackFluidHandler.this.inv.getStack(this.slot);
            if (stack == null || !(stack.what() instanceof AEFluidKey key)) {
                return null;
            }
            return key.toStack((int) Math.min(stack.amount(), Integer.MAX_VALUE));
        }

        @Override
        public int getCapacity() {
            return (int) Math.min(GenericSlotCapacities.get(AEKeyType.fluids()), Integer.MAX_VALUE);
        }

        @Override
        public boolean canFill() {
            return GenericStackFluidHandler.this.acceptsFluidIn(this.slot);
        }

        @Override
        public boolean canDrain() {
            return GenericStackFluidHandler.this.inv.getKey(this.slot) instanceof AEFluidKey;
        }

        @Override
        public boolean canFillFluidType(FluidStack fluidStack) {
            return this.canFill();
        }

        @Override
        public boolean canDrainFluidType(FluidStack fluidStack) {
            return AEFluidKey.matches(GenericStackFluidHandler.this.inv.getKey(this.slot), fluidStack);
        }
    }
}
