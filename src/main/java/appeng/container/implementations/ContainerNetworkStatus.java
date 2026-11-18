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


import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IPowerUsageReporter;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEPartLocation;
import appeng.client.gui.implementations.GuiNetworkStatus;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.me.GridInventoryEntry;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;


public class ContainerNetworkStatus extends AEBaseContainer {

    @GuiSync(0)
    public long avgAddition;
    @GuiSync(1)
    public long powerUsage;
    @GuiSync(2)
    public long currentPower;
    @GuiSync(3)
    public long maxPower;
    private IGrid network;
    /** The key of every plain machine picture seen, kept for the screen's life: a key copies its stack's tag
     * and capabilities, and the same few pictures come back for every cable on every refresh. */
    private final Map<Item, Int2ObjectMap<AEItemKey>> plainKeys = new HashMap<>();
    private int delay = 40;
    private GuiNetworkStatus guiNetworkStatus;

    public ContainerNetworkStatus(final InventoryPlayer ip, final INetworkTool te) {
        super(ip, null, null);
        final IGridHost host = te.getGridHost();

        if (host != null) {
            this.findNode(host, AEPartLocation.INTERNAL);
            for (final AEPartLocation d : AEPartLocation.SIDE_LOCATIONS) {
                this.findNode(host, d);
            }
        }

        if (this.network == null && Platform.isServer()) {
            this.setValidContainer(false);
        }
    }

    private AEItemKey keyOf(final ItemStack is) {
        if (is.hasTagCompound()) {
            return AEItemKey.of(is);
        }
        final Int2ObjectMap<AEItemKey> byDamage = this.plainKeys.computeIfAbsent(is.getItem(), item -> new Int2ObjectOpenHashMap<>());
        AEItemKey key = byDamage.get(is.getItemDamage());
        if (key == null) {
            key = AEItemKey.of(is.getItem(), is.getItemDamage());
            byDamage.put(is.getItemDamage(), key);
        }
        return key;
    }

    private void findNode(final IGridHost host, final AEPartLocation d) {
        if (this.network == null) {
            final IGridNode node = host.getGridNode(d);
            if (node != null) {
                this.network = node.getGrid();
            }
        }
    }

    @Override
    public void detectAndSendChanges() {
        this.delay++;
        if (Platform.isServer() && this.delay > 15 && this.network != null) {
            this.delay = 0;

            final IEnergyGrid eg = this.network.getCache(IEnergyGrid.class);
            if (eg != null) {
                this.setAverageAddition((long) (100.0 * eg.getAvgPowerInjection()));
                this.setPowerUsage((long) (100.0 * eg.getAvgPowerUsage()));
                this.setCurrentPower((long) (100.0 * eg.getStoredPower()));
                this.setMaxPower((long) (100.0 * eg.getMaxStoredPower()));
            }

            try {
                final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

                for (final Class<? extends IGridHost> machineClass : this.network.getMachinesClasses()) {
                    // Reuses the ME-update packet for machines, not items: a machine count goes into
                    // storedAmount and that machine's power drain (x100), idle and active, into requestableAmount - see
                    // CONTRACT.md §10/§9's wave 4 prerequisites. Matching representations are merged the
                    // same way the old IItemList.add() merge did: counts and power both summed per key.
                    // Count and power per picture.
                    final Map<AEItemKey, long[]> totals = new LinkedHashMap<>();
                    for (final IGridNode machine : this.network.getMachines(machineClass)) {
                        final IGridBlock blk = machine.getGridBlock();
                        final ItemStack is = blk.getMachineRepresentation();
                        if (!is.isEmpty()) {
                            final AEItemKey key = this.keyOf(is);
                            if (key != null) {
                                final long[] total = totals.computeIfAbsent(key, k -> new long[2]);
                                total[0]++;
                                // What a machine takes while it works, on top of what it declares idle
                                final double active = machine.getMachine() instanceof IPowerUsageReporter
                                        ? ((IPowerUsageReporter) machine.getMachine()).getActivePowerUsage()
                                        : 0;
                                total[1] += (long) ((blk.getIdlePowerUsage() + active) * 100.0);
                            }
                        }
                    }

                    for (final Map.Entry<AEItemKey, long[]> e : totals.entrySet()) {
                        piu.appendItem(new GridInventoryEntry(e.getKey(), e.getValue()[0], e.getValue()[1], false));
                    }
                }

                for (final Object c : this.listeners) {
                    if (c instanceof EntityPlayer) {
                        NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);
                    }
                }
            } catch (final IOException e) {
                // :P
            }
        }
        super.detectAndSendChanges();
    }

    public long getCurrentPower() {
        return this.currentPower;
    }

    private void setCurrentPower(final long currentPower) {
        this.currentPower = currentPower;
    }

    public long getMaxPower() {
        return this.maxPower;
    }

    private void setMaxPower(final long maxPower) {
        this.maxPower = maxPower;
    }

    public long getAverageAddition() {
        return this.avgAddition;
    }

    private void setAverageAddition(final long avgAddition) {
        this.avgAddition = avgAddition;
    }

    public long getPowerUsage() {
        return this.powerUsage;
    }

    private void setPowerUsage(final long powerUsage) {
        this.powerUsage = powerUsage;
    }

    public void postUpdate(final List<GridInventoryEntry> list) {
        this.guiNetworkStatus.postUpdate(list);
    }

    public void setGui(GuiNetworkStatus guiNetworkStatus) {
        this.guiNetworkStatus = guiNetworkStatus;
    }
}
