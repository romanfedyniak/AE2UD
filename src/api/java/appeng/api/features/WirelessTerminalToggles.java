/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.features;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMap;

import net.minecraft.util.ResourceLocation;

/**
 * Where an addon registers its {@link WirelessTerminalToggle}s. The buttons appear in the order of registration.
 * Register during mod initialisation, before any screen opens.
 */
public final class WirelessTerminalToggles {

    private static volatile Map<ResourceLocation, WirelessTerminalToggle> toggles = ImmutableMap.of();

    private WirelessTerminalToggles() {
    }

    /**
     * @return false if a toggle with that id was already registered, which is then kept.
     */
    public static synchronized boolean register(final WirelessTerminalToggle toggle) {
        if (toggles.containsKey(toggle.getId())) {
            return false;
        }
        final Map<ResourceLocation, WirelessTerminalToggle> next = new LinkedHashMap<>(toggles);
        next.put(toggle.getId(), toggle);
        toggles = ImmutableMap.copyOf(next);
        return true;
    }

    @Nullable
    public static WirelessTerminalToggle get(final ResourceLocation id) {
        return toggles.get(id);
    }

    public static Collection<WirelessTerminalToggle> getAll() {
        return toggles.values();
    }
}
