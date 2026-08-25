/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.networking.pathing;

import java.util.Collection;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

/**
 * The channel tiers a node can declare through {@link appeng.api.networking.IGridBlock#getChannelTier()}.
 *
 * <p><b>Register during pre-initialisation.</b> Every tier's number is read from the config file as it is
 * registered, and the file is written once pre-initialisation ends; a tier registered after that never appears
 * there, so nobody can configure it.</p>
 *
 * <p>Registering a tier is all an addon has to do to ship a cable of any size. A node declaring the tier is
 * then limited by that number, and by whatever sits on either side of it - a cable carrying a thousand
 * channels still delivers eight through a normal cable next to it.</p>
 */
public interface IChannelTierRegistry {

    /**
     * Registers a tier under its own name, with the number to use when the config file says nothing.
     *
     * <p>The first registration of a name wins, and a second one is ignored - a pack overrides a tier by
     * editing the config, not by registering over it.</p>
     *
     * @param id              the tier's name, which becomes its key in the config file
     * @param defaultCapacity channels carried, {@code 0} for none and {@code -1} for no limit of its own
     */
    IChannelTier register(ResourceLocation id, int defaultCapacity);

    /**
     * @return the tier registered under this name, or null if nothing was
     */
    @Nullable
    IChannelTier getTier(ResourceLocation id);

    /**
     * @return every registered tier, in registration order
     */
    Collection<IChannelTier> getTiers();
}
