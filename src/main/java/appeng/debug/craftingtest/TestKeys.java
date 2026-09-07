/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.debug.craftingtest;


import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.init.Items;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Names the things a scenario crafts, without needing an item registered for each of them.
 * <p>
 * Every name becomes a metadata variant of paper. Paper is not damageable and none of the test patterns
 * substitute, so nothing in the planner treats the variants as related - each name is simply its own key.
 * They look odd in a terminal, which is the point: seeing one means a test rig is still standing.
 */
public final class TestKeys {

    private final Map<String, AEKey> byName = new LinkedHashMap<>();

    /**
     * The key for a name, made on first use. Two scenarios asking for the same name in the same rig get the
     * same key, which is what lets a scenario share a leaf with the one before it.
     */
    public AEKey key(final String name) {
        return this.byName.computeIfAbsent(name, n -> AEItemKey.of(Items.PAPER, this.byName.size() + 1));
    }

    public GenericStack stack(final String name, final long amount) {
        return new GenericStack(this.key(name), amount);
    }

    /**
     * The name a key was made under, for a report that would otherwise print "paper@37".
     */
    public String nameOf(final AEKey what) {
        for (final Map.Entry<String, AEKey> entry : this.byName.entrySet()) {
            if (entry.getValue().equals(what)) {
                return entry.getKey();
            }
        }

        return String.valueOf(what);
    }
}
