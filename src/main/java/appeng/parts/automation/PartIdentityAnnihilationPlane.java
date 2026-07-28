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


import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Enchantments;


public class PartIdentityAnnihilationPlane extends PartAnnihilationPlane {

    private static final PlaneModels MODELS = new PlaneModels("part/identity_annihilation_plane_", "part/identity_annihilation_plane_on_");

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    public PartIdentityAnnihilationPlane(final ItemStack is) {
        super(is);
    }

    /**
     * Substitutes {@link IdentityItemPickupStrategy} for the plain {@link ItemPickupStrategy} that
     * {@link PartAnnihilationPlane} would otherwise build, so this plane always yields the
     * silk-touch drop at a fixed energy surcharge regardless of its actual enchantments. This used to
     * be two protected method overrides directly on {@code PartAnnihilationPlane}
     * ({@code calculateEnergyUsage}/{@code obtainBlockDrops}); both moved onto
     * {@link ItemPickupStrategy} when the pickup logic left the part, so this class now overrides the
     * strategy list instead.
     */
    @Override
    protected List<PickupStrategy> createPickupStrategies(World world, BlockPos fromPos, EnumFacing fromSide,
            TileEntity host, Map<Enchantment, Integer> enchantments, @Nullable UUID owner) {
        return List.of(new IdentityItemPickupStrategy(world, fromPos, fromSide, host, enchantments, owner));
    }

    @Override
    public boolean onPartShiftActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        TileEntity tile = getTile();
        if (tile instanceof IPartHost host) {
            host.removePart(getSide(), false);
            ItemStack itemStack = AEApi.instance().definitions().parts().annihilationPlane().maybeStack(1).orElse(ItemStack.EMPTY);
            itemStack.addEnchantment(Enchantments.SILK_TOUCH, 1);
            host.addPart(itemStack, getSide(), player, hand);
            return true;
        }

        return false;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.getConnections(), this.isPowered(), this.isActive());
    }

}
