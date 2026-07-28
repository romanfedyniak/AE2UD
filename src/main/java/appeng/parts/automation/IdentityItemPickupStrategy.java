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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * Pickup strategy backing {@link PartIdentityAnnihilationPlane}: always yields the block's
 * silk-touch drop (regardless of whether the plane is actually enchanted with Silk Touch) at a fixed
 * energy surcharge. This is the pre-port {@code PartIdentityAnnihilationPlane}'s overrides of
 * {@code calculateEnergyUsage}/{@code obtainBlockDrops}, moved from the part onto the strategy since
 * that logic no longer lives on the part.
 */
class IdentityItemPickupStrategy extends ItemPickupStrategy {

    private static final float SILK_TOUCH_FACTOR = 16;

    IdentityItemPickupStrategy(World world, BlockPos pos, EnumFacing side, TileEntity host,
            Map<Enchantment, Integer> enchantments, @Nullable UUID ownerUuid) {
        super(world, pos, side, host, enchantments, ownerUuid);
    }

    @Override
    protected float calculateEnergyUsage(WorldServer w, BlockPos pos, List<ItemStack> items) {
        return super.calculateEnergyUsage(w, pos, items) * SILK_TOUCH_FACTOR;
    }

    @Override
    protected List<ItemStack> obtainBlockDrops(WorldServer w, BlockPos pos) {
        FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(w);
        IBlockState state = w.getBlockState(pos);

        if (state.getBlock().canSilkHarvest(w, pos, state, fakePlayer)) {
            List<ItemStack> out = new ArrayList<>(1);
            Item item = Item.getItemFromBlock(state.getBlock());

            if (item != Items.AIR) {
                int meta = 0;
                if (item.getHasSubtypes()) {
                    meta = state.getBlock().getMetaFromState(state);
                }
                out.add(new ItemStack(item, 1, meta));
            }
            return out;
        }

        return super.obtainBlockDrops(w, pos);
    }
}
