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


import appeng.client.render.visualiser.NetworkVisualiserData;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * The picture a held network visualiser draws, as built by
 * {@link appeng.me.visualiser.NetworkVisualiserService}.
 * <p>
 * The payload travels as an opaque block of bytes so that one snapshot can be sent to every player watching
 * the same network without being encoded again for each of them. An empty payload means stop drawing.
 */
public class PacketNetworkVisualiser extends AppEngPacket {

    private final byte[] payload;

    public PacketNetworkVisualiser(final ByteBuf stream) {
        this.payload = new byte[stream.readInt()];
        stream.readBytes(this.payload);
    }

    public PacketNetworkVisualiser(final byte[] payload) {
        this.payload = payload;

        final ByteBuf data = Unpooled.buffer(payload.length + 8);
        data.writeInt(this.getPacketID());
        data.writeInt(payload.length);
        data.writeBytes(payload);

        this.configureWrite(data);
    }

    public static PacketNetworkVisualiser clear() {
        return new PacketNetworkVisualiser(new byte[0]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        NetworkVisualiserData.accept(this.payload);
    }
}
