/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.crafting;


import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.stacks.GenericStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;


/**
 * An ad-hoc crafting-table combination with no pattern registered anywhere in the network - used
 * to let a HEI recipe transfer craft its own missing ingredients instead of just refusing to move
 * them. {@link #getInputs()} carries every ingredient the combination needs, not just the missing
 * ones, so the crafting tree pulls whatever is already in storage and only recurses into real
 * sub-crafting for the rest.
 * <p/>
 * Treated as a processing pattern ({@link #isCraftable()} false) rather than a crafting-table one:
 * it has no fixed 3x3 slot shape to validate against, just a flat ingredient list.
 * {@link appeng.me.cluster.implementations.CraftingCPUCluster} short-circuits execution for this
 * class specifically, since no real machine will ever claim to provide for it.
 */
public class VirtualPatternDetails implements ICraftingPatternDetails {

    private final GenericStack[] inputs;
    private final GenericStack[] outputs;
    private int priority = 0;

    public VirtualPatternDetails(final GenericStack[] inputs, final GenericStack[] outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
    }

    @Override
    public ItemStack getPattern() {
        return GenericStack.wrapInItemStack(this.getPrimaryOutput());
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
}
