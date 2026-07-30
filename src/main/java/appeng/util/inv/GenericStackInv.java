package appeng.util.inv;


import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;

import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;


/**
 * A fixed set of slots, each holding an amount of one {@link AEKey} of any type.
 * <p/>
 * This is the storage counterpart of {@link appeng.tile.inventory.AppEngInternalAEInventory}, which holds the
 * same thing but exists to be a <em>config</em>: its {@code IItemHandler} face hands out
 * {@link appeng.items.misc.WrappedGenericStack} placeholders so a filter can be edited in a GUI. That is
 * exactly wrong for real stock - a machine pulling items out of an interface must never be handed a display
 * shim - so the two views a real inventory needs are separate adapters over this class instead:
 * {@link GenericStackItemHandler} shows only the item slots and {@link GenericStackFluidHandler} only the
 * fluid ones, each speaking its own platform API and neither aware of the other.
 * <p/>
 * Per-slot capacity comes from {@link GenericSlotCapacities}, so a slot holds a stack of items or four
 * buckets of fluid without this class knowing which is which.
 */
public class GenericStackInv implements Iterable<GenericStack> {

    /**
     * Told whenever a slot changes, so a host can mark itself dirty and re-plan. Deliberately not
     * {@link IAEAppEngInventory}: that one reports changes as {@code ItemStack}s, which is the very thing a
     * generic inventory cannot express.
     */
    @FunctionalInterface
    public interface ChangeListener {
        void onSlotChanged(GenericStackInv inv, int slot);
    }

    /**
     * How much of a key one slot of <em>this</em> inventory holds. Defaults to {@link GenericSlotCapacities},
     * but an owner may hold more than the standard: the ME Interface has always stocked several stacks of an
     * item per slot, and that had to survive the move off {@code AppEngInternalOversizedInventory}.
     */
    @FunctionalInterface
    public interface SlotCapacity {
        long forKey(AEKey what);
    }

    @Nullable
    private final ChangeListener listener;
    private final SlotCapacity capacity;
    private final GenericStack[] slots;
    private boolean suppressListener = false;

    public GenericStackInv(@Nullable ChangeListener listener, int size) {
        this(listener, size, GenericSlotCapacities::get);
    }

    public GenericStackInv(@Nullable ChangeListener listener, int size, SlotCapacity capacity) {
        this.listener = listener;
        this.capacity = capacity;
        this.slots = new GenericStack[size];
    }

    public int size() {
        return this.slots.length;
    }

    @Nullable
    public GenericStack getStack(int slot) {
        return this.slots[slot];
    }

    @Nullable
    public AEKey getKey(int slot) {
        final GenericStack stack = this.slots[slot];
        return stack == null ? null : stack.what();
    }

    public long getAmount(int slot) {
        final GenericStack stack = this.slots[slot];
        return stack == null ? 0 : stack.amount();
    }

    /**
     * @return how much of {@code what} one slot can hold in total, regardless of what is in it now.
     */
    public long getCapacity(AEKey what) {
        return what == null ? 0 : Math.max(1, this.capacity.forKey(what));
    }

    public void setStack(int slot, @Nullable GenericStack stack) {
        // An amount of zero is not "a slot holding none of something", it is an empty slot. Storing it
        // would leave a key behind that every isEmpty()-style check would then have to know to ignore -
        // the same trap KeyCounter.reset() set (CONTRACT.md §9.1a).
        final GenericStack normalised = stack == null || stack.amount() <= 0 ? null : stack;
        if (java.util.Objects.equals(this.slots[slot], normalised)) {
            return;
        }
        this.slots[slot] = normalised;
        this.notifyChanged(slot);
    }

    public boolean isEmpty() {
        for (final GenericStack stack : this.slots) {
            if (stack != null) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        for (int slot = 0; slot < this.slots.length; slot++) {
            this.setStack(slot, null);
        }
    }

    /**
     * @return how much was accepted, which is zero when the slot holds a different key or is already full.
     */
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (what == null || amount <= 0) {
            return 0;
        }

        final GenericStack current = this.slots[slot];
        if (current != null && !current.what().equals(what)) {
            return 0;
        }

        final long held = current == null ? 0 : current.amount();
        final long room = this.getCapacity(what) - held;
        if (room <= 0) {
            return 0;
        }

        final long inserted = Math.min(room, amount);
        if (mode == Actionable.MODULATE) {
            this.setStack(slot, new GenericStack(what, held + inserted));
        }
        return inserted;
    }

    /**
     * Inserts into the first slot that will take it, filling partially used slots before empty ones so stock
     * does not scatter across the inventory.
     */
    public long insert(AEKey what, long amount, Actionable mode) {
        long remaining = amount;

        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            final boolean fillingExisting = pass == 0;
            for (int slot = 0; slot < this.slots.length && remaining > 0; slot++) {
                if (fillingExisting == (this.slots[slot] == null)) {
                    continue;
                }
                remaining -= this.insert(slot, what, remaining, mode);
            }
        }

        return amount - remaining;
    }

    public long extract(int slot, AEKey what, long amount, Actionable mode) {
        if (what == null || amount <= 0) {
            return 0;
        }

        final GenericStack current = this.slots[slot];
        if (current == null || !current.what().equals(what)) {
            return 0;
        }

        final long extracted = Math.min(current.amount(), amount);
        if (mode == Actionable.MODULATE) {
            this.setStack(slot, new GenericStack(what, current.amount() - extracted));
        }
        return extracted;
    }

    public long extract(AEKey what, long amount, Actionable mode) {
        long remaining = amount;
        for (int slot = 0; slot < this.slots.length && remaining > 0; slot++) {
            remaining -= this.extract(slot, what, remaining, mode);
        }
        return amount - remaining;
    }

    public void writeToNBT(NBTTagCompound target, String name) {
        final NBTTagCompound tag = new NBTTagCompound();
        for (int slot = 0; slot < this.slots.length; slot++) {
            if (this.slots[slot] == null) {
                continue;
            }
            final NBTTagCompound slotTag = new NBTTagCompound();
            GenericStack.writeTag(slotTag, this.slots[slot]);
            tag.setTag("#" + slot, slotTag);
        }
        target.setTag(name, tag);
    }

    public void readFromNBT(NBTTagCompound source, String name) {
        final NBTTagCompound tag = source.getCompoundTag(name);

        // One notification at the end rather than one per slot: a load is not a series of edits, and a host
        // that re-plans on every change would otherwise re-plan `size()` times while reading a saved world.
        this.suppressListener = true;
        try {
            for (int slot = 0; slot < this.slots.length; slot++) {
                this.setStack(slot, tag.hasKey("#" + slot) ? GenericStack.readTag(tag.getCompoundTag("#" + slot)) : null);
            }
        } finally {
            this.suppressListener = false;
        }

        this.notifyChanged(-1);
    }

    private void notifyChanged(int slot) {
        if (!this.suppressListener && this.listener != null) {
            this.listener.onSlotChanged(this, slot);
        }
    }

    /**
     * Iterates the non-empty slots only.
     */
    @Override
    public Iterator<GenericStack> iterator() {
        return new Iterator<GenericStack>() {
            private int next = this.advance(0);

            private int advance(int from) {
                int i = from;
                while (i < GenericStackInv.this.slots.length && GenericStackInv.this.slots[i] == null) {
                    i++;
                }
                return i;
            }

            @Override
            public boolean hasNext() {
                return this.next < GenericStackInv.this.slots.length;
            }

            @Override
            public GenericStack next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                }
                final GenericStack stack = GenericStackInv.this.slots[this.next];
                this.next = this.advance(this.next + 1);
                return stack;
            }
        };
    }
}
