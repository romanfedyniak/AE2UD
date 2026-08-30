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

package appeng.helpers;


import appeng.api.config.LevelType;
import appeng.api.config.Settings;
import appeng.api.stacks.GenericStack;
import appeng.container.implementations.ContainerSetAmount;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.parts.automation.PartLevelEmitter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;


/**
 * The threshold a level emitter watches for.
 * <p>
 * The window shows what is being watched rather than the emitter, so that a threshold on a fluid is typed
 * in buckets - the unit comes from the filter. Watching energy, or watching nothing yet, leaves the
 * emitter's own item there and the threshold reads in plain numbers.
 */
public class LevelAmountTarget implements IAmountTarget {

    private final PartLevelEmitter emitter;

    public LevelAmountTarget(final PartLevelEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public ItemStack getIcon() {
        if (this.emitter.getConfigManager().getSetting(Settings.LEVEL_TYPE) != LevelType.ENERGY_LEVEL) {
            final GenericStack filter = GenericStack
                    .resolveItemStack(this.emitter.getInventoryByName("config").getStackInSlot(0));

            if (filter != null) {
                return filter.what().wrapForDisplayOrFilter();
            }
        }

        return this.emitter.getItemStackRepresentation();
    }

    @Override
    public long getAmount() {
        return this.emitter.getReportingValue();
    }

    @Override
    public long getMinAmount() {
        return 0;
    }

    @Override
    public long getMaxAmount() {
        return Long.MAX_VALUE;
    }

    @Override
    public void apply(final EntityPlayer player, final ContainerSetAmount from, final long amount) {
        this.emitter.setReportingValue(amount);

        PacketSwitchGuis.reopen(player, from, this.emitter.getGuiBridge());
    }
}
