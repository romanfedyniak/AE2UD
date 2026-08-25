/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.networking.pathing;

import net.minecraft.util.ResourceLocation;

import appeng.api.AEApi;

/**
 * The channel tiers AE2 registers itself, and a short way to ask what a tier ended up configured as.
 */
public final class ChannelTiers {

    /** Carries no channels at all - the quartz fibre. */
    public static final ResourceLocation NONE = new ResourceLocation("appliedenergistics2", "none");

    /** Glass, covered and smart cables. */
    public static final ResourceLocation NORMAL = new ResourceLocation("appliedenergistics2", "normal");

    /** Dense cables. */
    public static final ResourceLocation DENSE = new ResourceLocation("appliedenergistics2", "dense");

    /**
     * One face of a controller. Unlimited by default, so that what a face gives is decided by the cable
     * plugged into it.
     */
    public static final ResourceLocation CONTROLLER = new ResourceLocation("appliedenergistics2", "controller");

    /**
     * A quantum bridge. Everything crossing it funnels through the ring's centre, so one number covers
     * all nine blocks; cables reach only the four side blocks, so there is no single cable to defer to.
     */
    public static final ResourceLocation QUANTUM_BRIDGE = new ResourceLocation("appliedenergistics2", "quantum_bridge");

    /**
     * The far side of an ME P2P tunnel. Unlimited by default: a tunnel has exactly one cable at either
     * end, so what it carries is decided by the narrower of the two.
     */
    public static final ResourceLocation P2P_ME_TUNNEL = new ResourceLocation("appliedenergistics2", "p2p_me_tunnel");

    private ChannelTiers() {
    }

    /**
     * @return the tier's configured capacity, or 0 if no such tier was registered
     */
    public static int capacityOf(final ResourceLocation id) {
        final IChannelTier tier = AEApi.instance().registries().channelTiers().getTier(id);
        return tier == null ? 0 : tier.getCapacity();
    }
}
