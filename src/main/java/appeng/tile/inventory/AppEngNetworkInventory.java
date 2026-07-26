package appeng.tile.inventory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.wrapper.RangedWrapper;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class AppEngNetworkInventory extends AppEngInternalOversizedInventory {

    private final Supplier<IStorageService> supplier;
    private final IActionSource source;

    public AppEngNetworkInventory(Supplier<IStorageService> networkSupplier, IActionSource source, IAEAppEngInventory inventory, int size, int maxStack) {
        super(inventory, size, maxStack);
        this.supplier = networkSupplier;
        this.source = source;
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        IStorageService storage = supplier.get();
        AEItemKey what = AEItemKey.of(stack);
        if (storage != null && what != null) {
            long originAmt = stack.getCount();
            MEStorage dest = storage.getInventory();
            long inserted = dest.insert(what, originAmt, simulate ? Actionable.SIMULATE : Actionable.MODULATE, this.source);

            if (inserted <= 0) {
                return super.insertItem(slot, stack, simulate);
            } else if (inserted < originAmt) {
                if (!simulate) {
                    ItemStack added = stack.copy();
                    added.setCount((int) inserted);
                    this.getTileEntity().onChangeInventory(this, slot, InvOperation.INSERT, ItemStack.EMPTY, added);
                }
                return what.toStack((int) (originAmt - inserted));
            } else {
                if (!simulate) {
                    this.getTileEntity().onChangeInventory(this, slot, InvOperation.INSERT, ItemStack.EMPTY, stack);
                }
                return ItemStack.EMPTY;
            }
        } else {
            return super.insertItem(slot, stack, simulate);
        }
    }

    @Nonnull
    private ItemStack insertToBuffer(int slot, @Nonnull ItemStack stack, boolean simulate) {
        return super.insertItem(slot, stack, simulate);
    }

    public RangedWrapper getBufferWrapper(int selectSlot) {
        return new RangedWrapper(this, selectSlot, selectSlot + 1) {
            @Override
            @Nonnull
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                if (slot == 0) {
                    return AppEngNetworkInventory.this.insertToBuffer(selectSlot, stack, simulate);
                }
                return stack;
            }
        };
    }

}
