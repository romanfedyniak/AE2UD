/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.implementations;

import java.util.Set;

import javax.annotation.Nullable;

import appeng.api.stacks.AEKeyType;
import appeng.api.util.AutoExport;
import appeng.api.util.IOrientable;
import appeng.api.util.RelativeSide;

/**
 * A machine that pushes what it makes into the blocks beside it, through the faces a player has chosen. Only a
 * tile entity implements this: {@link AutoExport} reads its world and position.
 */
public interface IAutoExportHost extends IOrientable {

    AutoExport getAutoExport();

    /** What the machine pushes out. A face is offered only when the block there takes one of these. */
    Set<AEKeyType> getAutoExportTypes();

    /** False for a face the machine keeps for something else, such as the Inscriber's plates. */
    default boolean canAutoExportTo(final RelativeSide side) {
        return true;
    }

    /** The translation key saying why a face is refused, or null to say nothing. */
    @Nullable
    default String getAutoExportRefusal(final RelativeSide side) {
        return null;
    }
}
