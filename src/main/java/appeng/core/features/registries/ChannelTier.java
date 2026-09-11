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

import net.minecraft.util.ResourceLocation;

import appeng.api.networking.pathing.IChannelTier;

public final class ChannelTier implements IChannelTier {

    private final ResourceLocation id;
    /** What this side's config file says. */
    private final int localCapacity;
    /** What is in force: the local number, or the server's while connected to one. */
    private int capacity;

    ChannelTier(final ResourceLocation id, final int capacity) {
        this.id = id;
        this.localCapacity = capacity;
        this.capacity = capacity;
    }

    /**
     * @return whether the number in force changed
     */
    boolean override(final int serverCapacity) {
        if (this.capacity == serverCapacity) {
            return false;
        }
        this.capacity = serverCapacity;
        return true;
    }

    /**
     * @return whether the number in force changed
     */
    boolean restore() {
        return this.override(this.localCapacity);
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public int getCapacity() {
        return this.capacity;
    }
}
