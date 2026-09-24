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

package appeng.parts.misc;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;

import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.util.AECableType;
import appeng.core.AppEng;
import appeng.helpers.Reflected;
import appeng.helpers.exposer.DualityExposer;
import appeng.items.parts.PartModels;
import appeng.parts.PartBasicState;
import appeng.parts.PartModel;
import appeng.util.Platform;

/**
 * The storage exposer on a cable: shows the network to the one block it faces.
 */
public class PartExposer extends PartBasicState implements IStorageWatcherNode {

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID, "part/exposer_base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/interface_off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/interface_on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/interface_has_channel"));

    private final DualityExposer duality = new DualityExposer(this.getProxy(), this);

    @Reflected
    public PartExposer(final ItemStack is) {
        super(is);
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public float getCableConnectionLength(final AECableType cable) {
        return 4;
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        final TileEntity tile = this.getTile();
        if (Platform.isServer() && tile != null) {
            Platform.notifyBlocksOfNeighbors(tile.getWorld(), tile.getPos());
        }
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    @Override
    public boolean hasCapability(final Capability<?> capability) {
        return Platform.isServer() && DualityExposer.exposes(capability);
    }

    @Override
    public <T> T getCapability(final Capability<T> capability) {
        return Platform.isServer() ? this.duality.getCapability(capability) : null;
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
