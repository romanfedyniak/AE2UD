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
    private final int capacity;

    ChannelTier(final ResourceLocation id, final int capacity) {
        this.id = id;
        this.capacity = capacity;
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
