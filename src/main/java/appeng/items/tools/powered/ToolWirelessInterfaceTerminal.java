package appeng.items.tools.powered;

import appeng.core.features.registries.WirelessTerminalMode;
import appeng.core.sync.GuiBridge;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.IGuiHandler;

/**
 * The wireless interface terminal from before there was one terminal with modes. See
 * {@link ToolWirelessCraftingTerminal} for why it is still registered.
 */
public class ToolWirelessInterfaceTerminal extends ToolWirelessTerminal {
    @Override
    public boolean canHandle(ItemStack is) {
        return is.getItem() == this;
    }

    @Override
    public ResourceLocation getLegacyMode() {
        return WirelessTerminalMode.Ids.PATTERN_ACCESS;
    }

    @Override
    public IGuiHandler getGuiHandler(ItemStack is) {
        return GuiBridge.GUI_WIRELESS_PATTERN_ACCESS_TERMINAL;
    }
}
