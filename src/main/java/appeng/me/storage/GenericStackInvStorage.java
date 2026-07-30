package appeng.me.storage;


import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.util.inv.GenericStackInv;


/**
 * Exposes a {@link GenericStackInv} to the network as an {@link MEStorage}.
 * <p/>
 * The type-agnostic counterpart of {@link MEMonitorIInventory}, which can only ever show items because it
 * speaks to an {@code InventoryAdaptor}. Nothing here inspects a key: whether a slot ends up holding an item,
 * a fluid or something an addon registered is entirely the inventory's business.
 * <p/>
 * Not an {@link ITickingMonitor}: that exists so an <em>external</em> inventory, which can change without
 * telling anyone, can be polled for differences. A {@link GenericStackInv} reports its own changes, so its
 * owner pushes them instead of the network pulling them.
 */
public class GenericStackInvStorage implements MEStorage {

    private final GenericStackInv inv;
    private final ITextComponent description;

    public GenericStackInvStorage(GenericStackInv inv, ITextComponent description) {
        this.inv = inv;
        this.description = description;
    }

    public GenericStackInvStorage(GenericStackInv inv, String description) {
        this(inv, new TextComponentString(description));
    }

    protected final GenericStackInv getInv() {
        return this.inv;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return this.inv.insert(what, amount, mode);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return this.inv.extract(what, amount, mode);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (final GenericStack stack : this.inv) {
            out.add(stack.what(), stack.amount());
        }
    }

    @Override
    public ITextComponent getDescription() {
        return this.description;
    }
}
