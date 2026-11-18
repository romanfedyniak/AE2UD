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


import appeng.block.networking.BlockCableBus;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.items.tools.ToolNetworkTool;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.ForgeHooks;


public class PacketClick extends AppEngPacket {

    private final int x;
    private final int y;
    private final int z;
    private EnumFacing side;
    private final float hitX;
    private final float hitY;
    private final float hitZ;
    private EnumHand hand;
    private final boolean leftClick;

    // automatic.
    public PacketClick(final ByteBuf stream) {
        this.x = stream.readInt();
        this.y = stream.readInt();
        this.z = stream.readInt();
        byte side = stream.readByte();
        if (side != -1) {
            this.side = EnumFacing.values()[side];
        } else {
            this.side = null;
        }
        this.hitX = stream.readFloat();
        this.hitY = stream.readFloat();
        this.hitZ = stream.readFloat();
        this.hand = EnumHand.values()[stream.readByte()];
        this.leftClick = stream.readBoolean();
    }

    // api
    public PacketClick(final BlockPos pos, final EnumFacing side, final float hitX, final float hitY, final float hitZ, final EnumHand hand) {
        this(pos, side, hitX, hitY, hitZ, hand, false);
    }

    public PacketClick(final BlockPos pos, final EnumFacing side, final float hitX, final float hitY, final float hitZ, final EnumHand hand, boolean leftClick) {

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(this.x = pos.getX());
        data.writeInt(this.y = pos.getY());
        data.writeInt(this.z = pos.getZ());
        if (side == null) {
            data.writeByte(-1);
        } else {
            data.writeByte(side.ordinal());
        }
        data.writeFloat(this.hitX = hitX);
        data.writeFloat(this.hitY = hitY);
        data.writeFloat(this.hitZ = hitZ);
        data.writeByte(hand.ordinal());
        data.writeBoolean(this.leftClick = leftClick);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final BlockPos pos = new BlockPos(this.x, this.y, this.z);
        if (this.leftClick) {
            if (!withinReach(player, pos)) {
                return;
            }
            final Block block = player.world.getBlockState(pos).getBlock();
            if (block instanceof BlockCableBus) {
                final Vec3d hitVec = new Vec3d(this.hitX, this.hitY, this.hitZ);
                if (ForgeHooks.onLeftClickBlock(player, pos, this.side, hitVec).isCanceled()) {
                    return;
                }
                ((BlockCableBus) block).onBlockClickPacket(player.world, pos, player, this.hand, hitVec);
            }
        } else if (this.side == null) {
            // The network tool used on nothing: its own screen. A use on a block arrives as the game's own packet.
            final ItemStack is = player.inventory.getCurrentItem();
            if (!is.isEmpty() && is.getItem() instanceof ToolNetworkTool tnt) {
                tnt.serverSideToolLogic(is, player, hand, player.world, pos, null, hitX, hitY, hitZ);
            }
        }
    }

    /** The block is loaded and as near as a click on it may be (the game's own limit for digging). */
    private static boolean withinReach(final EntityPlayer player, final BlockPos pos) {
        if (!player.world.isBlockLoaded(pos)) {
            return false;
        }
        final double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 1;
        return player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reach * reach;
    }
}
