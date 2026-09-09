/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.reporting;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * The meter is the one piece of the throughput monitor that can be driven without a world: it is arithmetic
 * over a ring of samples, and everything a player complains about - a number that lags, a number that reads
 * half what it should - is decided here.
 */
class ThroughputMeterTest {

    /** Runs a steady rate for a number of seconds, a tick at a time. */
    private static ThroughputMeter running(final long inPerTick, final long outPerTick, final int seconds) {
        final ThroughputMeter meter = new ThroughputMeter();

        for (int tick = 0; tick < seconds * ThroughputMeter.BUCKET_TICKS; tick++) {
            meter.add(inPerTick, outPerTick);
            meter.advance(1);
        }

        return meter;
    }

    @Test
    void steadyFlowReadsBackAtTheRateItWasFedIn() {
        final ThroughputMeter meter = running(7, 3, 10);

        assertEquals(7.0, meter.inRate(5), 1e-9);
        assertEquals(3.0, meter.outRate(5), 1e-9);
        assertEquals(7.0, meter.inRate(ThroughputMeter.BUCKETS), 1e-9);
    }

    /**
     * The bucket in hand is part of a second. Counting it as a whole one is what would make a steady line read
     * low, so it is left out - and this is the case that would catch it, because half a bucket is fed here.
     */
    @Test
    void thePartlyFilledBucketDoesNotDragTheAverageDown() {
        final ThroughputMeter meter = running(10, 0, 3);

        for (int tick = 0; tick < 10; tick++) {
            meter.add(10, 0);
            meter.advance(1);
        }

        assertEquals(10.0, meter.inRate(5), 1e-9);
    }

    /** A meter switched on a moment ago has to say something, or "slow" is indistinguishable from "stopped". */
    @Test
    void readsThePartialBucketWhileNothingHasFinished() {
        final ThroughputMeter meter = new ThroughputMeter();

        for (int tick = 0; tick < 5; tick++) {
            meter.add(4, 0);
            meter.advance(1);
        }

        assertEquals(4.0, meter.inRate(5), 1e-9);
    }

    @Test
    void saysNothingBeforeAnyTimeHasPassed() {
        final ThroughputMeter meter = new ThroughputMeter();
        meter.add(1000, 1000);

        assertEquals(0.0, meter.inRate(5), 1e-9);
        assertEquals(0.0, meter.outRate(5), 1e-9);
    }

    /**
     * A short window has to react and a long one has to hold steady - which is the whole reason the unit picks
     * the window. A line that has been running a minute and stops is nearly gone from the five-second average
     * while the minute average still remembers most of it.
     */
    @Test
    void theShortWindowForgetsFasterThanTheLongOne() {
        final ThroughputMeter meter = running(10, 0, 60);

        for (int tick = 0; tick < 5 * ThroughputMeter.BUCKET_TICKS; tick++) {
            meter.advance(1);
        }

        assertEquals(0.0, meter.inRate(5), 1e-9);
        assertTrue(meter.inRate(ThroughputMeter.BUCKETS) > 8.0,
                "a minute of history should still remember a line that stopped five seconds ago");
    }

    /** A part is ticked at whatever rate the network feels like, and may have been asleep for longer. */
    @Test
    void jumpingSeveralBucketsAtOnceLosesNothingButTheBucketsJumped() {
        final ThroughputMeter meter = new ThroughputMeter();

        meter.add(200, 0);
        meter.advance(3 * ThroughputMeter.BUCKET_TICKS);

        // 200 arrived in the first second, and two empty seconds followed: 200 over three seconds.
        assertEquals(200.0 / (3 * ThroughputMeter.BUCKET_TICKS), meter.inRate(5), 1e-9);
    }

    /** A monitor switched on again after a long sleep has nothing to report, and must not walk there. */
    @Test
    void aJumpLongerThanTheRingLeavesNothingBehind() {
        final ThroughputMeter meter = running(50, 50, 30);

        meter.advance(BUCKETS_OF_TICKS * 100);

        assertEquals(0.0, meter.inRate(ThroughputMeter.BUCKETS), 1e-9);
        assertEquals(0.0, meter.outRate(5), 1e-9);
    }

    private static final int BUCKETS_OF_TICKS = ThroughputMeter.BUCKETS * ThroughputMeter.BUCKET_TICKS;

    @Test
    void aRingOlderThanItselfKeepsOnlyWhatItCanHold() {
        final ThroughputMeter meter = running(5, 0, ThroughputMeter.BUCKETS * 3);

        assertEquals(5.0, meter.inRate(ThroughputMeter.BUCKETS), 1e-9);

        meter.clear();

        assertEquals(0.0, meter.inRate(ThroughputMeter.BUCKETS), 1e-9);
    }
}
