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


import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.hooks.CompassManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;


/**
 * What a compass was told a moment ago is no longer true - throw it away and ask again.
 * <p>
 * A client keeps an answer for half a minute rather than asking every frame, which is fine while nothing
 * moves; but a chest someone has just broken is gone now, and a needle pointing at it until the cache lapses
 * looks like a broken compass rather than a stale one.
 */
public class PacketClearCompassCache extends AppEngPacket {

    // automatic.
    public PacketClearCompassCache(final ByteBuf stream) {
    }

    // api
    public PacketClearCompassCache() {
        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        this.configureWrite(data);
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        CompassManager.INSTANCE.invalidateAll();
    }
}
