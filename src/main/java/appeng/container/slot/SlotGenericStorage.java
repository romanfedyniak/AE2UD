package appeng.container.slot;


import net.minecraftforge.items.IItemHandler;

import appeng.util.inv.GenericStackInv;


/**
 * A slot showing one slot of a {@link GenericStackInv} that holds real stock, of any key type.
 * <p/>
 * Exists to be recognisable. The handler behind it already refuses to hand out a non-item key, so the slot
 * needs no guards of its own; what the client and the server both need is a way to say "this slot is backed
 * by a generic inventory", so a click with a container in hand can be turned into a fill or an empty against
 * that slot rather than against the network.
 */
public class SlotGenericStorage extends SlotOversized {

    private final GenericStackInv inv;
    private final int invSlot;

    public SlotGenericStorage(IItemHandler display, GenericStackInv inv, int slot, int xPos, int yPos) {
        super(display, slot, xPos, yPos);
        this.inv = inv;
        this.invSlot = slot;
    }

    public GenericStackInv getGenericInv() {
        return this.inv;
    }

    public int getGenericSlot() {
        return this.invSlot;
    }
}
