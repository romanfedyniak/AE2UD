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

package appeng.integration.modules.waila.tile;


import appeng.core.localization.WailaText;
import appeng.integration.modules.waila.BaseWailaDataProvider;
import appeng.tile.networking.TileCrystalResonanceGenerator;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import java.util.List;


/**
 * Says when a crystal resonance generator is being held quiet by another one on the same network.
 */
public final class CrystalResonanceGeneratorWailaDataProvider extends BaseWailaDataProvider {

    @Override
    public List<String> getWailaBody(@Nonnull final ItemStack itemStack, final List<String> currentToolTip, final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
        final TileEntity te = accessor.getTileEntity();
        if (te instanceof TileCrystalResonanceGenerator && ((TileCrystalResonanceGenerator) te).isSuppressed()) {
            currentToolTip.add(TextFormatting.RED + WailaText.Suppressed.getLocal());
        }

        return currentToolTip;
    }
}
