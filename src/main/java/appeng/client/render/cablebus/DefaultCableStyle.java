/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.render.cablebus;

import net.minecraft.util.ResourceLocation;

import appeng.api.parts.cable.CableStyle;
import appeng.api.parts.cable.CableStyles;
import appeng.api.util.AECableCore;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.core.AppEng;

/** AE2's own cables, and the style anything that names none is drawn in. */
public final class DefaultCableStyle implements CableStyle {

    public static final DefaultCableStyle INSTANCE = new DefaultCableStyle();

    private DefaultCableStyle() {
    }

    @Override
    public ResourceLocation getId() {
        return CableStyles.DEFAULT;
    }

    @Override
    public ResourceLocation getConnectionTexture(final AECableType cableType, final AEColor color) {
        final String folder;

        switch (cableType) {
            case GLASS:
                folder = "parts/cable/glass/";
                break;
            case COVERED:
                folder = "parts/cable/covered/";
                break;
            case SMART:
                folder = "parts/cable/smart/";
                break;
            case DENSE_COVERED:
                folder = "parts/cable/dense_covered/";
                break;
            case DENSE_SMART:
                folder = "parts/cable/dense_smart/";
                break;
            default:
                throw new IllegalStateException("Cable type " + cableType + " does not support connections.");
        }

        return new ResourceLocation(AppEng.MOD_ID, folder + color.name().toLowerCase());
    }

    @Override
    public ResourceLocation getCoreTexture(final AECableCore core, final AEColor color) {
        final String folder;

        switch (core) {
            case GLASS:
                folder = "parts/cable/core/glass/";
                break;
            case COVERED:
                folder = "parts/cable/core/covered/";
                break;
            default:
                folder = "parts/cable/core/dense_smart/";
                break;
        }

        return new ResourceLocation(AppEng.MOD_ID, folder + color.name().toLowerCase());
    }
}
