package appeng.fluids.container;


import appeng.api.stacks.GenericStack;

import java.util.Map;


/**
 * Implemented by containers whose fluid config slots are synchronised with
 * {@code appeng.core.sync.packets.PacketFluidSlot}.
 * <p/>
 * The map's value type changed from the deleted {@code IAEFluidStack} to {@link GenericStack} in wave 4,
 * when the packet itself was migrated; a missing entry still means "this slot was not part of this
 * update", and a {@code null} value still means "this slot is now empty". See the packet's javadoc and
 * CONTRACT.md §9's wave 4a entry for the wire format.
 */
public interface IFluidSyncContainer {
    void receiveFluidSlots(final Map<Integer, GenericStack> fluids);
}
