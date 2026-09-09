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


import java.util.Arrays;

/**
 * What has moved lately, in one-second buckets.
 * <p>
 * A rate taken from the last measurement alone dances too much to read - the number on the wall changes
 * faster than a player can take it in, and a single busy tick throws it across the whole display. So the
 * measurements are kept in a ring a minute long and read back over a window: a short one for a rate that
 * should react, a long one for a rate that should be steady.
 * <p>
 * The bucket being filled right now is deliberately left out of the average. It holds part of a second, and
 * counting a partial second against a whole one drags every reading down; it is used only before there is a
 * finished bucket to read, so that a meter just switched on says something rather than zero.
 */
public final class ThroughputMeter {

    public static final int BUCKET_TICKS = 20;
    public static final int BUCKETS = 60;

    private final long[] in = new long[BUCKETS];
    private final long[] out = new long[BUCKETS];

    private int head;
    private int finished;
    private int ticksIntoBucket;

    public void add(final long inserted, final long extracted) {
        this.in[this.head] += inserted;
        this.out[this.head] += extracted;
    }

    /**
     * Moves time on. Takes however many ticks have passed rather than assuming one, because a part is ticked
     * at whatever rate the network feels like and may have been asleep.
     */
    public void advance(final int ticks) {
        // Longer than the ring holds and there is nothing left to keep, however long it was. A monitor woken
        // after a night asleep would otherwise wind the ring forward one bucket at a time.
        if (ticks >= BUCKETS * BUCKET_TICKS) {
            this.clear();
            return;
        }

        this.ticksIntoBucket += ticks;

        while (this.ticksIntoBucket >= BUCKET_TICKS) {
            this.ticksIntoBucket -= BUCKET_TICKS;
            this.head = (this.head + 1) % BUCKETS;
            this.in[this.head] = 0;
            this.out[this.head] = 0;
            this.finished = Math.min(this.finished + 1, BUCKETS);
        }
    }

    public void clear() {
        Arrays.fill(this.in, 0);
        Arrays.fill(this.out, 0);
        this.head = 0;
        this.finished = 0;
        this.ticksIntoBucket = 0;
    }

    /** How much arrived per tick over the last {@code window} buckets. */
    public double inRate(final int window) {
        return this.rate(this.in, window);
    }

    /** How much left per tick over the last {@code window} buckets. */
    public double outRate(final int window) {
        return this.rate(this.out, window);
    }

    private double rate(final long[] samples, final int window) {
        final int count = Math.min(Math.min(window, this.finished), BUCKETS - 1);

        if (count == 0) {
            // Nothing has finished yet. The bucket in hand is all there is, and it is worth reading only
            // once some of it has actually elapsed.
            return this.ticksIntoBucket == 0 ? 0 : samples[this.head] / (double) this.ticksIntoBucket;
        }

        long total = 0;

        for (int i = 1; i <= count; i++) {
            total += samples[Math.floorMod(this.head - i, BUCKETS)];
        }

        return total / (double) (count * BUCKET_TICKS);
    }
}
