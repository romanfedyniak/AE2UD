/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.util;

import com.google.common.math.IntMath;
import com.google.common.math.LongMath;

public final class UpgradeSpeedCalculations {

    private UpgradeSpeedCalculations() {
    }

    public static int itemBusOperations(final int speedPoints) {
        if (speedPoints <= 0) {
            return 1;
        }
        if (speedPoints == 1) {
            return 8;
        }
        return IntMath.saturatedMultiply(32, speedPoints - 1);
    }

    public static long ioPortTransferLimit(final int speedPoints) {
        return LongMath.saturatedMultiply(256, LongMath.saturatedPow(2, Math.max(0, speedPoints)));
    }

    public static int linearSpeed(final int speedPoints) {
        return IntMath.saturatedAdd(1, Math.max(0, speedPoints));
    }

    public static int molecularAssemblerSpeed(final int speedPoints) {
        switch (speedPoints) {
            case 0:
                return 10;
            case 1:
                return 13;
            case 2:
                return 17;
            case 3:
                return 20;
            case 4:
                return 25;
            case 5:
                return 50;
            default:
                return speedPoints < 0 ? 10
                        : IntMath.saturatedAdd(50, IntMath.saturatedMultiply(25, speedPoints - 5));
        }
    }
}
