/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.patterns;

import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

/**
 * The pattern terminal as a {@link PatternEncodingMode} sees it: the grids it keeps for each mode, and the two
 * substitution toggles every mode may read.
 */
public interface IPatternEncodingHost {

    /**
     * @return the inventory behind one of a mode's {@link PatternGrid}s; never null for a grid the mode declares.
     */
    IItemHandler getEncodingGrid(PatternEncodingMode mode, String grid);

    boolean isSubstitution();

    void setSubstitution(boolean substitute);

    boolean isFluidSubstitution();

    void setFluidSubstitution(boolean substitute);

    World getEncodingWorld();
}
