/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.util.math.BlockPos;

class BlockPosUtilsTest {

    @Test
    void distanceAcrossZero() {
        assertEquals(8, BlockPosUtils.getDistance(new BlockPos(5, 0, 0), new BlockPos(-3, 0, 0)));
        assertEquals(8, BlockPosUtils.getDistance(new BlockPos(-3, 0, 0), new BlockPos(5, 0, 0)));
        assertEquals(10 + 3 + 7, BlockPosUtils.getDistance(new BlockPos(0, 64, -2), new BlockPos(10, 61, 5)));
    }

    @Test
    void distanceOnOneSide() {
        assertEquals(2 + 4, BlockPosUtils.getDistance(new BlockPos(5, 10, 0), new BlockPos(3, 14, 0)));
        assertEquals(3, BlockPosUtils.getDistance(new BlockPos(-5, 0, -1), new BlockPos(-2, 0, -1)));
    }
}
