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


import appeng.api.networking.security.IActionHost;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;


public class PacketSwitchGuis extends AppEngPacket {

    private final GuiBridge newGui;

    // automatic.
    public PacketSwitchGuis(final ByteBuf stream) {
        this.newGui = GuiBridge.values()[stream.readInt()];
    }

    // api
    public PacketSwitchGuis(final GuiBridge newGui) {
        this.newGui = newGui;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(newGui.ordinal());

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final Container c = player.openContainer;
        if (!(c instanceof AEBaseContainer)) {
            return;
        }

        if (!reopen(player, (AEBaseContainer) c, this.newGui)) {
            return;
        }

        // Leaving a crafting plan for the amount screen means going back to edit the order, so the new
        // screen is seeded with it. Every other switch opens a screen that stands on its own.
        if (c instanceof ContainerCraftConfirm && player.openContainer instanceof ContainerCraftAmount) {
            final ContainerCraftAmount amount = (ContainerCraftAmount) player.openContainer;
            ((ContainerCraftConfirm) c).restoreRequestTo(amount);
            amount.detectAndSendChanges();
        }
    }

    /**
     * Opens another screen on the host the given container was opened from - a tile in the world, or a
     * terminal item in an inventory or bauble slot.
     *
     * @return false if the host offered no way back, which leaves the current screen open.
     */
    public static boolean reopen(final EntityPlayer player, final AEBaseContainer from, final GuiBridge gui) {
        final ContainerOpenContext context = from.getOpenContext();
        if (context == null || !(from.getTarget() instanceof IActionHost)) {
            return false;
        }

        final TileEntity te = context.getTile();
        if (te != null) {
            Platform.openGUI(player, te, context.getSide(), gui);
            return true;
        }

        if (from.getTarget() instanceof IInventorySlotAware) {
            final IInventorySlotAware i = (IInventorySlotAware) from.getTarget();
            Platform.openGUI(player, i.getInventorySlot(), gui, i.isBaubleSlot());
            return true;
        }

        return false;
    }
}
