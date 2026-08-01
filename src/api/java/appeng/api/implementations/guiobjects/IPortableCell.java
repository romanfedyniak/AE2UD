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

package appeng.api.implementations.guiobjects;


import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.ITerminalHost;


/**
 * Obtained via {@link IGuiItem} getGuiObject
 */
public interface IPortableCell extends ITerminalHost, MEStorage, IEnergySource, IGuiItemObject
{
	/**
	 * Maximum useful number of terminal rows for this portable cell. Addons with a custom storage
	 * implementation can override this method; basic cell items are sized automatically.
	 */
	default int getTerminalRowLimit()
	{
		if( this.getItemStack().getItem() instanceof IBasicCellItem cellItem )
		{
			final long totalTypes = cellItem.getTotalTypes( this.getItemStack() );
			return (int) Math.min( Integer.MAX_VALUE, Math.max( 3, ( totalTypes + 8 ) / 9 ) );
		}

		return 3;
	}

}
