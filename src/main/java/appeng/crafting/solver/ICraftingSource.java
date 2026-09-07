/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.crafting.solver;


import appeng.api.stacks.AEKey;

import java.util.Collections;
import java.util.List;


/**
 * Everything the solver may ask about a network, and nothing more.
 * <p>
 * Deliberately this small: what is in storage arrives as a snapshot instead, so a solve is a pure function
 * of its inputs and a test can build one without a world, a grid or an item registry.
 */
public interface ICraftingSource {

    /**
     * Every pattern that makes this key, most preferred first. Empty when nothing does.
     */
    List<SolverPattern> patternsFor(AEKey what);

    /**
     * Whether a level emitter promises this key. Such a key is never crafted, whatever patterns exist.
     */
    boolean canEmit(AEKey what);

    /**
     * A network that can make nothing - the base case for a test that only cares about storage.
     */
    ICraftingSource NOTHING = new ICraftingSource() {

        @Override
        public List<SolverPattern> patternsFor(final AEKey what) {
            return Collections.emptyList();
        }

        @Override
        public boolean canEmit(final AEKey what) {
            return false;
        }
    };
}
