package appeng.util.inv;


import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;


/**
 * An item view of a {@link GenericStackInv} that sends anything pushed into it to the <em>network</em> first,
 * and only keeps what the network would not take.
 * <p/>
 * This is the fork-specific behaviour {@code AppEngNetworkInventory} gave the ME Interface: a machine that
 * pushes its output into an interface has it land in storage rather than filling the interface's nine slots
 * and jamming. Preserved verbatim, only retyped - the previous implementation was an
 * {@code AppEngInternalOversizedInventory} of {@code ItemStack}s and therefore item-only by construction.
 */
public class NetworkFirstItemHandler implements IItemHandlerModifiable {

    private final IItemHandlerModifiable local;
    private final Supplier<IStorageService> network;
    private final IActionSource source;

    public NetworkFirstItemHandler(IItemHandlerModifiable local, Supplier<IStorageService> network,
            IActionSource source) {
        this.local = local;
        this.network = network;
        this.source = source;
    }

    @Override
    public int getSlots() {
        return this.local.getSlots();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.local.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        this.local.setStackInSlot(slot, stack);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final AEItemKey what = AEItemKey.of(stack);
        final IStorageService storage = this.network.get();
        if (what == null || storage == null) {
            return this.local.insertItem(slot, stack, simulate);
        }

        final long inserted = storage.getInventory().insert(what, stack.getCount(),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, this.source);

        if (inserted <= 0) {
            return this.local.insertItem(slot, stack, simulate);
        }
        if (inserted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }

        // The network took part of it; the rest falls back to the interface's own slot.
        return this.local.insertItem(slot, what.toStack((int) (stack.getCount() - inserted)), simulate);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return this.local.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.local.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @Nullable @Nonnull ItemStack stack) {
        return this.local.isItemValid(slot, stack);
    }
}
