/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.patterns;

import java.util.Objects;

/**
 * One inventory a {@link PatternEncodingMode} edits. Every pattern terminal holds one of each, saved with the
 * terminal, and shows it as ghost slots while the mode is on screen.
 */
public final class PatternGrid {

    public enum Role {
        /** What the pattern takes. */
        INPUT,
        /** What the pattern makes. */
        OUTPUT
    }

    private final String name;
    private final int size;
    private final Role role;
    private final boolean itemsOnly;

    private PatternGrid(final String name, final int size, final Role role, final boolean itemsOnly) {
        if (size <= 0) {
            throw new IllegalArgumentException("A grid needs at least one slot: " + name);
        }
        this.name = Objects.requireNonNull(name);
        this.size = size;
        this.role = Objects.requireNonNull(role);
        this.itemsOnly = itemsOnly;
    }

    /** Slots that take any kind of key, fluids included, with an amount. */
    public static PatternGrid of(final String name, final int size, final Role role) {
        return new PatternGrid(name, size, role, false);
    }

    /** Slots that take items only, one of each, as a crafting grid does. */
    public static PatternGrid itemsOnly(final String name, final int size, final Role role) {
        return new PatternGrid(name, size, role, true);
    }

    /** Unique within its mode; the terminal saves the grid under it. */
    public String getName() {
        return this.name;
    }

    public int getSize() {
        return this.size;
    }

    public Role getRole() {
        return this.role;
    }

    public boolean isItemsOnly() {
        return this.itemsOnly;
    }
}
