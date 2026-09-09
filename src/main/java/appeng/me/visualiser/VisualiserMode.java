/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.me.visualiser;


import java.util.Locale;

/**
 * What a held visualiser draws. Purely a filter on the client: the same snapshot serves every mode, so
 * switching costs neither a tick on the server nor a byte on the wire.
 */
public enum VisualiserMode {

    /** Nodes, links, and the number of channels on each link. */
    FULL,

    /** The same picture without the numbers, which is the cheapest way to see the whole network at once. */
    NONUM,

    NODES,

    LINKS;

    private static final VisualiserMode[] VALUES = values();

    public VisualiserMode next() {
        return VALUES[(this.ordinal() + 1) % VALUES.length];
    }

    public static VisualiserMode byOrdinal(final int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : FULL;
    }

    public boolean drawsNodes() {
        return this != LINKS;
    }

    public boolean drawsLinks() {
        return this != NODES;
    }

    public boolean drawsNumbers() {
        return this == FULL;
    }

    public String getUnlocalizedName() {
        return "item.appliedenergistics2.network_visualiser.mode." + this.name().toLowerCase(Locale.ROOT);
    }
}
