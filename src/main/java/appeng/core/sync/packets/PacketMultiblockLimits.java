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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import appeng.core.MultiblockLimits;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;

/**
 * How large the server lets each multiblock be built, sent as a player joins. The numbers come from the
 * config file, and nothing made a client's file agree with the server's.
 */
public class PacketMultiblockLimits extends AppEngPacket {

    private final List<int[]> sizes = new ArrayList<>();
    private final List<ResourceLocation> ids = new ArrayList<>();
    private final List<Boolean> singleChunk = new ArrayList<>();

    // automatic.
    public PacketMultiblockLimits(final ByteBuf stream) {
        final int count = stream.readInt();
        for (int i = 0; i < count; i++) {
            this.ids.add(new ResourceLocation(ByteBufUtils.readUTF8String(stream)));
            this.sizes.add(new int[] { stream.readInt(), stream.readInt(), stream.readInt() });
            this.singleChunk.add(stream.readBoolean());
        }
    }

    // api
    public PacketMultiblockLimits(final Collection<MultiblockLimits.Limit> limits) {
        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(limits.size());
        for (final MultiblockLimits.Limit limit : limits) {
            ByteBufUtils.writeUTF8String(data, limit.getId().toString());
            data.writeInt(limit.getX());
            data.writeInt(limit.getY());
            data.writeInt(limit.getZ());
            data.writeBoolean(limit.requiresSingleChunk());
        }
        this.configureWrite(data);
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        for (int i = 0; i < this.ids.size(); i++) {
            final MultiblockLimits.Limit limit = MultiblockLimits.get(this.ids.get(i));
            if (limit == null) {
                // A multiblock from a mod this side does not have; nothing here can say anything about it.
                continue;
            }
            final int[] size = this.sizes.get(i);
            limit.override(size[0], size[1], size[2], this.singleChunk.get(i));
        }
    }
}
