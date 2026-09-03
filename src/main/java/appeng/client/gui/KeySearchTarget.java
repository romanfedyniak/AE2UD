/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui;

import java.awt.Rectangle;
import java.util.function.Consumer;

import appeng.api.stacks.AEKey;

/**
 * A search box a key's name can be put into, and where it sits on screen.
 * <p>
 * Two things reach a search box this way - an ingredient dragged out of HEI, and an item held on the cursor -
 * and both want the same two answers: which rectangle, and what to write. A screen says that once, in
 * {@link AEBaseGui#getKeySearchTargets()}, instead of once per way in.
 * <p>
 * The rectangle is in screen coordinates, because that is what both callers have. The screen builds it: the
 * two field classes in this mod share no supertype, and one screen keeps its field in the window's own
 * coordinates while the rest use the screen's.
 */
public final class KeySearchTarget {

    private final Rectangle area;
    private final Consumer<AEKey> fill;

    public KeySearchTarget(final Rectangle area, final Consumer<AEKey> fill) {
        this.area = area;
        this.fill = fill;
    }

    public Rectangle getArea() {
        return this.area;
    }

    public boolean contains(final int x, final int y) {
        return this.area.contains(x, y);
    }

    public void accept(final AEKey what) {
        this.fill.accept(what);
    }
}
