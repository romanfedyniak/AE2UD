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

package appeng.parts.automation;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBlockSpecial;
import net.minecraft.item.ItemFirework;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.IPlantable;

import appeng.api.behaviors.PlacementStrategy;
import appeng.api.config.Actionable;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.util.AEPartLocation;
import appeng.core.AEConfig;
import appeng.util.Platform;

/**
 * Item placement for the formation plane: places a block/plant/etc. in the adjacent space, or spawns
 * the item as an entity. This is the pre-port {@code PartFormationPlane#injectItems} logic, moved
 * off the part and behind the frozen {@link PlacementStrategy} contract so any other registered key
 * type shares the same plane.
 */
class ItemPlacementStrategy implements PlacementStrategy {

    private final World world;
    private final BlockPos pos;
    private final EnumFacing fromSide;
    private final TileEntity host;
    @Nullable
    private final UUID ownerUuid;

    private boolean blocked = false;

    ItemPlacementStrategy(World world, BlockPos pos, EnumFacing fromSide, TileEntity host, @Nullable UUID ownerUuid) {
        this.world = world;
        this.pos = pos;
        this.fromSide = fromSide;
        this.host = host;
        this.ownerUuid = ownerUuid;
    }

    @Override
    public void clearBlocked() {
        this.blocked = !this.world.getBlockState(this.pos).getBlock().isReplaceable(this.world, this.pos);
    }

    @Override
    public long placeInWorld(AEKey what, long amount, Actionable type, boolean placeAsEntity) {
        if (this.blocked || !(what instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }

        Item i = itemKey.getItem();

        long maxStorage = Math.min(amount, itemKey.getMaxStackSize());
        ItemStack is = itemKey.toStack((int) maxStorage);
        boolean worked = false;

        // The direction pointing from the host towards the adjacent block, matching the pre-port
        // AEPartLocation "side" of the plane part itself.
        EnumFacing hostToExternal = this.fromSide.getOpposite();
        AEPartLocation sideLoc = AEPartLocation.fromFacing(hostToExternal);

        if (this.world.getBlockState(this.pos).getBlock().isReplaceable(this.world, this.pos)) {
            if (placeAsEntity) {
                int sum = this.countEntitiesAround(this.world, this.pos);

                if (sum < AEConfig.instance().getFormationPlaneEntityLimit()) {
                    worked = true;

                    if (type == Actionable.MODULATE) {
                        is.setCount((int) maxStorage);
                        this.spawnItemEntity(this.world, this.host, sideLoc, is);
                    }
                }
            } else if (i instanceof ItemBlock || i instanceof ItemBlockSpecial || i instanceof IPlantable
                    || i instanceof ItemSkull || i instanceof ItemFirework || i instanceof IPartItem
                    || i == Item.getItemFromBlock(Blocks.REEDS)) {
                EntityPlayer player = Platform.getPlayer((WorldServer) this.world);
                Platform.configurePlayer(player, sideLoc, this.host);
                EnumHand hand = player.getActiveHand();
                player.setHeldItem(hand, is);

                maxStorage = is.getCount();
                worked = true;
                if (type == Actionable.MODULATE) {
                    if (i instanceof IPlantable || i instanceof ItemSkull || i == Item.getItemFromBlock(Blocks.REEDS)) {
                        boolean didWork = false;

                        if (sideLoc.xOffset == 0 && sideLoc.zOffset == 0) {
                            didWork = i.onItemUse(player, this.world, this.pos.offset(hostToExternal), hand,
                                    hostToExternal.getOpposite(), sideLoc.xOffset, sideLoc.yOffset,
                                    sideLoc.zOffset) == EnumActionResult.SUCCESS;
                        }

                        if (!didWork && sideLoc.xOffset == 0 && sideLoc.zOffset == 0) {
                            didWork = i.onItemUse(player, this.world, this.pos.offset(hostToExternal.getOpposite()), hand,
                                    hostToExternal, sideLoc.xOffset, sideLoc.yOffset,
                                    sideLoc.zOffset) == EnumActionResult.SUCCESS;
                        }

                        if (!didWork && sideLoc.yOffset == 0) {
                            didWork = i.onItemUse(player, this.world, this.pos.offset(EnumFacing.DOWN), hand,
                                    EnumFacing.UP, sideLoc.xOffset, sideLoc.yOffset,
                                    sideLoc.zOffset) == EnumActionResult.SUCCESS;
                        }

                        if (!didWork) {
                            i.onItemUse(player, this.world, this.pos, hand, hostToExternal.getOpposite(),
                                    sideLoc.xOffset, sideLoc.yOffset, sideLoc.zOffset);
                        }

                        maxStorage -= is.getCount();
                    } else {
                        i.onItemUse(player, this.world, this.pos, hand, hostToExternal.getOpposite(), sideLoc.xOffset,
                                sideLoc.yOffset, sideLoc.zOffset);
                        maxStorage -= is.getCount();
                    }
                } else {
                    maxStorage = 1;
                }

                player.setHeldItem(hand, ItemStack.EMPTY);
            } else {
                worked = true;

                int sum = this.countEntitiesAround(this.world, this.pos);

                if (sum < AEConfig.instance().getFormationPlaneEntityLimit()) {
                    if (type == Actionable.MODULATE) {
                        is.setCount((int) maxStorage);
                        this.spawnItemEntity(this.world, this.host, sideLoc, is);
                    }
                } else {
                    worked = false;
                }
            }
        }

        this.blocked = !this.world.getBlockState(this.pos).getBlock().isReplaceable(this.world, this.pos);

        return worked ? maxStorage : 0;
    }

    private void spawnItemEntity(World w, TileEntity te, AEPartLocation side, ItemStack is) {
        double x = (side.xOffset != 0 ? 0 : .7 * (Platform.getRandomFloat() - .5)) + side.xOffset + .5 + te.getPos().getX();
        double y = (side.yOffset != 0 ? 0 : .7 * (Platform.getRandomFloat() - .5)) + side.yOffset + .5 + te.getPos().getY();
        double z = (side.zOffset != 0 ? 0 : .7 * (Platform.getRandomFloat() - .5)) + side.zOffset + .5 + te.getPos().getZ();

        EntityItem ei = new EntityItem(w, x, y, z, is.copy());

        Entity result = ei;

        ei.motionX = side.xOffset * 0.2;
        ei.motionY = side.yOffset * 0.2;
        ei.motionZ = side.zOffset * 0.2;

        if (is.getItem().hasCustomEntity(is)) {
            Entity custom = is.getItem().createEntity(w, ei, is);
            if (custom != null) {
                ei.setDead();
                result = custom;
            }
        }

        if (!w.spawnEntity(result)) {
            result.setDead();
        }
    }

    private int countEntitiesAround(World world, BlockPos pos) {
        AxisAlignedBB t = new AxisAlignedBB(pos).grow(8);
        return world.getEntitiesWithinAABB(Entity.class, t).size();
    }
}
