/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.features;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.IGuiHandler;

/**
 * One face of the wireless terminal - the screen it opens and the way it is unlocked.
 *
 * <p>There is a single wireless terminal item, and a mode decides what it does. A player unlocks a mode by
 * crafting it into the terminal, and switches between the modes they own.</p>
 *
 * <p>Register through {@link IWirelessTerminalModeRegistry}, which says when.</p>
 */
public interface IWirelessTerminalMode {

    /**
     * What this mode is called in the terminal's NBT, so it survives the mode that owns it being removed and
     * put back. Never an index: registration order is not the same in two installs.
     */
    ResourceLocation getId();

    /**
     * The picture on this mode's button. Ordinarily the terminal part this mode is the wireless form of.
     */
    ItemStack getIcon();

    /**
     * The translation key naming this mode. It is shown on the button, in the terminal's own name and in its
     * tooltip, so keep it to a word or two - it is read as "Wireless Terminal (Crafting)".
     */
    String getUnlocalizedName();

    /**
     * The screen this mode opens. Either one of AE2's own, or one wrapped through
     * {@code GuiWrapper#wrap(IExternalGui)}.
     */
    IGuiHandler getGuiHandler();

    /**
     * What is crafted together with the terminal to add this mode to it. An empty stack registers a mode that
     * can be switched to but never unlocked in survival, which is only ever what a creative-only mode wants.
     */
    ItemStack getUnlockIngredient();
}
