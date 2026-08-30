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


import appeng.client.gui.implementations.GuiCraftingTree;
import appeng.core.AELog;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.crafting.tree.CraftingPlanTree;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


/**
 * The crafting plan as a tree. Compressed rather than trimmed: the same item repeats all over a real plan,
 * which is exactly what GZIP is good at, and a tree cut short would be a tree that lies.
 */
public class PacketCraftingPlanTree extends AppEngPacket {

    private static final int MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024;

    @Nullable
    private final CraftingPlanTree tree;

    public PacketCraftingPlanTree(final ByteBuf stream) {
        CraftingPlanTree read = null;
        try {
            final byte[] compressed = new byte[stream.readInt()];
            stream.readBytes(compressed);
            read = CraftingPlanTree.read(Unpooled.wrappedBuffer(decompress(compressed)));
        } catch (final IOException | RuntimeException e) {
            AELog.debug(e);
        }
        this.tree = read;
    }

    public PacketCraftingPlanTree(final CraftingPlanTree tree) throws IOException {
        this.tree = tree;

        final ByteBuf body = Unpooled.buffer();
        final byte[] compressed;
        try {
            tree.write(body);
            compressed = compress(body);
        } finally {
            body.release();
        }

        final ByteBuf data = Unpooled.buffer(compressed.length + 8);
        data.writeInt(this.getPacketID());
        data.writeInt(compressed.length);
        data.writeBytes(compressed);
        this.configureWrite(data);
    }

    private static byte[] compress(final ByteBuf body) throws IOException {
        final byte[] raw = new byte[body.readableBytes()];
        body.getBytes(body.readerIndex(), raw);

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(raw.length / 2 + 32);
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(raw);
        }
        return bytes.toByteArray();
    }

    private static byte[] decompress(final byte[] compressed) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(compressed.length * 4);
        final byte[] chunk = new byte[8192];

        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            int read;
            while ((read = in.read(chunk)) > 0) {
                if (bytes.size() + read > MAX_DECOMPRESSED_BYTES) {
                    throw new IOException("Crafting plan tree expands past " + MAX_DECOMPRESSED_BYTES + " bytes");
                }
                bytes.write(chunk, 0, read);
            }
        }

        return bytes.toByteArray();
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen screen = Minecraft.getMinecraft().currentScreen;

        if (this.tree != null && screen instanceof GuiCraftingTree gui) {
            gui.postTree(this.tree);
        }
    }
}
