/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.networking.pathing;

import net.minecraft.util.ResourceLocation;

/**
 * How many channels one kind of node can carry.
 *
 * <p>A tier is what a cable, a controller face, a quantum bridge or a P2P tunnel all have in common: a number
 * saying how much passes through. The number is not fixed by the tier itself - it is read from the config file
 * under the tier's own name, so a pack can retune an addon's cable without the addon knowing.</p>
 *
 * @see IChannelTierRegistry
 */
public interface IChannelTier {

    /**
     * The tier's name, which is also the key it is configured under.
     */
    ResourceLocation getId();

    /**
     * How many channels a node of this tier carries.
     *
     * <p>{@code 0} carries nothing at all - that is the quartz fibre, and it is what keeps a node whose route
     * to the controller broke from handing out a channel anyway. {@code -1} means the node imposes no limit of
     * its own, and what passes through it is decided by whatever is on either side.</p>
     */
    int getCapacity();
}
