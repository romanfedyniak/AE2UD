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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;

import appeng.api.implementations.IAutoExportHost;
import appeng.api.util.RelativeSide;
import appeng.container.AEBaseContainer;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.Reflected;

/**
 * Switches one face of the machine whose window the player has open between pushing out and not.
 */
public final class PacketAutoExportSide extends AppEngPacket {

    private final int side;

    @Reflected
    public PacketAutoExportSide(final ByteBuf stream) {
        this.side = stream.readByte();
    }

    public PacketAutoExportSide(final RelativeSide side) {
        this.side = side.ordinal();

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeByte(this.side);
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (this.side < 0 || this.side >= RelativeSide.values().length
                || !(player.openContainer instanceof AEBaseContainer)) {
            return;
        }

        final Object target = ((AEBaseContainer) player.openContainer).getTarget();
        if (target instanceof IAutoExportHost) {
            ((IAutoExportHost) target).getAutoExport().toggle(RelativeSide.values()[this.side]);
        }
    }
}
