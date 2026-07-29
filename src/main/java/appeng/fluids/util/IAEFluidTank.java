package appeng.fluids.util;


import appeng.api.stacks.GenericStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;


/**
 * A fixed-size array of fluid config/storage slots that also behaves as a Forge {@link IFluidHandler}.
 * <p/>
 * The slot accessors used to be typed {@code IAEFluidStack} - the mutable, fluid-only stack type deleted
 * in wave 0 (CONTRACT.md §4.1). They are now typed {@link GenericStack}, mirroring the identical rename
 * {@code appeng.util.inv.AppEngInternalAEInventory} already got in wave 2
 * ({@code getAEStackInSlot(int): GenericStack}, CONTRACT.md §9 wave 2). An empty slot is still
 * {@code null}, exactly as before - {@code GenericStack} has no "empty" instance, and
 * {@code GenericStack.writeTag}/{@code readTag} already round-trip {@code null} through NBT.
 * <p/>
 * <b>The stored key is not required to be an {@link appeng.api.stacks.AEFluidKey}</b> at the type level,
 * because {@code GenericStack} is type-erased. Every implementor in this package does hold fluids only,
 * and callers that need the {@code FluidStack} back must pattern-match:
 * {@code stack.what() instanceof AEFluidKey fk ? fk.toStack((int) stack.amount()) : null}.
 * <p/>
 * Callers already committed against this shape (wave 4, do not change them):
 * {@code appeng.container.implementations.ContainerFluidInterfaceConfigurationTerminal},
 * {@code appeng.client.me.ClientDCInternalFluidInv}, {@code appeng.parts.AEBasePart#readFromNBT},
 * {@code appeng.tile.AEBaseTile#readFromNBT} and
 * {@code appeng.core.sync.packets.PacketInventoryAction} (via {@code getFluidConfigInventory()}).
 */
public interface IAEFluidTank extends IFluidHandler {
    void setFluidInSlot(final int slot, @Nullable final GenericStack fluid);

    @Nullable
    GenericStack getFluidInSlot(final int slot);

    int getSlots();

}
