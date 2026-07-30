package appeng.fluids.parts;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * {@link ContainerItemStrategy} for {@link appeng.api.stacks.AEKeyType#fluids()}: buckets, tanks, and anything
 * else exposing Forge's {@code FLUID_HANDLER_ITEM_CAPABILITY}.
 * <p/>
 * This is where the fill/empty logic that used to be copy-pasted into {@code ContainerFluidTerminal},
 * {@code ContainerMEPortableFluidCell} and {@code ContainerFluidInterface} now lives, once.
 */
public class FluidContainerItemStrategy implements ContainerItemStrategy {

    @Nullable
    @Override
    public GenericStack getContainedStack(final ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        // On a single item: FluidUtil reads the capability off the stack as given, and a stack of four buckets
        // would otherwise report four buckets' worth from a handler that only ever fills one.
        final ItemStack one = stack.copy();
        one.setCount(1);

        final FluidStack contained = FluidUtil.getFluidContained(one);
        if (contained == null || contained.amount <= 0) {
            return null;
        }
        return new GenericStack(AEFluidKey.of(contained), contained.amount);
    }

    @Nullable
    @Override
    public Context openContext(final ItemStack container) {
        if (container.isEmpty()) {
            return null;
        }

        final ItemStack one = container.copy();
        one.setCount(1);

        final IFluidHandlerItem handler = FluidUtil.getFluidHandler(one);
        return handler == null ? null : new FluidContext(handler);
    }

    private static final class FluidContext implements Context {

        private final IFluidHandlerItem handler;

        private FluidContext(final IFluidHandlerItem handler) {
            this.handler = handler;
        }

        @Override
        public long insert(final AEKey what, final long amount, final Actionable mode) {
            if (!(what instanceof AEFluidKey fluidKey) || amount <= 0) {
                return 0;
            }
            final int toFill = (int) Math.min(amount, Integer.MAX_VALUE);
            return this.handler.fill(fluidKey.toStack(toFill), mode == Actionable.MODULATE);
        }

        @Override
        public long extract(final AEKey what, final long amount, final Actionable mode) {
            if (!(what instanceof AEFluidKey fluidKey) || amount <= 0) {
                return 0;
            }
            final int toDrain = (int) Math.min(amount, Integer.MAX_VALUE);
            // Drain by stack, not by amount: a container holding several fluids must not hand back a
            // different one than the caller asked for.
            final FluidStack drained = this.handler.drain(fluidKey.toStack(toDrain), mode == Actionable.MODULATE);
            return drained == null ? 0 : drained.amount;
        }

        @Nullable
        @Override
        public GenericStack getExtractableContent() {
            final FluidStack drainable = this.handler.drain(Integer.MAX_VALUE, false);
            if (drainable == null || drainable.amount <= 0) {
                return null;
            }
            return new GenericStack(AEFluidKey.of(drainable), drainable.amount);
        }

        @Override
        public ItemStack getContainer() {
            return this.handler.getContainer();
        }
    }
}
