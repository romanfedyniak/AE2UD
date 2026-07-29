package appeng.fluids.helper;


import appeng.api.stacks.GenericStack;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketFluidSlot;
import appeng.fluids.util.AEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IContainerListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * Server-side change detection for a container's fluid config slots: keeps a shadow copy of the tank,
 * and ships whatever differs to the listening clients as a {@link PacketFluidSlot}.
 * <p/>
 * Retyped from {@code IAEFluidStack} to {@link GenericStack} along with {@link IAEFluidTank} and
 * {@code IFluidSyncContainer}; the packet was already migrated in wave 4.
 */
public class FluidSyncHelper {
    private final IAEFluidTank inv;
    private final IAEFluidTank cache;
    private final int idOffset;

    public FluidSyncHelper(final IAEFluidTank inv, final int idOffset) {
        this.inv = inv;
        this.cache = new AEFluidInventory(null, inv.getSlots());
        this.idOffset = idOffset;
    }

    public void sendFull(final Iterable<IContainerListener> listeners) {
        this.sendDiffMap(this.createDiffMap(true), listeners);
    }

    public void sendDiff(final Iterable<IContainerListener> listeners) {
        this.sendDiffMap(this.createDiffMap(false), listeners);
    }

    public void readPacket(final Map<Integer, GenericStack> data) {
        for (int i = 0; i < this.inv.getSlots(); ++i) {
            if (data.containsKey(i + this.idOffset)) {
                this.inv.setFluidInSlot(i, data.get(i + this.idOffset));
            }
        }
    }

    private void sendDiffMap(final Map<Integer, GenericStack> data, final Iterable<IContainerListener> listeners) {
        if (data.isEmpty()) {
            return;
        }

        for (final IContainerListener l : listeners) {
            if (l instanceof EntityPlayerMP) {
                NetworkHandler.instance().sendTo(new PacketFluidSlot(data), (EntityPlayerMP) l);
            }
        }
    }

    private Map<Integer, GenericStack> createDiffMap(final boolean full) {
        final Map<Integer, GenericStack> ret = new HashMap<>();
        for (int i = 0; i < this.inv.getSlots(); ++i) {
            if (full || !this.equalsSlot(i)) {
                ret.put(i + this.idOffset, this.inv.getFluidInSlot(i));
            }
            if (!full) {
                this.cache.setFluidInSlot(i, this.inv.getFluidInSlot(i));
            }
        }
        return ret;
    }

    /**
     * This is the <b>inverse</b> of the CONTRACT.md §9.1 hazard, and the collapse below is deliberate.
     * The old code compared twice - {@code Objects.equals} (which for {@code IAEFluidStack} ignored the
     * stack size) and then the sizes separately - precisely because it needed an amount change to count
     * as a change. {@code GenericStack} is a record and already compares the amount, so the single
     * {@code Objects.equals} preserves the old behaviour exactly. Do not "fix" this into a key-only
     * comparison: a config slot whose amount changed would then stop syncing to the client.
     */
    private boolean equalsSlot(int slot) {
        return Objects.equals(this.inv.getFluidInSlot(slot), this.cache.getFluidInSlot(slot));
    }
}
