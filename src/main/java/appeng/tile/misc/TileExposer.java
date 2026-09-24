/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's storage exposer (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.tile.misc;

import javax.annotation.Nullable;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import appeng.api.networking.GridFlags;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import appeng.helpers.exposer.DualityExposer;
import appeng.tile.grid.AENetworkTile;
import appeng.util.Platform;

/**
 * The storage exposer as a block: shows the network on all six sides.
 */
public class TileExposer extends AENetworkTile implements IStorageWatcherNode {

    private final DualityExposer duality = new DualityExposer(this.getProxy(), this);

    public TileExposer() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    @Override
    public boolean hasCapability(final Capability<?> capability, @Nullable final EnumFacing facing) {
        return Platform.isServer() && DualityExposer.exposes(capability) || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(final Capability<T> capability, @Nullable final EnumFacing facing) {
        if (Platform.isServer() && DualityExposer.exposes(capability)) {
            return this.duality.getCapability(capability);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void updateWatcher(final IStackWatcher newWatcher) {
        this.duality.updateWatcher(newWatcher);
    }

    @Override
    public void onStackChange(final AEKey what, final long amount) {
        this.duality.onStackChange(what, amount);
    }
}
