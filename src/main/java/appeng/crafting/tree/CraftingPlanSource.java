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

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;


/**
 * One way a {@link CraftingPlanNode} is satisfied. A node can have several: half taken from storage and the
 * rest crafted is an ordinary plan.
 */
public final class CraftingPlanSource {

    public enum Kind {
        /**
         * Taken from the network. {@link #what} is what was really extracted, which is not the node's own key
         * when a pattern substituted an ingredient.
         */
        STORAGE,
        CRAFT,
        EMITTER,
        MISSING
    }

    private final Kind kind;
    private final AEKey what;
    /**
     * How much of the parent node's requirement this source covers.
     */
    private final long amount;
    /**
     * {@link Kind#CRAFT} only: how many times the pattern runs.
     */
    private final long crafts;
    private final List<CraftingPlanNode> inputs = new ArrayList<>();
    /**
     * {@link Kind#CRAFT} only: the machine the pattern would be pushed to, or null when the network has
     * none for it - a pattern can outlive the machine it was encoded for.
     */
    @Nullable
    private AEKey machine;

    public CraftingPlanSource(final Kind kind, final AEKey what, final long amount, final long crafts) {
        this.kind = kind;
        this.what = what;
        this.amount = amount;
        this.crafts = crafts;
    }

    public Kind getKind() {
        return this.kind;
    }

    public AEKey getWhat() {
        return this.what;
    }

    public long getAmount() {
        return this.amount;
    }

    public long getCrafts() {
        return this.crafts;
    }

    @Nullable
    public AEKey getMachine() {
        return this.machine;
    }

    public void setMachine(@Nullable final AEKey machine) {
        this.machine = machine;
    }

    public List<CraftingPlanNode> getInputs() {
        return this.inputs;
    }
}
