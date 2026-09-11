/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.sync.packets;


import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import appeng.core.AELog;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.core.sync.network.NetworkHandler;
import appeng.me.storage.CellContents;
import appeng.me.storage.CellContentsStore;


/**
 * Asks what a cell holds, for the view key's window. Only the server knows: the client's copy of a cell carries
 * an id and a summary.
 */
public class PacketCellViewRequest extends AppEngPacket {

    /** Nobody presses the view key this often, so a client asking faster waits for the next second. */
    private static final int REQUESTS_PER_SECOND = 10;
    private static final Map<EntityPlayer, long[]> RECENT = new WeakHashMap<>();

    private final UUID cellId;

    // automatic.
    public PacketCellViewRequest(final ByteBuf stream) {
        this.cellId = new UUID(stream.readLong(), stream.readLong());
    }

    // api
    public PacketCellViewRequest(final UUID cellId) {
        this.cellId = cellId;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeLong(cellId.getMostSignificantBits());
        data.writeLong(cellId.getLeastSignificantBits());

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (!allow(player)) {
            return;
        }

        // An id with nothing saved under it is answered empty, and leaves nothing behind in the store.
        final CellContentsStore store = CellContentsStore.current();
        final CellContents contents = store == null ? null : store.getIfSaved(this.cellId);

        try {
            for (final PacketCellViewContents part : PacketCellViewContents.of(this.cellId, contents)) {
                NetworkHandler.instance().sendTo(part, (EntityPlayerMP) player);
            }
        } catch (final IOException | IllegalArgumentException e) {
            AELog.warn(e, "Could not send the contents of storage cell " + this.cellId);
        }
    }

    private static boolean allow(final EntityPlayer player) {
        final long second = player.world.getTotalWorldTime() / 20;
        final long[] window = RECENT.computeIfAbsent(player, p -> new long[2]);
        if (window[0] != second) {
            window[0] = second;
            window[1] = 0;
        }
        return ++window[1] <= REQUESTS_PER_SECOND;
    }
}
