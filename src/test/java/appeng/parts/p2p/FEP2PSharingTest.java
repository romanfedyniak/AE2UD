/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.parts.p2p;


import java.util.Arrays;
import java.util.List;

import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** How an FE tunnel input shares what it is offered among its outputs. */
public final class FEP2PSharingTest {

    private static int stored(final List<IEnergyStorage> storages) {
        int total = 0;
        for (final IEnergyStorage s : storages) {
            total += s.getEnergyStored();
        }
        return total;
    }

    @Test
    public void aTrickleIntoManyOutputsHandsOnNoMoreThanWasOffered() {
        final List<IEnergyStorage> outputs = Arrays.asList(new EnergyStorage(100, 100, 100, 100), new EnergyStorage(100),
                new EnergyStorage(100), new EnergyStorage(100), new EnergyStorage(100));
        final int took = PartP2PFEPower.receive(outputs, 3, false);
        assertTrue(took <= 3, "took " + took + " of 3");
        assertEquals(took, stored(outputs) - 100);
    }

    @Test
    public void aSimulationSaysWhatTheRealThingDoes() {
        final List<IEnergyStorage> simulated = Arrays.asList(new EnergyStorage(100, 100, 100, 70), new EnergyStorage(100, 100, 100, 100));
        final List<IEnergyStorage> real = Arrays.asList(new EnergyStorage(100, 100, 100, 70), new EnergyStorage(100, 100, 100, 100));
        final int said = PartP2PFEPower.receive(simulated, 50, true);
        final int did = PartP2PFEPower.receive(real, 50, false);
        assertEquals(did, said);
        assertEquals(30, did);
    }

    @Test
    public void bigBatteriesDoNotAddUpToLessThanNothing() {
        final List<IEnergyStorage> outputs = Arrays.asList(
                new EnergyStorage(Integer.MAX_VALUE, 0, 0, Integer.MAX_VALUE - 10),
                new EnergyStorage(Integer.MAX_VALUE, 0, 0, Integer.MAX_VALUE - 10));
        assertEquals(Integer.MAX_VALUE, PartP2PFEPower.sum(outputs, false));
        assertEquals(Integer.MAX_VALUE, PartP2PFEPower.sum(outputs, true));
    }
}
