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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.IConfigManager;
import appeng.container.guisync.GuiSync;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.parts.automation.PartExportBus;
import appeng.parts.automation.PartSharedItemBus;
import appeng.util.Platform;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.items.IItemHandler;


/**
 * The import and export busses. Everything here used to live in {@link ContainerUpgradeable}, which is
 * the shared base for a dozen unrelated screens - the filter layout, the craft-only toggle and the
 * scheduling mode are none of their business.
 *
 * @see appeng.client.gui.implementations.GuiIOBus
 */
public class ContainerIOBus extends ContainerUpgradeable {

    private static final String SEPARATOR = ",";
    private static final String ASSIGN = "=";

    /**
     * How often the network is re-read for the filter tooltips. Amounts move constantly in a live network,
     * and this is a tooltip - half a second late is not wrong, and a packet every tick would be.
     */
    private static final int STORED_REFRESH_TICKS = 10;

    @GuiSync(5)
    public YesNo cMode = YesNo.NO;

    @GuiSync(6)
    public SchedulingMode schedulingMode = SchedulingMode.DEFAULT;

    /**
     * How much of each configured key the network holds, as {@code slot=amount} pairs. Only slots that
     * carry a key appear.
     */
    @GuiSync(11)
    public String stored = "";

    private int ticksToStoredRefresh = 0;

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
        return this.getUpgradeable().getInstalledCapacityPoints() > idx;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer() && --this.ticksToStoredRefresh <= 0) {
            this.ticksToStoredRefresh = STORED_REFRESH_TICKS;
            this.stored = this.readStoredAmounts();
        }

        super.detectAndSendChanges();
    }

    private String readStoredAmounts() {
        final AENetworkProxy proxy = ((PartSharedItemBus) this.getUpgradeable()).getProxy();

        // A bus cut off from its network still forms a grid of its own, and that grid honestly holds
        // nothing - so asking it gives "0" rather than an error. There is no number to report here, and
        // "Stored: 0" is a claim about the network that this bus is in no position to make.
        if (!proxy.isActive()) {
            return "";
        }

        final KeyCounter inventory;
        try {
            inventory = proxy.getStorage().getCachedInventory();
        } catch (final GridAccessException e) {
            return "";
        }

        final IItemHandler config = this.getUpgradeable().getInventoryByName("config");
        final List<String> entries = new ArrayList<>();
        for (int x = 0; x < config.getSlots(); x++) {
            final GenericStack configured = GenericStack.resolveItemStack(config.getStackInSlot(x));
            if (configured != null) {
                entries.add(x + ASSIGN + inventory.get(configured.what()));
            }
        }
        return String.join(SEPARATOR, entries);
    }

    /**
     * What the network holds for each configured slot, keyed by slot index. Empty on the server, which
     * reads {@link #readStoredAmounts()} directly.
     */
    public Map<Integer, Long> getStoredAmounts() {
        final Map<Integer, Long> out = new HashMap<>();
        for (final String entry : this.stored.split(SEPARATOR)) {
            final int split = entry.indexOf(ASSIGN);
            if (split <= 0) {
                continue;
            }
            try {
                out.put(Integer.parseInt(entry.substring(0, split)), Long.parseLong(entry.substring(split + 1)));
            } catch (final NumberFormatException ignored) {
            }
        }
        return out;
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
