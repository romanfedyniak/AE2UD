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

import java.util.Collections;
import java.util.List;


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
	 * @return a list of the inputs, will be clean
	 */
	GenericStack[] getCondensedInputs();

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
	 * Whether the container item in the given input slot is assembled for the craft out of a key taken from
	 * the network, instead of being pulled out of storage as an item.
	 * <p>
	 * Such a container never existed before the craft and must not survive it, so whoever performs the craft
	 * has to leave nothing behind for that slot - see {@code Platform.getRemainingItem}. Note that this is
	 * decided by the pattern alone and not by what happens to sit in the slot: a slot that answers true is
	 * <em>only ever</em> supplied that way, which is what lets a molecular assembler still holding a
	 * half-finished craft work it out again after a reload.
	 */
	default boolean isContainerFabricated( int slot )
	{
		return false;
	}

	/**
	 * The inputs that may stand in for the one encoded in the given slot, most preferred first.
	 * <p>
	 * For a slot that {@link #isContainerFabricated(int)} reports, this is the single non-item stack the
	 * network supplies - a fluid amount rather than the container - and nothing else, because mixing the
	 * two sources would make the fabricated container indistinguishable from a real one.
	 */
	default List<GenericStack> getSubstituteInputs( int slot )
	{
		return Collections.emptyList();
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
