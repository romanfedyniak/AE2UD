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

import java.util.List;

import com.google.common.collect.ImmutableList;

import appeng.api.stacks.GenericStack;

/**
 * A recipe moved out of a recipe viewer into a pattern terminal, before any slot is chosen for it. Read by
 * {@link PatternEncodingMode#placeRecipe}.
 */
public final class TransferredRecipe {

    private final String categoryUid;
    private final List<List<GenericStack>> itemInputs;
    private final List<GenericStack> otherInputs;
    private final List<GenericStack> extraInputs;
    private final List<GenericStack> outputs;

    public TransferredRecipe(final String categoryUid, final List<List<GenericStack>> itemInputs,
            final List<GenericStack> otherInputs, final List<GenericStack> extraInputs,
            final List<GenericStack> outputs) {
        this.categoryUid = categoryUid;
        this.itemInputs = ImmutableList.copyOf(itemInputs);
        this.otherInputs = ImmutableList.copyOf(otherInputs);
        this.extraInputs = ImmutableList.copyOf(extraInputs);
        this.outputs = ImmutableList.copyOf(outputs);
    }

    /** The uid of the recipe category the recipe was shown in. */
    public String getCategoryUid() {
        return this.categoryUid;
    }

    /**
     * The item inputs in the order the recipe screen lays them out, gaps included: an empty list is an empty
     * square, so a shaped recipe keeps its shape. Each list holds the alternatives, the preferred one first.
     */
    public List<List<GenericStack>> getItemInputs() {
        return this.itemInputs;
    }

    /** Inputs that are not items, such as fluids, one stack each. */
    public List<GenericStack> getOtherInputs() {
        return this.otherInputs;
    }

    /** What the screen draws but does not list, such as a machine's mana bar; see ExtraInputProviders. */
    public List<GenericStack> getExtraInputs() {
        return this.extraInputs;
    }

    /** What the recipe makes, the one the player looked up first. */
    public List<GenericStack> getOutputs() {
        return this.outputs;
    }
}
