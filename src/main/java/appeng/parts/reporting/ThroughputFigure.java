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
 * Which of the three numbers a monitor puts on its face.
 * <p>
 * {@link #NET} is what the network gained or lost, and is the one to trust: it cannot be inflated by anything
 * that takes a stack out and puts it back. {@link #IN} and {@link #OUT} are what actually passed through, which
 * is the only way to see a line that produces and consumes at the same rate - net calls that nothing at all.
 */
public enum ThroughputFigure {

    NET(GuiText.MonitorNet, GuiText.MonitorRateNet),
    IN(GuiText.MonitorIn, GuiText.MonitorRateIn),
    OUT(GuiText.MonitorOut, GuiText.MonitorRateOut);

    private final GuiText label;
    private final GuiText format;

    ThroughputFigure(final GuiText label, final GuiText format) {
        this.label = label;
        this.format = format;
    }

    public GuiText getLabel() {
        return this.label;
    }

    /** The shape of the line on the monitor's face: what goes before the number, and where the unit sits. */
    public GuiText getFormat() {
        return this.format;
    }

    public double valueOf(final double in, final double out) {
        switch (this) {
            case IN:
                return in;
            case OUT:
                return out;
            default:
                return in - out;
        }
    }

    public ThroughputFigure next() {
        final ThroughputFigure[] all = values();

        return all[(this.ordinal() + 1) % all.length];
    }

    public static ThroughputFigure byOrdinal(final int ordinal) {
        final ThroughputFigure[] all = values();

        return ordinal < 0 || ordinal >= all.length ? NET : all[ordinal];
    }
}
