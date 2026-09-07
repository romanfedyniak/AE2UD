/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 * Copyright (c) 2026 AE2UD contributors
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


import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Everything a pattern's input side says, in one place.
 * <p>
 * Replaces the loose per-slot accessors {@code getSubstituteInputs(int)} and {@code isContainerFabricated(int)}
 * and the separate {@code getCondensedInputs()}: a slot's facts belong together, and something normalising a
 * pattern wants them together too.
 * <p>
 * Whatever an implementation returns must be worked out once and kept - {@link #getCondensed()} is read on
 * every tick a crafting cpu is running.
 */
public interface IPatternInputs
{

	/**
	 * The slot at that index, never null. An index outside the pattern, or a slot it leaves empty, answers
	 * with {@link IPatternInput#NOTHING}.
	 */
	IPatternInput get( int slot );

	/**
	 * Every input key once, carrying the total consumed by one craft across every slot using it.
	 */
	GenericStack[] getCondensed();

	/**
	 * For a pattern whose slots have nothing to say beyond what they were encoded with - no substitution and
	 * nothing the network fills in. The array is indexed by slot and may hold nulls.
	 */
	static IPatternInputs of( final GenericStack[] inputs )
	{
		final IPatternInput[] slots = new IPatternInput[inputs.length];
		final Map<AEKey, GenericStack> totals = new LinkedHashMap<>();

		for ( int x = 0; x < inputs.length; x++ )
		{
			final GenericStack input = inputs[x];

			if ( input == null )
			{
				slots[x] = IPatternInput.NOTHING;
				continue;
			}

			slots[x] = new SingleOption( input );
			totals.merge( input.what(), input, GenericStack::sum );
		}

		return new Fixed( slots, totals.values().toArray( new GenericStack[0] ) );
	}

	/** An input that stands only for itself. */
	final class SingleOption implements IPatternInput
	{
		private final List<GenericStack> options;

		SingleOption( final GenericStack input )
		{
			final List<GenericStack> one = new ArrayList<>( 1 );
			// One of the encoded ingredient, not the slot's whole count - see getOptions.
			one.add( input.amount() == 1 ? input : new GenericStack( input.what(), 1 ) );
			this.options = one;
		}

		@Override
		public List<GenericStack> getOptions()
		{
			return this.options;
		}

		@Override
		public boolean isFabricated()
		{
			return false;
		}
	}

	/** The inputs of a pattern that worked them out once. */
	final class Fixed implements IPatternInputs
	{
		private final IPatternInput[] slots;
		private final GenericStack[] condensed;

		public Fixed( final IPatternInput[] slots, final GenericStack[] condensed )
		{
			this.slots = slots;
			this.condensed = condensed;
		}

		@Override
		public IPatternInput get( final int slot )
		{
			return slot >= 0 && slot < this.slots.length && this.slots[slot] != null
					? this.slots[slot]
					: IPatternInput.NOTHING;
		}

		@Override
		public GenericStack[] getCondensed()
		{
			return this.condensed;
		}
	}
}
