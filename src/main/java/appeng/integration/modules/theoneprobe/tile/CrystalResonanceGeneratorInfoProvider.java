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

package appeng.integration.modules.theoneprobe.tile;


import appeng.integration.modules.theoneprobe.TheOneProbeText;
import appeng.tile.AEBaseTile;
import appeng.tile.networking.TileCrystalResonanceGenerator;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;


/**
 * Says when a crystal resonance generator is being held quiet by another one on the same network.
 */
public class CrystalResonanceGeneratorInfoProvider implements ITileProbInfoProvider {

    @Override
    public void addProbeInfo(final AEBaseTile tile, final ProbeMode mode, final IProbeInfo probeInfo, final EntityPlayer player, final World world, final IBlockState blockState, final IProbeHitData data) {
        if (tile instanceof TileCrystalResonanceGenerator && ((TileCrystalResonanceGenerator) tile).isSuppressed()) {
            probeInfo.text(TextFormatting.RED + TheOneProbeText.SUPPRESSED.getLocal());
        }
    }
}
