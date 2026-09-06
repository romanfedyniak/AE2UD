/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.debug;


import appeng.core.AppEng;
import appeng.items.AEBaseItem;
import appeng.util.Platform;
import appeng.worldgen.meteorite.MeteorConstants;
import appeng.worldgen.meteorite.MeteoritePlacer;
import appeng.worldgen.meteorite.debug.MeteoriteSpawner;
import appeng.worldgen.meteorite.settings.CraterType;
import appeng.worldgen.meteorite.settings.PlacedMeteoriteSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.util.Constants.NBT;
import org.jetbrains.annotations.NotNull;


/**
 * Drops a meteorite where it is clicked, in one go rather than a chunk at a time - the chunks around the
 * player are all there already, which is the one situation the generator never gets to assume.
 * <p>
 * Sneak and right-click the air to walk through the kinds of crater.
 */
public class ToolMeteoritePlacer extends AEBaseItem {

    private static final String MODE_TAG = "mode";

    /** Wide enough for the bowl of the largest meteorite, and it scales with the one being placed. */
    private static final float CRATER_REACH = 5f;

    @Override
    @NotNull
    public ActionResult<ItemStack> onItemRightClick(final World world, @NotNull final EntityPlayer player,
            @NotNull final EnumHand hand) {
        if (world.isRemote || hand != EnumHand.MAIN_HAND || !player.isSneaking()) {
            return super.onItemRightClick(world, player, hand);
        }

        final ItemStack itemStack = player.getHeldItemMainhand();
        final NBTTagCompound nbt = Platform.openNbtData(itemStack);

        if (nbt.hasKey(MODE_TAG, NBT.TAG_BYTE)) {
            nbt.setByte(MODE_TAG, (byte) ((nbt.getByte(MODE_TAG) + 1) % CraterType.values().length));
        } else {
            nbt.setByte(MODE_TAG, (byte) CraterType.NORMAL.ordinal());
        }

        player.sendStatusMessage(new TextComponentString(getCraterType(itemStack).name()), true);
        return ActionResult.newResult(EnumActionResult.SUCCESS, itemStack);
    }

    @Override
    @NotNull
    public EnumActionResult onItemUseFirst(@NotNull final EntityPlayer player, @NotNull final World world,
            @NotNull final BlockPos pos, @NotNull final EnumFacing side, final float hitX, final float hitY,
            final float hitZ, @NotNull final EnumHand hand) {
        if (world.isRemote) {
            return EnumActionResult.PASS;
        }

        // Without the generator there is nothing to hold back what a crater breaks.
        if (AppEng.instance().getMeteoriteGen() == null) {
            player.sendMessage(new TextComponentString("Meteorite world generation is disabled."));
            return EnumActionResult.FAIL;
        }

        final float coreRadius = world.rand.nextFloat()
                * (MeteorConstants.MAX_METEOR_RADIUS - MeteorConstants.MIN_METEOR_RADIUS)
                + MeteorConstants.MIN_METEOR_RADIUS;

        final PlacedMeteoriteSettings settings = new MeteoriteSpawner().trySpawnMeteorite(world, pos, coreRadius,
                getCraterType(player.getHeldItemMainhand()), world.rand.nextFloat() > 0.5f);

        final int range = (int) Math.ceil((coreRadius * 2 + MeteorConstants.MIN_METEOR_RADIUS) * CRATER_REACH);
        MeteoritePlacer.place(world, settings, boxAround(pos, range), true);

        player.sendMessage(new TextComponentString(
                "Placed a " + settings.getCraterType().name() + " meteorite of radius "
                        + String.format("%.1f", coreRadius) + " at y=" + pos.getY() + ", reaching " + range
                        + " blocks."));
        return EnumActionResult.SUCCESS;
    }

    private static CraterType getCraterType(final ItemStack stack) {
        return CraterType.values()[Platform.openNbtData(stack).getByte(MODE_TAG)];
    }

    /**
     * The whole height of the world, not a slice around the click: the crater is a bowl, and everything
     * above its wall has to be taken away or the meteorite ends up under a ceiling of untouched ground.
     */
    private static StructureBoundingBox boxAround(final BlockPos pos, final int range) {
        return new StructureBoundingBox(
                pos.getX() - range, 0, pos.getZ() - range,
                pos.getX() + range, 255, pos.getZ() + range);
    }
}
