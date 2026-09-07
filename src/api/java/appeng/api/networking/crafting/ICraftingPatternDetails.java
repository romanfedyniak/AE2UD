/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.networking.crafting;


import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;


/**
 * do not implement provided by {@link ICraftingPatternItem}
 *
 * caching this INSTANCE will increase performance of validation and checks.
 */
public interface ICraftingPatternDetails
{

	/**
	 * @return source item.
	 */
	ItemStack getPattern();

	/**
	 * @param slotIndex specific slot index
	 * @param itemStack item in slot
	 * @param world crafting world
	 *
	 * @return if an item can be used in the specific slot for this pattern.
	 */
	boolean isValidItemForSlot( int slotIndex, ItemStack itemStack, World world );

	/**
	 * @return if this pattern is a crafting pattern ( work bench )
	 */
	boolean isCraftable();

	/**
	 * @return a list of the inputs, will include nulls.
	 */
	GenericStack[] getInputs();

	/**
	 * Everything the input side says beyond {@link #getInputs()}: what else would do in each slot, which
	 * slots the network fills in, and the condensed totals. Worked out once and kept - see
	 * {@link IPatternInputs}.
	 */
	IPatternInputs getPatternInputs();

	/**
	 * @return a list of the outputs, will be clean
	 */
	GenericStack[] getCondensedOutputs();

	/**
	 * The primary output of this pattern. The pattern will only be used to craft the primary output; the others are
	 * just byproducts.
	 */
	default GenericStack getPrimaryOutput() {
		return getOutputs()[0];
	}

	/**
	 * @return a list of the outputs, will include nulls.
	 */
	GenericStack[] getOutputs();

	/**
	 * @return if this pattern is enabled to support substitutions.
	 */
	boolean canSubstitute();

	/**
	 * Whether this pattern takes the contents of its container-item ingredients straight from the network
	 * rather than the filled containers themselves - a recipe calling for a bucket of water drawing the
	 * water and nothing else.
	 * <p>
	 * This is the <em>effective</em> answer, not the flag the player set: a pattern with the option turned
	 * on but no ingredient that qualifies answers false, so that everything reading it - the tooltip, the
	 * interface's refusal to hand the pattern to a third-party machine - is telling the truth about what
	 * this pattern will actually do.
	 */
	default boolean canSubstituteFluids()
	{
		return false;
	}

	/**
	 * Allow using this INSTANCE of the pattern details to preform the crafting action with performance enhancements.
	 *
	 * @param craftingInv inventory
	 * @param world crafting world
	 *
	 * @return the crafted ( work bench ) item.
	 */
	ItemStack getOutput( InventoryCrafting craftingInv, World world );

	/**
	 * Get the priority of this pattern
	 *
	 * @return the priority of this pattern
	 */
	int getPriority();

	/**
	 * Set the priority the of this pattern.
	 *
	 * @param priority priority of pattern
	 */
	void setPriority( int priority );
}
