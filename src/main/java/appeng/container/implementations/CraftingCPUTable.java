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


import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.container.AEBaseContainer;
import appeng.core.AELog;
import appeng.core.sync.packets.PacketCraftingCPUsUpdate;
import appeng.util.Platform;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;


/**
 * The list of crafting CPUs behind a CPU table, shared by every screen that shows one. Serials, not list
 * positions, name a CPU: the list is rebuilt whenever the network changes, and a position means a different
 * CPU as soon as one is added or removed.
 */
public class CraftingCPUTable {

    private static final int REFRESH_INTERVAL = 20;

    private static final Comparator<CraftingCPUStatus> CPU_COMPARATOR = Comparator
            .comparing((CraftingCPUStatus e) -> e.getName() == null || e.getName().isEmpty())
            .thenComparing(e -> e.getName() != null ? e.getName() : "")
            .thenComparingInt(CraftingCPUStatus::getSerial);

    private final AEBaseContainer container;
    private final ICraftingCPUTableHost host;

    private final WeakHashMap<ICraftingCPU, Integer> cpuSerialMap = new WeakHashMap<>();
    private int nextCpuSerial = 1;

    private ImmutableSet<ICraftingCPU> lastCpuSet = null;
    private List<CraftingCPUStatus> cpus = new ArrayList<>();
    private int lastUpdate = 0;
    private boolean rebuildWanted = true;

    public CraftingCPUTable(final AEBaseContainer container, final ICraftingCPUTableHost host) {
        this.container = container;
        this.host = host;
    }

    /**
     * Asks for a rebuild on the next tick. Needed when what {@link ICraftingCPUTableHost#cpuMatches} answers
     * changed without the network changing - a job finishing its calculation moves the byte count every CPU
     * is measured against.
     */
    public void invalidate() {
        this.rebuildWanted = true;
    }

    public void detectAndSendChanges(@Nullable final IGrid network) {
        if (Platform.isServer() && network != null) {
            final ICraftingGrid cc = network.getCache(ICraftingGrid.class);
            final ImmutableSet<ICraftingCPU> cpuSet = cc.getCpus();

            // Update at least once a second
            ++this.lastUpdate;
            if (this.rebuildWanted || !cpuSet.equals(this.lastCpuSet) || this.lastUpdate > REFRESH_INTERVAL) {
                this.rebuildWanted = false;
                this.lastUpdate = 0;
                this.lastCpuSet = cpuSet;
                this.updateCpuList();
                this.sendCPUs();
            }
        }

        // Clear selection if CPU is no longer in list
        final int selected = this.host.getSelectedCpuSerial();
        if (selected != -1 && this.cpus.stream().noneMatch(c -> c.getSerial() == selected)) {
            this.selectCPU(-1);
        }

        // Select a suitable CPU if none is selected, unless leaving the choice to the network is itself an option
        if (this.host.getSelectedCpuSerial() == -1 && !this.host.allowsAutomaticCpu()) {
            // Try busy CPUs first
            for (final CraftingCPUStatus cpu : this.cpus) {
                if (cpu.getRemainingItems() > 0) {
                    this.selectCPU(cpu.getSerial());
                    break;
                }
            }
            // If we couldn't find a busy one, just select the first
            if (this.host.getSelectedCpuSerial() == -1 && !this.cpus.isEmpty()) {
                this.selectCPU(this.cpus.get(0).getSerial());
            }
        }
    }

    public void selectCPU(int serial) {
        if (!Platform.isServer()) {
            return;
        }

        if (serial < -1) {
            serial = -1;
        }

        final int searchedSerial = serial;
        if (serial > -1 && this.cpus.stream().noneMatch(c -> c.getSerial() == searchedSerial)) {
            serial = -1;
        }

        this.host.setSelectedCpuSerial(serial);
        this.host.onCpuSelected(this.findCpu(serial));
    }

    /**
     * The cluster the current selection names, or null when the network is to pick one.
     */
    @Nullable
    public ICraftingCPU getSelectedCpu() {
        return this.findCpu(this.host.getSelectedCpuSerial());
    }

    public List<CraftingCPUStatus> getCPUs() {
        return Collections.unmodifiableList(this.cpus);
    }

    public boolean isEmpty() {
        return this.cpus.isEmpty();
    }

    public void postCPUUpdate(final CraftingCPUStatus[] cpus) {
        this.cpus = new ArrayList<>(Arrays.asList(cpus));
    }

    @Nullable
    private ICraftingCPU findCpu(final int serial) {
        if (serial == -1 || this.lastCpuSet == null) {
            return null;
        }

        for (final ICraftingCPU cpu : this.lastCpuSet) {
            if (this.cpuSerialMap.getOrDefault(cpu, -1) == serial) {
                return cpu;
            }
        }
        return null;
    }

    private void updateCpuList() {
        this.cpus.clear();
        for (final ICraftingCPU cpu : this.lastCpuSet) {
            // A serial is handed out to every CPU, matching or not, so that a CPU keeps its identity across
            // a filter that stops matching it and starts again.
            final int serial = this.cpuSerialMap.computeIfAbsent(cpu, unused -> this.nextCpuSerial++);
            if (this.host.cpuMatches(cpu)) {
                this.cpus.add(new CraftingCPUStatus(cpu, serial));
            }
        }
        this.cpus.sort(CPU_COMPARATOR);
    }

    private void sendCPUs() {
        try {
            this.container.sendPacketToListeners(new PacketCraftingCPUsUpdate(this.cpus));
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }
}
