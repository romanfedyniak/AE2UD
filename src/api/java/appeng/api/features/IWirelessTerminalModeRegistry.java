/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.features;

import java.util.Collection;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

/**
 * The modes the wireless terminal can be switched to.
 *
 * <p><b>Register during pre-initialisation.</b> Two things are built from this registry after that and never
 * again: the recipe that adds each mode to a terminal, on {@code RegistryEvent.Register<IRecipe>}, and the key
 * binding that opens each mode, during initialisation. A mode registered later still works and can still be
 * switched to, but nothing will craft it and no key will reach it.</p>
 */
public interface IWirelessTerminalModeRegistry {

    /**
     * Adds a mode. A mode whose id is already taken is refused, so the first registration wins.
     */
    void register(IWirelessTerminalMode mode);

    /**
     * @return the mode by that id, or null if nothing registered it - which is also what a terminal carrying a
     *         mode from a since-removed addon returns.
     */
    @Nullable
    IWirelessTerminalMode getMode(ResourceLocation id);

    /**
     * Every registered mode, in registration order. That order is what the mode buttons are drawn in.
     */
    Collection<IWirelessTerminalMode> getModes();

    /**
     * The mode a terminal has when it says nothing about which mode it has - the plain storage terminal.
     * Null only where that feature is switched off entirely.
     */
    @Nullable
    IWirelessTerminalMode getDefaultMode();
}
