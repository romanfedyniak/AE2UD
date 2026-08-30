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

package appeng.tile.networking;


import appeng.api.networking.energy.IPassiveEnergyGenerator;
import appeng.core.AEConfig;
import appeng.tile.grid.AENetworkTile;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import java.io.IOException;
import java.util.EnumSet;


/**
 * Generates power out of nothing, at the rate the config gives it. Only one of these runs on an energy grid;
 * the grid picks the winner and suppresses the rest, see {@link appeng.me.cache.EnergyGridCache}.
 */
public class TileCrystalResonanceGenerator extends AENetworkTile implements IPassiveEnergyGenerator {

    /**
     * Sent to the client so that a suppressed generator can say so in a tooltip. Not saved: the grid decides
     * it anew every tick.
     */
    private boolean suppressed;

    public TileCrystalResonanceGenerator() {
        // It is here to make power, not to spend it.
        this.getProxy().setIdlePowerUsage(0);
        this.getProxy().setValidSides(EnumSet.of(this.getUp().getOpposite()));
    }

    @Override
    public void setOrientation(final EnumFacing inForward, final EnumFacing inUp) {
        super.setOrientation(inForward, inUp);
        // Only the face it stands on joins the grid, so it has to be set onto a cable or a machine.
        this.getProxy().setValidSides(EnumSet.of(inUp.getOpposite()));
    }

    @Override
    public void onPlacement(final ItemStack stack, final EntityPlayer player, final EnumFacing side) {
        super.onPlacement(stack, player, side);
        // It stands on the face it was placed against, crystal pointing away from it.
        this.setOrientation(side.getYOffset() == 0 ? EnumFacing.UP : EnumFacing.SOUTH, side);
    }

    @Override
    public double getRate() {
        return AEConfig.instance().getCrystalResonanceGeneratorRate();
    }

    @Override
    public boolean isSuppressed() {
        return this.suppressed;
    }

    @Override
    public void setSuppressed(final boolean suppressed) {
        if (suppressed != this.suppressed) {
            this.suppressed = suppressed;
            this.markForUpdate();
        }
    }

    @Override
    protected boolean readFromStream(final ByteBuf data) throws IOException {
        final boolean changed = super.readFromStream(data);
        this.suppressed = data.readBoolean();
        return changed;
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeBoolean(this.suppressed);
    }
}
