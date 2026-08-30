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

package appeng.crafting.tree;


import appeng.api.stacks.AEKey;

import java.util.ArrayList;
import java.util.List;


/**
 * One thing the plan needs, and how much of it. What it costs hangs underneath as {@link CraftingPlanSource}
 * children.
 */
public final class CraftingPlanNode {

    private final AEKey what;
    private final long amount;
    private final List<CraftingPlanSource> sources = new ArrayList<>();

    private boolean missingKnown;
    private boolean missingCache;

    public CraftingPlanNode(final AEKey what, final long amount) {
        this.what = what;
        this.amount = amount;
    }

    public AEKey getWhat() {
        return this.what;
    }

    public long getAmount() {
        return this.amount;
    }

    public List<CraftingPlanSource> getSources() {
        return this.sources;
    }

    /**
     * Whether this node or anything below it is short of ingredients - what the "missing only" filter keeps.
     * Cached because the filter asks this of every node on every frame.
     */
    public boolean hasMissing() {
        if (this.missingKnown) {
            return this.missingCache;
        }

        this.missingKnown = true;
        this.missingCache = false;

        for (final CraftingPlanSource source : this.sources) {
            if (source.getKind() == CraftingPlanSource.Kind.MISSING) {
                this.missingCache = true;
                return true;
            }
            for (final CraftingPlanNode input : source.getInputs()) {
                if (input.hasMissing()) {
                    this.missingCache = true;
                    return true;
                }
            }
        }

        return false;
    }
}
