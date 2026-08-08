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

import javax.annotation.Nullable;


/**
 * A container that shows the crafting CPU table. The selected serial lives on the container rather than on
 * the {@link CraftingCPUTable} itself because it is what travels to the client, through the container's own
 * {@link appeng.container.guisync.GuiSync} field.
 */
public interface ICraftingCPUTableHost {

    CraftingCPUTable getCPUTable();

    int getSelectedCpuSerial();

    void setSelectedCpuSerial(int serial);

    /**
     * Which CPUs this screen offers. The status screen shows every CPU; a screen that submits a job shows
     * only the ones that could take it.
     */
    boolean cpuMatches(ICraftingCPU cpu);

    /**
     * Whether "no CPU chosen" is an answer in its own right - it means the network picks one when the job is
     * submitted. Screens that only watch a CPU have nothing to leave to the network, so they select one for
     * the player instead.
     */
    default boolean allowsAutomaticCpu() {
        return false;
    }

    default void onCpuSelected(@Nullable final ICraftingCPU cpu) {
    }
}
