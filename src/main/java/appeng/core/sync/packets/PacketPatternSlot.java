/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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


import appeng.api.stacks.GenericStack;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.io.IOException;


/**
 * The pattern terminal's craft-from-pattern shift-click, carrying the 9-slot pattern (CONTRACT.md §10).
 * Pinned signature: {@code PacketPatternSlot(IItemHandler pat, @Nullable GenericStack slotItem, boolean
 * shift)} -- {@code pattern} is built from {@code pat}'s slots via {@link GenericStack#fromItemStack}.
 */
public class PacketPatternSlot extends AppEngPacket {

    @Nullable
    public final GenericStack slotItem;

    public final GenericStack[] pattern = new GenericStack[9];

    public final boolean shift;

    // automatic.
    public PacketPatternSlot(final ByteBuf stream) throws IOException {

        this.shift = stream.readBoolean();

        this.slotItem = GenericStack.readBuffer(stream);

        for (int x = 0; x < 9; x++) {
            this.pattern[x] = GenericStack.readBuffer(stream);
        }
    }

    // api
    public PacketPatternSlot(final IItemHandler pat, @Nullable final GenericStack slotItem, final boolean shift) throws IOException {

        this.slotItem = slotItem;
        this.shift = shift;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        data.writeBoolean(shift);

        GenericStack.writeBuffer(slotItem, data);
        for (int x = 0; x < 9; x++) {
            this.pattern[x] = GenericStack.fromItemStack(pat.getStackInSlot(x));
            GenericStack.writeBuffer(this.pattern[x], data);
        }

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final EntityPlayerMP sender = (EntityPlayerMP) player;
        if (sender.openContainer instanceof ContainerPatternEncoder) {
            final ContainerPatternEncoder patternEncoder = (ContainerPatternEncoder) sender.openContainer;
            patternEncoder.craftOrGetItem(this);
        }
    }
}
