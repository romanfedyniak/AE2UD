/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.me.helpers;

import net.minecraft.world.World;

import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IPowerUsageReporter;

/**
 * What a machine drew from its network over the last second, for {@link IPowerUsageReporter}. One slot per
 * tick, so recording costs an array write and nothing ages out by itself.
 */
public final class PowerUsageMeter {

    private static final int WINDOW = 20;

    private final double[] drawn = new double[WINDOW];
    private final long[] ticks = new long[WINDOW];

    /**
     * @param extracted  what {@code extractAEPower} returned.
     * @param multiplier the multiplier it was called with, so the meter counts what the network lost.
     */
    public void record(final World world, final double extracted, final PowerMultiplier multiplier) {
        if (world == null || extracted <= 0) {
            return;
        }
        final long now = world.getTotalWorldTime();
        final int slot = (int) Math.floorMod(now, (long) WINDOW);
        if (this.ticks[slot] != now) {
            this.ticks[slot] = now;
            this.drawn[slot] = 0;
        }
        this.drawn[slot] += multiplier.multiply(extracted);
    }

    /** AE per tick over the last second. */
    public double average(final World world) {
        if (world == null) {
            return 0;
        }
        final long now = world.getTotalWorldTime();
        double sum = 0;
        for (int slot = 0; slot < WINDOW; slot++) {
            final long age = now - this.ticks[slot];
            if (age >= 0 && age < WINDOW) {
                sum += this.drawn[slot];
            }
        }
        return sum / WINDOW;
    }
}
