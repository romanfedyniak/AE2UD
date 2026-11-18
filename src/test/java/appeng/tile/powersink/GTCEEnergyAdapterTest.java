/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.tile.powersink;


import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.util.EnumFacing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;


/**
 * A GregTech cable offering more than the network takes: every EU the network gets has to be paid for by the
 * amps the adapter says it used. Reflective, because GregTech is only on the runtime classpath of the tests.
 */
public final class GTCEEnergyAdapterTest {

    /** Takes a fixed number of EU per tick and hands the rest back. */
    private static final class Sink implements IExternalPowerSink {
        long demand;
        double received;

        @Override
        public double injectExternalPower(final PowerUnits unit, final double amount, final Actionable mode) {
            final double taken = Math.min(amount, this.demand);
            if (mode == Actionable.MODULATE) {
                this.received += taken;
            }
            return amount - taken;
        }

        @Override
        public double getExternalPowerDemand(final PowerUnits unit, final double maxPowerRequired) {
            return Math.min(maxPowerRequired, this.demand);
        }

        @Override
        public double injectAEPower(final double amt, final Actionable mode) {
            return amt;
        }

        @Override
        public double getAEMaxPower() {
            return 0;
        }

        @Override
        public double getAECurrentPower() {
            return 0;
        }

        @Override
        public boolean isAEPublicPowerStorage() {
            return false;
        }

        @Override
        public AccessRestriction getPowerFlow() {
            return AccessRestriction.WRITE;
        }

        @Override
        public double extractAEPower(final double amt, final Actionable mode, final PowerMultiplier usePowerMultiplier) {
            return 0;
        }
    }

    @Test
    public void theNetworkGetsNoMoreThanTheCablePaidFor() throws Exception {
        final Class<?> type = Class.forName("appeng.tile.powersink.GTCEEnergyAdapter");
        final Constructor<?> make = type.getDeclaredConstructor(IExternalPowerSink.class);
        make.setAccessible(true);
        final Method accept = type.getMethod("acceptEnergyFromNetwork", EnumFacing.class, long.class, long.class);

        for (final long demand : new long[] { 10, 50, 100 }) {
            for (final long amps : new long[] { 1, 4 }) {
                final Sink sink = new Sink();
                sink.demand = demand;
                final Object adapter = make.newInstance(sink);

                long paid = 0;
                for (int tick = 0; tick < 50; tick++) {
                    paid += (Long) accept.invoke(adapter, EnumFacing.UP, 32L, amps) * 32;
                }

                final Field buffer = type.getDeclaredField("EUBuffer");
                buffer.setAccessible(true);
                final double buffered = buffer.getDouble(adapter);
                assertEquals(paid, sink.received + buffered, 1e-6,
                        "demand " + demand + " EU/t at 32 V x " + amps + " A: received " + sink.received + ", buffered "
                                + buffered + ", paid " + paid);
            }
        }
    }
}
