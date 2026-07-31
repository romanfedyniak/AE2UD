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

package appeng.container.implementations;


import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.container.guisync.GuiSync;
import appeng.parts.automation.PartExportBus;
import appeng.parts.automation.PartSharedItemBus;
import net.minecraft.entity.player.InventoryPlayer;


/**
 * The import and export busses. Everything here used to live in {@link ContainerUpgradeable}, which is
 * the shared base for a dozen unrelated screens - the filter layout, the craft-only toggle and the
 * scheduling mode are none of their business.
 *
 * @see appeng.client.gui.implementations.GuiIOBus
 */
public class ContainerIOBus extends ContainerUpgradeable {

    @GuiSync(5)
    public YesNo cMode = YesNo.NO;

    @GuiSync(6)
    public SchedulingMode schedulingMode = SchedulingMode.DEFAULT;

    public ContainerIOBus(final InventoryPlayer ip, final PartSharedItemBus te) {
        super(ip, te);
    }

    @Override
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void setupConfig() {
        this.setupExpandableConfig(2, 9, 5);
        this.setupUpgrades();
    }

    @Override
    public int availableUpgrades() {
        return 5;
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        return this.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY) > idx;
    }

    @Override
    protected void loadSettingsFromHost(final IConfigManager cm) {
        super.loadSettingsFromHost(cm);

        if (this.getUpgradeable() instanceof PartExportBus) {
            this.setCraftingMode((YesNo) cm.getSetting(Settings.CRAFT_ONLY));
            this.setSchedulingMode((SchedulingMode) cm.getSetting(Settings.SCHEDULING_MODE));
        }
    }

    public YesNo getCraftingMode() {
        return this.cMode;
    }

    public void setCraftingMode(final YesNo cMode) {
        this.cMode = cMode;
    }

    public SchedulingMode getSchedulingMode() {
        return this.schedulingMode;
    }

    private void setSchedulingMode(final SchedulingMode schedulingMode) {
        this.schedulingMode = schedulingMode;
    }
}
