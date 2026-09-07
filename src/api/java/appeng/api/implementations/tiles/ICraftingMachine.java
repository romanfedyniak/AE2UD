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

package appeng.api.implementations.tiles;


import javax.annotation.Nullable;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;


public interface ICraftingMachine
{

	/**
	 * The crafting machine a neighbour offers on the given side, be it the block itself or a part sitting on
	 * that side of a cable bus - a part is a machine to an interface just as much as a block is.
	 *
	 * @param te the neighbouring tile entity, may be null
	 * @param side the side of that neighbour which faces the interface
	 *
	 * @return the machine, or null if there is none
	 */
	@Nullable
	static ICraftingMachine of( @Nullable final TileEntity te, final EnumFacing side )
	{
		if( te instanceof ICraftingMachine )
		{
			return (ICraftingMachine) te;
		}

		if( te instanceof IPartHost )
		{
			final IPart part = ( (IPartHost) te ).getPart( side );
			if( part instanceof ICraftingMachine )
			{
				return (ICraftingMachine) part;
			}
		}

		return null;
	}

	/**
	 * inserts a crafting plan, and the necessary items into the crafting machine.
	 *
	 * @param patternDetails details of pattern
	 * @param table crafting table
	 * @param ejectionDirection ejection direction
	 *
	 * @return if it was accepted, all or nothing.
	 */
	boolean pushPattern( ICraftingPatternDetails patternDetails, InventoryCrafting table, EnumFacing ejectionDirection );

	/**
	 * check if the crafting machine is accepting pushes via pushPattern, if this is false, all calls to push will fail,
	 * you can try inserting into the inventory instead.
	 *
	 * @return true, if pushPattern can complete, if its false push will always be false.
	 */
	boolean acceptsPlans();

	/**
	 * Whether this machine can be given a pattern whose container items the network assembled out of a
	 * fluid - see {@link appeng.api.networking.crafting.IPatternInput#isFabricated()}.
	 * <p>
	 * Such a container never existed before the craft and must not survive it, so a machine that hands
	 * every container back the way an ordinary crafting table does would be minting one out of a fluid on
	 * every craft. Defaults to false, so a machine written before this existed is simply never offered such
	 * a pattern rather than quietly duplicating items; the pattern waits for a molecular assembler instead.
	 * Answer true only if the machine consults {@code isFabricated} and leaves that slot empty.
	 */
	default boolean acceptsFabricatedContainers()
	{
		return false;
	}
}
