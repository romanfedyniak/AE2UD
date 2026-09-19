/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.parts.cable;

import net.minecraft.util.ResourceLocation;

import appeng.api.util.AECableCore;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;

/**
 * What a cable is made of, as the cable bus draws it: one set of textures for every shape a cable takes and
 * every colour it can be painted. The shapes themselves - where a connection starts, how thick a dense cable is,
 * where the channel lights go - belong to AE2 and are the same for every style.
 * <p>
 * An addon registers one in {@link CableStyles} and names it from its cable part, which is all it takes for the
 * bus to draw that cable in its own textures.
 */
public interface CableStyle {

    ResourceLocation getId();

    /** The sprite along a connection of that shape, in that colour. */
    ResourceLocation getConnectionTexture(AECableType cableType, AEColor color);

    /** The sprite of the block in the middle, in that colour. */
    ResourceLocation getCoreTexture(AECableCore core, AEColor color);
}
