/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.features.registries;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import appeng.api.networking.pathing.IChannelTier;
import appeng.api.networking.pathing.IChannelTierRegistry;
import appeng.core.AEConfig;
import appeng.core.AELog;

public final class ChannelTierRegistry implements IChannelTierRegistry {

    private final Map<ResourceLocation, IChannelTier> tiers = new LinkedHashMap<>();

    /**
     * The number comes from the config file the moment the tier is named, which is why registering late leaves
     * a tier nobody can configure.
     */
    @Override
    public IChannelTier register(final ResourceLocation id, final int defaultCapacity) {
        if (id == null) {
            return null;
        }

        final IChannelTier existing = this.tiers.get(id);
        if (existing != null) {
            AELog.warn("Channel tier %s is already registered, ignoring the second one.", id);
            return existing;
        }

        final IChannelTier tier = new ChannelTier(id, AEConfig.instance().getChannelTierCapacity(id, defaultCapacity));
        this.tiers.put(id, tier);
        return tier;
    }

    @Nullable
    @Override
    public IChannelTier getTier(final ResourceLocation id) {
        return id == null ? null : this.tiers.get(id);
    }

    @Override
    public Collection<IChannelTier> getTiers() {
        return Collections.unmodifiableCollection(this.tiers.values());
    }

    /**
     * Puts the server's numbers in force on the client. A tier only one side registered keeps what it had.
     *
     * @return whether any number changed
     */
    public boolean applyServerCapacities(final Map<ResourceLocation, Integer> capacities) {
        boolean changed = false;
        for (final Map.Entry<ResourceLocation, Integer> entry : capacities.entrySet()) {
            if (this.tiers.get(entry.getKey()) instanceof ChannelTier tier) {
                changed |= tier.override(entry.getValue());
            }
        }
        return changed;
    }

    /**
     * Back to this side's own config, once the server whose numbers were in force is left.
     *
     * @return whether any number changed
     */
    public boolean restoreLocalCapacities() {
        boolean changed = false;
        for (final IChannelTier tier : this.tiers.values()) {
            if (tier instanceof ChannelTier channelTier) {
                changed |= channelTier.restore();
            }
        }
        return changed;
    }
}
