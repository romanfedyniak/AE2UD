package appeng.util.inv;


import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;


/**
 * A {@link GenericStackInv} seen through a GUI: every slot is visible, but only the item ones can be touched.
 * <p/>
 * {@link GenericStackItemHandler} is the face a <em>machine</em> gets, and it hides the non-item slots
 * entirely - a hopper must not see a fluid at all. A player looking at an interface has the opposite need:
 * the fluid it is stocking has to be on screen, or the machine looks broken. So this view wraps a non-item
 * key into a {@link appeng.items.misc.WrappedGenericStack} placeholder for display, and then refuses every
 * mutation of that slot, which is what stops the placeholder from being picked up - a vanilla slot decides
 * whether it can be taken by asking {@link #extractItem} for one.
 */
public class GenericStackDisplayHandler implements IItemHandlerModifiable {

    private final GenericStackInv inv;
    private final GenericStackItemHandler items;

    public GenericStackDisplayHandler(GenericStackInv inv) {
        this.inv = inv;
        this.items = new GenericStackItemHandler(inv);
    }

    private boolean isItemSlot(int slot) {
        final GenericStack stack = this.inv.getStack(slot);
        return stack == null || stack.what() instanceof AEItemKey;
    }

    @Override
    public int getSlots() {
        return this.inv.size();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        final GenericStack stack = this.inv.getStack(slot);
        if (stack == null) {
            return ItemStack.EMPTY;
        }
        if (stack.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack((int) Math.min(stack.amount(), Integer.MAX_VALUE));
        }
        // Carries the amount, so the slot's overlay draws "4B" rather than nothing - AEBaseGui resolves a
        // wrapped stack before sizing it.
        return GenericStack.wrapInItemStack(stack);
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        // Resolve, never AEItemKey.of. Vanilla syncs a container slot by sending its ItemStack and calling
        // putStack on the client, which lands here - so reading a placeholder as the item it looks like
        // wrote AEItemKey(WrappedGenericStack) into the client's copy of the inventory. The slot then held
        // a genuine item as far as every other check was concerned: it rendered as one, it could be picked
        // up, and a bucket could be swapped into it. The fluid only ever *looked* like an item on the
        // client, which is exactly how it was reported.
        this.inv.setStack(slot, GenericStack.resolveItemStack(stack));
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        return this.isItemSlot(slot) ? this.items.insertItem(slot, stack, simulate) : stack;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return this.isItemSlot(slot) ? this.items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.items.getSlotLimit(slot);
    }
}
