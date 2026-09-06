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
import appeng.worldgen.meteorite.converter.MeteoriteSettingsConverter;
import appeng.worldgen.meteorite.fallout.FalloutMode;
import appeng.worldgen.meteorite.settings.CraterLakeState;
import appeng.worldgen.meteorite.settings.CraterType;
import appeng.worldgen.meteorite.settings.PlacedMeteoriteSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;


/**
 * Drops a meteorite where it is clicked, in one go rather than a chunk at a time - the chunks around the
 * player are all there already, which is the one situation the generator never gets to assume.
 */
public class ToolMeteoritePlacer extends AEBaseItem {

    @Override
    public EnumActionResult onItemUseFirst(final EntityPlayer player, final World world, final BlockPos pos,
            final EnumFacing side, final float hitX, final float hitY, final float hitZ, final EnumHand hand) {
        if (Platform.isClient()) {
            return EnumActionResult.PASS;
        }

        // Without the generator there is nothing to hold back what a crater breaks.
        if (AppEng.instance().getMeteoriteGen() == null) {
            player.sendMessage(new TextComponentString("Meteorite world generation is disabled."));
            return EnumActionResult.SUCCESS;
        }

        final long seed = MeteoriteSettingsConverter.generateSeed(pos, world);
        final Random rng = new Random(seed);
        final float radius = rng.nextFloat()
                * (MeteorConstants.MAX_METEOR_RADIUS - MeteorConstants.MIN_METEOR_RADIUS)
                + MeteorConstants.MIN_METEOR_RADIUS;

        final PlacedMeteoriteSettings settings = new PlacedMeteoriteSettings(seed, pos, radius, CraterType.NORMAL,
                false, CraterLakeState.FALSE, FalloutMode.fromBiome(world.getBiome(pos)));

        MeteoritePlacer.place(world, settings, boxAround(pos, radius), true);
        return EnumActionResult.SUCCESS;
    }

    private static StructureBoundingBox boxAround(final BlockPos pos, final float radius) {
        final int range = 4 * 16 + MathHelper.ceil(radius);

        return new StructureBoundingBox(
                pos.getX() - range, 0, pos.getZ() - range,
                pos.getX() + range, 255, pos.getZ() + range);
    }
}
