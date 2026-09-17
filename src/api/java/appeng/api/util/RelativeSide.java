/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.util;

import net.minecraft.util.EnumFacing;

/**
 * A face of a block named from the block's own point of view, so a setting kept per face turns with the block.
 * Left and right are as a player standing in front of it sees them.
 */
public enum RelativeSide {
    TOP,
    LEFT,
    FRONT,
    RIGHT,
    BOTTOM,
    BACK;

    public int mask() {
        return 1 << this.ordinal();
    }

    public EnumFacing toFacing(final EnumFacing forward, final EnumFacing up) {
        switch (this) {
            case FRONT:
                return forward;
            case BACK:
                return forward.getOpposite();
            case TOP:
                return up;
            case BOTTOM:
                return up.getOpposite();
            case RIGHT:
                return right(forward, up);
            default:
                return right(forward, up).getOpposite();
        }
    }

    /** The viewer's right: the way they look, which is back along the front, crossed with up. */
    private static EnumFacing right(final EnumFacing forward, final EnumFacing up) {
        final int lookX = -forward.getXOffset();
        final int lookY = -forward.getYOffset();
        final int lookZ = -forward.getZOffset();
        final int x = lookY * up.getZOffset() - lookZ * up.getYOffset();
        final int y = lookZ * up.getXOffset() - lookX * up.getZOffset();
        final int z = lookX * up.getYOffset() - lookY * up.getXOffset();
        if (x == 0 && y == 0 && z == 0) {
            // A front and a top on one axis have no right; any face across them will do
            return forward.getAxis() == EnumFacing.Axis.Y ? EnumFacing.EAST : EnumFacing.UP;
        }
        return EnumFacing.getFacingFromVector(x, y, z);
    }
}
