/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.sync.packets;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import appeng.api.AEApi;
import appeng.api.networking.pathing.IChannelTier;
import appeng.core.features.registries.ChannelTierRegistry;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;

/**
 * The channel tiers the server runs with, sent as a player joins. A tier's number comes from the config file,
 * and nothing made a client's file agree with the server's.
 */
public class PacketChannelTiers extends AppEngPacket {

    private final Map<ResourceLocation, Integer> capacities = new LinkedHashMap<>();

    // automatic.
    public PacketChannelTiers(final ByteBuf stream) {
        final int count = stream.readInt();
        for (int i = 0; i < count; i++) {
            this.capacities.put(new ResourceLocation(ByteBufUtils.readUTF8String(stream)), stream.readInt());
        }
    }

    // api
    public PacketChannelTiers(final Collection<IChannelTier> tiers) {
        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(tiers.size());
        for (final IChannelTier tier : tiers) {
            ByteBufUtils.writeUTF8String(data, tier.getId().toString());
            data.writeInt(tier.getCapacity());
        }
        this.configureWrite(data);
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final ChannelTierRegistry registry = (ChannelTierRegistry) AEApi.instance().registries().channelTiers();
        if (registry.applyServerCapacities(this.capacities)) {
            // Cables may already have built their channel marks from the client's own numbers
            Minecraft.getMinecraft().renderGlobal.loadRenderers();
        }
    }
}
