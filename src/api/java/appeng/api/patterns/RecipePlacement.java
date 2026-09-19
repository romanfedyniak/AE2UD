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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import appeng.api.stacks.GenericStack;

/**
 * Where a {@link PatternEncodingMode} puts each part of a {@link TransferredRecipe}. The terminal then fills
 * every slot of the mode's grids: a slot given alternatives takes the first the network has or can craft, and
 * a slot given nothing is emptied.
 */
public final class RecipePlacement {

    private final Map<String, Map<Integer, List<GenericStack>>> placed = new LinkedHashMap<>();

    /** Places one slot; the first alternative is preferred. Replaces whatever was placed there before. */
    public RecipePlacement put(final String grid, final int slot, final List<GenericStack> alternatives) {
        if (!alternatives.isEmpty()) {
            this.placed.computeIfAbsent(grid, g -> new LinkedHashMap<>()).put(slot, ImmutableList.copyOf(alternatives));
        }
        return this;
    }

    public RecipePlacement put(final String grid, final int slot, final GenericStack stack) {
        return this.put(grid, slot, Collections.singletonList(stack));
    }

    /** What was placed in one grid, by slot. */
    public Map<Integer, List<GenericStack>> get(final String grid) {
        return this.placed.getOrDefault(grid, Collections.emptyMap());
    }

    /** How many slots of one grid were given something. */
    public int count(final String grid) {
        return this.get(grid).size();
    }
}
