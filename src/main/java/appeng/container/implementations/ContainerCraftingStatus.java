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


import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.storage.ITerminalHost;
import appeng.container.guisync.GuiSync;
import net.minecraft.entity.player.InventoryPlayer;

import javax.annotation.Nullable;
import java.util.List;


public class ContainerCraftingStatus extends ContainerCraftingCPU implements ICraftingCPUTableHost {

    private final CraftingCPUTable cpuTable = new CraftingCPUTable(this, this);

    @GuiSync(5)
    public int selectedCpuSerial = -1;

    public ContainerCraftingStatus(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }

    @Override
    public void detectAndSendChanges() {
        this.cpuTable.detectAndSendChanges(this.getNetwork());

        super.detectAndSendChanges();
    }

    @Override
    public CraftingCPUTable getCPUTable() {
        return this.cpuTable;
    }

    @Override
    public int getSelectedCpuSerial() {
        return this.selectedCpuSerial;
    }

    @Override
    public void setSelectedCpuSerial(final int serial) {
        this.selectedCpuSerial = serial;
    }

    /**
     * The status screen watches CPUs rather than picking one for a job, so every CPU belongs in the list.
     */
    @Override
    public boolean cpuMatches(final ICraftingCPU cpu) {
        return true;
    }

    @Override
    public void onCpuSelected(@Nullable final ICraftingCPU cpu) {
        if (cpu != this.getMonitor()) {
            this.setCPU(cpu);
        }
    }

    public void selectCPU(final int serial) {
        this.cpuTable.selectCPU(serial);
    }

    public List<CraftingCPUStatus> getCPUs() {
        return this.cpuTable.getCPUs();
    }

    public void postCPUUpdate(final CraftingCPUStatus[] cpus) {
        this.cpuTable.postCPUUpdate(cpus);
    }
}
