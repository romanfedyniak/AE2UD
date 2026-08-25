package appeng.container.interfaces;

import net.minecraft.item.ItemStack;

/**
 * A screen opened from a wireless terminal, which knows which terminal that is.
 *
 * <p>The mode switch needs it on the client, where the only place the list of modes exists is the item's own
 * NBT.</p>
 */
public interface IWirelessTerminalContainer {

    ItemStack getTerminal();
}
