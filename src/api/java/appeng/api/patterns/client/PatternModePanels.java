/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.patterns.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMap;

import net.minecraft.util.ResourceLocation;

import appeng.api.patterns.PatternEncodingMode;

/**
 * Where a mode's screen side is registered, from the client alone: a mod that registers a
 * {@link PatternEncodingMode} on both sides registers its panel here in its client proxy.
 * <p>
 * A mode with no panel is still encoded and still keeps its grids; it simply cannot be shown, which is what a
 * terminal opened on a server-only mode falls back to.
 */
public final class PatternModePanels {

    private static volatile Map<ResourceLocation, BiFunction<PatternEncodingMode, IPatternTerminalScreen, PatternModePanel>> panels = ImmutableMap
            .of();

    private PatternModePanels() {
    }

    public static synchronized void register(final ResourceLocation mode,
            final BiFunction<PatternEncodingMode, IPatternTerminalScreen, PatternModePanel> factory) {
        final Map<ResourceLocation, BiFunction<PatternEncodingMode, IPatternTerminalScreen, PatternModePanel>> next = new HashMap<>(
                panels);
        next.put(mode, factory);
        panels = ImmutableMap.copyOf(next);
    }

    /** @return the panel for that mode on that screen, or null where the mode registered none. */
    @Nullable
    public static PatternModePanel create(final PatternEncodingMode mode, final IPatternTerminalScreen screen) {
        final BiFunction<PatternEncodingMode, IPatternTerminalScreen, PatternModePanel> factory = panels
                .get(mode.getId());
        return factory == null ? null : factory.apply(mode, screen);
    }
}
