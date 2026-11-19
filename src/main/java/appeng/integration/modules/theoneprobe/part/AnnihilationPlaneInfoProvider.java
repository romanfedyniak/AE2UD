/*
 * This file is part of Applied Energistics 2.
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

package appeng.integration.modules.theoneprobe.part;


import appeng.api.parts.IPart;
import appeng.integration.modules.theoneprobe.TheOneProbeText;
import appeng.parts.automation.PartAnnihilationPlane;
import appeng.util.EnchantmentUtil;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.Map;


public class AnnihilationPlaneInfoProvider implements IPartProbInfoProvider {

    @Override
    public void addProbeInfo(IPart part, ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
        if (part instanceof PartAnnihilationPlane plane) {
            // Read from the saved form, the same one the Waila provider reads.
            final NBTTagCompound tag = new NBTTagCompound();
            plane.writeEnchantments(tag);

            final Map<Enchantment, Integer> enchantments = EnchantmentUtil.getEnchantments(tag);
            if (!enchantments.isEmpty()) {
                probeInfo.text(TheOneProbeText.ENCHANTED_WITH.marker());
                for (final Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    probeInfo.text(name(entry.getKey(), entry.getValue()));
                }
            }
        }
    }

    /** {@link Enchantment#getTranslatedName}, translated by the client. */
    private static String name(final Enchantment enchantment, final int level) {
        String name = IProbeInfo.STARTLOC + enchantment.getName() + IProbeInfo.ENDLOC;
        if (enchantment.isCurse()) {
            name = TextFormatting.RED + name;
        }
        if (level != 1 || enchantment.getMaxLevel() != 1) {
            name += " " + IProbeInfo.STARTLOC + "enchantment.level." + level + IProbeInfo.ENDLOC;
        }
        return name;
    }

}
