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


import appeng.core.localization.GuiText;

/**
 * How long the span is that a monitor's throughput is quoted over, and how much history it is averaged from.
 * <p>
 * The two go together on purpose. A rate quoted per hour is read by someone watching a slow process and wants
 * to be steady, so it looks at the whole minute of history; a rate quoted per tick is read by someone watching
 * a machine start and stop, so it looks at five seconds and is allowed to jump. Neither waits for its own span
 * to elapse: an hourly rate measures a minute and multiplies, or a monitor placed a moment ago would have
 * nothing to say for an hour.
 */
public enum ThroughputUnit {

    OFF(0, 0, GuiText.MonitorOff),
    TICK(1, 5, GuiText.MonitorPerTick),
    SECOND(20, 5, GuiText.MonitorPerSecond),
    MINUTE(1200, ThroughputMeter.BUCKETS, GuiText.MonitorPerMinute),
    HOUR(72000, ThroughputMeter.BUCKETS, GuiText.MonitorPerHour);

    private final int ticks;
    private final int window;
    private final GuiText label;

    ThroughputUnit(final int ticks, final int window, final GuiText label) {
        this.ticks = ticks;
        this.window = window;
        this.label = label;
    }

    /** How many ticks the quoted span is, which is what a per-tick rate is multiplied by. */
    public int getTicks() {
        return this.ticks;
    }

    /** How many finished seconds the average is taken over. */
    public int getWindow() {
        return this.window;
    }

    public GuiText getLabel() {
        return this.label;
    }

    public ThroughputUnit next() {
        final ThroughputUnit[] all = values();

        return all[(this.ordinal() + 1) % all.length];
    }

    public static ThroughputUnit byOrdinal(final int ordinal) {
        final ThroughputUnit[] all = values();

        return ordinal < 0 || ordinal >= all.length ? OFF : all[ordinal];
    }
}
