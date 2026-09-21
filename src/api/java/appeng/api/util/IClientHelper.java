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


package appeng.api.util;


import java.util.List;

import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.cells.StorageCell;


public interface IClientHelper
{
	/**
	 * Add cell information to the provided list. Used for tooltip content.
	 * 
	 * @param handler Cell handler.
	 * @param lines List of lines to add to.
	 */
	void addCellInformation( StorageCell handler, List<String> lines );

	/**
	 * Add the lines a pattern's tooltip carries: what it makes, what it takes, whether it substitutes,
	 * who encoded it, and how to open the view that draws it. Used for tooltip content.
	 * <p>
	 * Here rather than on the pattern item, so that an addon's pattern reads the same as AE2's own and
	 * is translated once. A pattern that will not decode has nothing to show and is not passed here.
	 *
	 * @param details the decoded pattern
	 * @param stack the pattern item itself, which carries who wrote it
	 * @param lines List of lines to add to.
	 */
	void addPatternInformation( ICraftingPatternDetails details, ItemStack stack, List<String> lines );

}
