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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.client.gui.implementations.GuiCellView;
import appeng.core.AELog;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.PacketCompression;
import appeng.core.sync.network.INetworkInfo;
import appeng.me.storage.CellContents;


/**
 * What a cell holds, answering {@link PacketCellViewRequest}: in as many parts as it takes, the last one saying so.
 * <p/>
 * A part is closed once it reaches {@link #PART_BYTES} before compression, so however little an entry compresses,
 * no part comes near what one packet may carry, and no cell is too big to be shown whole.
 */
public class PacketCellViewContents extends AppEngPacket {

    static final int PART_BYTES = 1024 * 1024;
    private static final int MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024;

    private final UUID cellId;
    private final boolean last;
    @Nullable
    private final KeyCounter contents;

    // automatic.
    public PacketCellViewContents(final ByteBuf stream) {
        this.cellId = new UUID(stream.readLong(), stream.readLong());
        this.last = stream.readBoolean();

        KeyCounter read = null;
        try {
            final byte[] part = new byte[stream.readInt()];
            stream.readBytes(part);
            read = decodePart(part);
        } catch (final IOException | RuntimeException e) {
            AELog.debug(e);
        }
        this.contents = read;
    }

    // api
    private PacketCellViewContents(final UUID cellId, final byte[] part, final boolean last) {
        this.cellId = cellId;
        this.last = last;
        this.contents = null;

        final ByteBuf data = Unpooled.buffer(part.length + 32);

        data.writeInt(this.getPacketID());
        data.writeLong(cellId.getMostSignificantBits());
        data.writeLong(cellId.getLeastSignificantBits());
        data.writeBoolean(last);
        data.writeInt(part.length);
        data.writeBytes(part);

        this.configureWrite(data);
    }

    /** The packets that carry {@code stored}; a cell with nothing saved is one empty part. */
    public static List<PacketCellViewContents> of(final UUID cellId, @Nullable final CellContents stored)
            throws IOException {
        final List<byte[]> parts = encodeParts(stored == null ? Object2LongMaps.emptyMap() : stored.amounts());

        final List<PacketCellViewContents> packets = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            packets.add(new PacketCellViewContents(cellId, parts.get(i), i == parts.size() - 1));
        }
        return packets;
    }

    static List<byte[]> encodeParts(final Object2LongMap<AEKey> amounts) throws IOException {
        final List<byte[]> parts = new ArrayList<>();
        final ByteBuf body = Unpooled.buffer();

        try {
            for (final Object2LongMap.Entry<AEKey> entry : amounts.object2LongEntrySet()) {
                GenericStack.writeBuffer(new GenericStack(entry.getKey(), entry.getLongValue()), body);
                if (body.readableBytes() >= PART_BYTES) {
                    parts.add(PacketCompression.compress(body));
                    body.clear();
                }
            }

            if (body.readableBytes() > 0 || parts.isEmpty()) {
                parts.add(PacketCompression.compress(body));
            }
        } finally {
            body.release();
        }

        return parts;
    }

    static KeyCounter decodePart(final byte[] part) throws IOException {
        final ByteBuf body = Unpooled.wrappedBuffer(PacketCompression.decompress(part, MAX_DECOMPRESSED_BYTES));
        final KeyCounter out = new KeyCounter();

        while (body.readableBytes() > 0) {
            final GenericStack stack = GenericStack.readBuffer(body);
            if (stack != null) {
                out.add(stack.what(), stack.amount());
            }
        }

        return out;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiCellView) {
            // A part that could not be read still has to count, or the window would wait for ever.
            ((GuiCellView) screen).postContents(this.cellId, this.contents != null ? this.contents : new KeyCounter(),
                    this.last);
        }
    }
}
