package appeng.util.inv;


import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;


/**
 * The {@code IItemHandler} face of a {@link GenericStackInv}: the slots that hold items, as items.
 * <p/>
 * A slot holding something that is not an item reads as empty and refuses everything. That is deliberate and
 * it is the whole point of keeping this separate from the config inventory's item-handler face: an adjacent
 * machine pulling from an interface must see the fluid slot as empty, not as one
 * {@link appeng.items.misc.WrappedGenericStack} it would happily hopper away.
 */
public class GenericStackItemHandler implements IItemHandlerModifiable {

    private final GenericStackInv inv;

    public GenericStackItemHandler(GenericStackInv inv) {
        this.inv = inv;
    }

    @Override
    public int getSlots() {
        return this.inv.size();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        final GenericStack stack = this.inv.getStack(slot);
        if (stack == null || !(stack.what() instanceof AEItemKey itemKey)) {
            return ItemStack.EMPTY;
        }
        return itemKey.toStack((int) Math.min(stack.amount(), Integer.MAX_VALUE));
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            // Only clears an item slot. Blanking a fluid slot through the item view would be a machine
            // deleting stock it cannot even see.
            if (this.inv.getKey(slot) instanceof AEItemKey) {
                this.inv.setStack(slot, null);
            }
            return;
        }

        final AEItemKey key = AEItemKey.of(stack);
        if (key != null) {
            this.inv.setStack(slot, new GenericStack(key, stack.getCount()));
        }
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final AEItemKey key = AEItemKey.of(stack);
        if (key == null || !this.acceptsItemsIn(slot)) {
            return stack;
        }

        final long inserted = this.inv.insert(slot, key, stack.getCount(),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE);
        if (inserted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        return key.toStack((int) (stack.getCount() - inserted));
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }

        final AEKey key = this.inv.getKey(slot);
        if (!(key instanceof AEItemKey itemKey)) {
            return ItemStack.EMPTY;
        }

        final long extracted = this.inv.extract(slot, itemKey, Math.min(amount, itemKey.getMaxStackSize()),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE);
        return extracted <= 0 ? ItemStack.EMPTY : itemKey.toStack((int) extracted);
    }

    @Override
    public int getSlotLimit(int slot) {
        return (int) Math.min(GenericSlotCapacities.get(AEKeyType.items()), Integer.MAX_VALUE);
    }

    /**
     * @return false when the slot already holds a non-item key, so an item cannot displace a fluid.
     */
    private boolean acceptsItemsIn(int slot) {
        final AEKey key = this.inv.getKey(slot);
        return key == null || key instanceof AEItemKey;
    }
}
