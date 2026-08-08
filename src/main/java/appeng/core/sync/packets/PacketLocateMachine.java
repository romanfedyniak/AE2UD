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


import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.util.DimensionalCoord;
import appeng.container.AEBaseContainer;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.core.sync.network.NetworkHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Asks where the machines of one kind are. The crafting tree knows which machine runs a pattern but not
 * where it stands, and carrying every position for every node would cost far more than the rare click that
 * asks for one.
 */
public class PacketLocateMachine extends AppEngPacket {

    private final AEKey machine;

    public PacketLocateMachine(final ByteBuf stream) throws IOException {
        this.machine = AEKey.readKey(stream);
    }

    public PacketLocateMachine(final AEKey machine) throws IOException {
        this.machine = machine;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        AEKey.writeKey(data, machine);
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (this.machine == null || !(player.openContainer instanceof AEBaseContainer container)) {
            return;
        }

        final IGrid grid = gridOf(container);
        if (grid == null) {
            return;
        }

        final ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
        final List<BlockPos> found = new ArrayList<>();
        int dimension = player.world.provider.getDimension();

        for (final ICraftingMedium medium : crafting.getMediums()) {
            final ItemStack icon = medium.getMachineIdentity().getIcon();
            if (icon.isEmpty() || !this.machine.equals(AEItemKey.of(icon))) {
                continue;
            }

            final DimensionalCoord where = medium.getMachineLocation();
            if (where != null && where.getWorld().provider.getDimension() == dimension) {
                found.add(where.getPos());
            }
        }

        NetworkHandler.instance().sendTo(new PacketHighlightBlocks(found, dimension), (EntityPlayerMP) player);
    }

    private static IGrid gridOf(final AEBaseContainer container) {
        if (!(container.getTarget() instanceof IActionHost host)) {
            return null;
        }

        final IGridNode node = host.getActionableNode();
        return node == null ? null : node.getGrid();
    }
}
