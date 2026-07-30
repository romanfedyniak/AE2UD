/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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


import appeng.api.stacks.AEKey;
import appeng.container.implementations.ContainerFluidInterfaceConfigurationTerminal;
import appeng.core.AELog;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.fluids.container.ContainerFluidInterface;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nullable;


/**
 * @author BrockWS
 * @version rv6 - 23/05/2018
 * <p/>
 * Tells a fluid-backed container which fluid the player targeted. Pinned signature:
 * {@code PacketTargetFluidStack(@Nullable AEKey what)} -- kept as a distinct class from
 * {@link PacketTargetItemStack} per CONTRACT.md's wave 4 prerequisites ("do not merge them"), even though
 * both now carry a bare {@link AEKey}. Two dispatch targets remain, both interface screens:
 * {@code ContainerFluidInterface} and {@code ContainerFluidInterfaceConfigurationTerminal} (each must
 * implement {@code void setTargetStack(AEKey stack)}). The fluid terminal and wireless fluid terminal were
 * the other two, and went with the rest of the legacy fluid parts - the generic terminal targets a key
 * through {@link PacketTargetItemStack} regardless of its type. This packet follows them once the
 * interface is generalised in stage 3.
 */
public class PacketTargetFluidStack extends AppEngPacket {
    private AEKey stack;

    // automatic.
    public PacketTargetFluidStack(final ByteBuf stream) {
        try {
            this.stack = AEKey.readOptionalKey(stream);
        } catch (Exception ex) {
            AELog.debug(ex);
            this.stack = null;
        }
    }

    // api
    public PacketTargetFluidStack(@Nullable AEKey stack) {

        this.stack = stack;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        try {
            AEKey.writeOptionalKey(data, stack);
        } catch (Exception ex) {
            AELog.debug(ex);
        }
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (player.openContainer instanceof ContainerFluidInterface) {
            ((ContainerFluidInterface) player.openContainer).setTargetStack(this.stack);
        } else if (player.openContainer instanceof ContainerFluidInterfaceConfigurationTerminal) {
            ((ContainerFluidInterfaceConfigurationTerminal) player.openContainer).setTargetStack(this.stack);
        }
    }
}
