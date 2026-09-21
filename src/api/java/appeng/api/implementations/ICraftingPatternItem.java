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

package appeng.api.implementations;


import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.stacks.GenericStack;


/**
 * Implemented on {@link Item}
 */
public interface ICraftingPatternItem
{

	/**
	 * Access Details about a pattern
	 *
	 * @param is pattern
	 * @param w crafting world
	 *
	 * @return details of pattern
	 */
	ICraftingPatternDetails getPatternForItem( ItemStack is, World w );

	/**
	 * What this pattern is drawn as while the view key is held down - its first output, so that a shelf
	 * of patterns reads as the things they make rather than as a row of identical plates.
	 * <p>
	 * The stack is swapped, not the model: an item with a renderer of its own looks its contents up in
	 * the stack it is handed, so handing it another item's model and this pattern's stack draws nothing.
	 * A pattern whose first output is not what a player would recognise it by - one output standing for
	 * a whole multiblock craft, say - overrides this and names something else.
	 *
	 * @return what to draw instead of the pattern, or an empty stack to draw the pattern itself
	 */
	default ItemStack getOutput( ItemStack is, World w )
	{
		final ICraftingPatternDetails details = this.getPatternForItem( is, w );

		if( details == null || details.getOutputs().length == 0 || details.getOutputs()[0] == null )
		{
			return ItemStack.EMPTY;
		}

		return GenericStack.wrapInItemStack( details.getOutputs()[0] );
	}
}
