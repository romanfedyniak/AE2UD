/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.parts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.util.ResourceLocation;

/**
 * What a P2P tunnel looks like on a cable: the shared body and status lights, the frequency colours, and the
 * tunnel's own front. One is built per kind of tunnel and asked for the model of its current state.
 * <p>
 * Register a tunnel of an addon's through
 * {@link appeng.api.features.IP2PTunnelRegistry#registerTunnelType(String, net.minecraft.item.ItemStack, P2PTunnelModels)},
 * which registers these models along with the type; a part model that is never registered is never drawn.
 */
public final class P2PTunnelModels {

    private static final String AE2 = "appliedenergistics2";

    public static final ResourceLocation MODEL_STATUS_OFF = new ResourceLocation(AE2, "part/p2p/p2p_tunnel_status_off");
    public static final ResourceLocation MODEL_STATUS_ON = new ResourceLocation(AE2, "part/p2p/p2p_tunnel_status_on");
    public static final ResourceLocation MODEL_STATUS_HAS_CHANNEL = new ResourceLocation(AE2, "part/p2p/p2p_tunnel_status_has_channel");
    public static final ResourceLocation MODEL_FREQUENCY = new ResourceLocation(AE2, "part/builtin/p2p_tunnel_frequency");

    private final IPartModel modelOff;
    private final IPartModel modelOn;
    private final IPartModel modelHasChannel;

    /**
     * @param front the tunnel's own face, drawn over the shared body - usually a model whose parent is
     *              {@code appliedenergistics2:part/p2p/p2p_tunnel_base} with its {@code type} texture set.
     */
    public P2PTunnelModels(@Nonnull final ResourceLocation front) {
        this.modelOff = new Model(MODEL_STATUS_OFF, front);
        this.modelOn = new Model(MODEL_STATUS_ON, front);
        this.modelHasChannel = new Model(MODEL_STATUS_HAS_CHANNEL, front);
    }

    /**
     * @param hasPower   the part's {@code isPowered()}.
     * @param hasChannel the part's {@code isActive()}.
     */
    @Nonnull
    public IPartModel getModel(final boolean hasPower, final boolean hasChannel) {
        if (hasPower && hasChannel) {
            return this.modelHasChannel;
        }
        return hasPower ? this.modelOn : this.modelOff;
    }

    @Nonnull
    public List<IPartModel> getModels() {
        return Arrays.asList(this.modelOff, this.modelOn, this.modelHasChannel);
    }

    /** Every model file behind {@link #getModels()}, which is what the part model registry takes. */
    @Nonnull
    public List<ResourceLocation> getModelLocations() {
        final List<ResourceLocation> all = new ArrayList<>();
        for (final IPartModel model : this.getModels()) {
            all.addAll(model.getModels());
        }
        return all;
    }

    private static final class Model implements IPartModel {

        private final List<ResourceLocation> models;

        private Model(final ResourceLocation status, final ResourceLocation front) {
            this.models = Arrays.asList(status, MODEL_FREQUENCY, front);
        }

        @Nonnull
        @Override
        public List<ResourceLocation> getModels() {
            return this.models;
        }
    }
}
