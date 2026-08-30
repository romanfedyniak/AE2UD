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


import appeng.client.render.BlockPosHighlighter;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.List;


/**
 * Where to point the player. Sent only in answer to a click that asked, so it carries the positions the
 * screen deliberately does not keep.
 */
public class PacketHighlightBlocks extends AppEngPacket {

    private static final int HIGHLIGHT_MILLISECONDS = 15_000;
    private static final int MAX_BLOCKS = 1024;

    private final List<BlockPos> blocks = new ArrayList<>();
    private final int dimension;

    public PacketHighlightBlocks(final ByteBuf stream) {
        this.dimension = stream.readInt();
        final int count = Math.min(MAX_BLOCKS, stream.readInt());
        for (int i = 0; i < count; i++) {
            this.blocks.add(BlockPos.fromLong(stream.readLong()));
        }
    }

    public PacketHighlightBlocks(final List<BlockPos> blocks, final int dimension) {
        this.blocks.addAll(blocks.size() > MAX_BLOCKS ? blocks.subList(0, MAX_BLOCKS) : blocks);
        this.dimension = dimension;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(dimension);
        data.writeInt(this.blocks.size());
        for (final BlockPos block : this.blocks) {
            data.writeLong(block.toLong());
        }
        this.configureWrite(data);
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final Minecraft mc = Minecraft.getMinecraft();

        if (this.blocks.isEmpty()) {
            mc.player.sendMessage(new TextComponentTranslation("chat.appliedenergistics2.MachineNotFound"));
            return;
        }

        BlockPosHighlighter.hilightBlocks(this.blocks, System.currentTimeMillis() + HIGHLIGHT_MILLISECONDS,
                this.dimension);
        BlockPosHighlighter.turnPlayerTowards(this.blocks);
        mc.player.sendMessage(new TextComponentTranslation("chat.appliedenergistics2.MachineHighlighted",
                this.blocks.size()));

        // Nothing to look at while a screen is in the way.
        mc.player.closeScreen();
    }
}
