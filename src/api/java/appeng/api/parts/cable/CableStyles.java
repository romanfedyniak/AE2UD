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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMap;

import net.minecraft.util.ResourceLocation;

/**
 * The cable styles the cable bus can draw.
 * <p>
 * Register during client pre-initialisation: every style's textures are stitched into the block atlas when the
 * cable bus model is loaded, and one that arrives later has nothing to draw with.
 */
public final class CableStyles {

    /** AE2's own cables, and what a cable that names no style is drawn as. */
    public static final ResourceLocation DEFAULT = new ResourceLocation("appliedenergistics2", "default");

    private static volatile Map<ResourceLocation, CableStyle> styles = ImmutableMap.of();

    private CableStyles() {
    }

    public static synchronized void register(final CableStyle style) {
        final Map<ResourceLocation, CableStyle> next = new LinkedHashMap<>(styles);
        next.put(style.getId(), style);
        styles = ImmutableMap.copyOf(next);
    }

    public static Collection<CableStyle> getAll() {
        return styles.values();
    }

    @Nullable
    public static CableStyle get(final ResourceLocation id) {
        return styles.get(id);
    }

    /** The style of that id, or AE2's own where an addon's style is not registered on this client. */
    public static CableStyle getOrDefault(@Nullable final ResourceLocation id) {
        final CableStyle style = id == null ? null : styles.get(id);
        return style != null ? style : styles.get(DEFAULT);
    }
}
