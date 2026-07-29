/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.integration.modules.theoneprobe.part;


import appeng.api.implementations.parts.IPartStorageMonitor;
import appeng.api.parts.IPart;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.theoneprobe.TheOneProbeText;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;


public class StorageMonitorInfoProvider implements IPartProbInfoProvider {

    @Override
    public void addProbeInfo(IPart part, ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
        if (part instanceof IPartStorageMonitor) {
            final IPartStorageMonitor monitor = (IPartStorageMonitor) part;

            final GenericStack displayed = monitor.getDisplayed();
            final boolean isLocked = monitor.isLocked();

            // The old "TODO: generalize" is done: the branch on item-vs-fluid is gone, because every key
            // type now answers getDisplayName() for itself. A monitor showing a key type this fork has
            // never heard of names it correctly with no change here.
            if (displayed != null) {
                probeInfo.text(TheOneProbeText.SHOWING.getLocal() + ": " + displayed.what().getDisplayName().getFormattedText());
            }

            probeInfo.text(isLocked ? TheOneProbeText.LOCKED.getLocal() : TheOneProbeText.UNLOCKED.getLocal());
        }
    }

}
