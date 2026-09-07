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


import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.stacks.GenericStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.Arrays;


/**
 * A pattern a scenario made up, rather than one a player encoded.
 * <p>
 * Deliberately a processing pattern: a crafting-table one would have to answer
 * {@link #isValidItemForSlot} against a real recipe, and a scenario that wants that should be built from a
 * real encoded pattern instead of from this.
 */
public final class TestPattern implements ICraftingPatternDetails {

    private final String name;
    private final GenericStack[] inputs;
    private final GenericStack[] outputs;
    private int priority;

    public TestPattern(final String name, final GenericStack[] inputs, final GenericStack[] outputs,
            final int priority) {
        this.name = name;
        this.inputs = inputs;
        this.outputs = outputs;
        this.priority = priority;
    }

    /**
     * The name a report prints for this pattern. Scenarios name their patterns so a diff says "chain_7 ran
     * 12 times" instead of naming an item that four patterns all produce.
     */
    public String getName() {
        return this.name;
    }

    @Override
    public ItemStack getPattern() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isValidItemForSlot(final int slotIndex, final ItemStack itemStack, final World world) {
        return false;
    }

    @Override
    public boolean isCraftable() {
        return false;
    }

    @Override
    public GenericStack[] getInputs() {
        return this.inputs;
    }

    @Override
    public GenericStack[] getCondensedInputs() {
        return this.inputs;
    }

    @Override
    public GenericStack[] getCondensedOutputs() {
        return this.outputs;
    }

    @Override
    public GenericStack[] getOutputs() {
        return this.outputs;
    }

    @Override
    public boolean canSubstitute() {
        return false;
    }

    @Override
    public ItemStack getOutput(final InventoryCrafting craftingInv, final World world) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return this.name + Arrays.toString(this.inputs) + " -> " + Arrays.toString(this.outputs);
    }
}
