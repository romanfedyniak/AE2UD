package appeng.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UpgradeSpeedCalculationsTest {

    @Test
    void preservesStandardItemBusSpeedsAndExtendsLinearly() {
        assertEquals(1, UpgradeSpeedCalculations.itemBusOperations(0));
        assertEquals(8, UpgradeSpeedCalculations.itemBusOperations(1));
        assertEquals(32, UpgradeSpeedCalculations.itemBusOperations(2));
        assertEquals(64, UpgradeSpeedCalculations.itemBusOperations(3));
        assertEquals(96, UpgradeSpeedCalculations.itemBusOperations(4));
        assertEquals(128, UpgradeSpeedCalculations.itemBusOperations(5));
        assertEquals(Integer.MAX_VALUE, UpgradeSpeedCalculations.itemBusOperations(Integer.MAX_VALUE));
    }

    @Test
    void ioPortSpeedUsesSaturatedPowersOfTwo() {
        assertEquals(256, UpgradeSpeedCalculations.ioPortTransferLimit(0));
        assertEquals(512, UpgradeSpeedCalculations.ioPortTransferLimit(1));
        assertEquals(2_048, UpgradeSpeedCalculations.ioPortTransferLimit(3));
        assertEquals(Long.MAX_VALUE, UpgradeSpeedCalculations.ioPortTransferLimit(100));
    }

    @Test
    void linearSpeedSaturates() {
        assertEquals(1, UpgradeSpeedCalculations.linearSpeed(0));
        assertEquals(101, UpgradeSpeedCalculations.linearSpeed(100));
        assertEquals(Integer.MAX_VALUE, UpgradeSpeedCalculations.linearSpeed(Integer.MAX_VALUE));
    }

    @Test
    void assemblerKeepsItsStandardCurveAndContinuesAfterFivePoints() {
        final int[] standard = { 10, 13, 17, 20, 25, 50 };
        for (int points = 0; points < standard.length; points++) {
            assertEquals(standard[points], UpgradeSpeedCalculations.molecularAssemblerSpeed(points));
        }
        assertEquals(75, UpgradeSpeedCalculations.molecularAssemblerSpeed(6));
        assertEquals(Integer.MAX_VALUE,
                UpgradeSpeedCalculations.molecularAssemblerSpeed(Integer.MAX_VALUE));
    }
}
