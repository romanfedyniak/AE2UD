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


import appeng.container.implementations.ContainerSetAmount;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.IAmountTarget;
import com.google.common.primitives.Longs;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;


/**
 * Carries the amount typed into {@link appeng.client.gui.implementations.GuiSetAmount} to whatever that
 * screen was opened for. Where the amount ends up, and when the player is sent back, belong to the target -
 * see {@link IAmountTarget}.
 */
public class PacketSetAmount extends AppEngPacket {

    private final long amount;

    // automatic.
    public PacketSetAmount(final ByteBuf stream) {
        this.amount = stream.readLong();
    }

    // api
    public PacketSetAmount(final long amount) {
        this.amount = amount;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeLong(amount);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (!(player.openContainer instanceof ContainerSetAmount)) {
            return;
        }

        final ContainerSetAmount source = (ContainerSetAmount) player.openContainer;
        final IAmountTarget target = source.getAmountTarget();

        if (target != null) {
            target.apply(player, source,
                    Longs.constrainToRange(this.amount, target.getMinAmount(), target.getMaxAmount()));
        }
    }
}
