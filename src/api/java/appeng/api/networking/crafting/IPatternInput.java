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


import appeng.api.stacks.GenericStack;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.List;


/**
 * One input slot of a pattern, and everything about it the encoded stack alone does not say.
 * <p>
 * Indexed alongside {@link ICraftingPatternDetails#getInputs()}: that array says <em>what and how much</em>
 * the slot was encoded with, this one says <em>what else would do</em> and <em>where it comes from</em>.
 */
public interface IPatternInput
{

	/**
	 * A slot with nothing in it. Handed out rather than null so nothing has to check.
	 */
	IPatternInput NOTHING = new IPatternInput()
	{
		@Override
		public List<GenericStack> getOptions()
		{
			return Collections.emptyList();
		}

		@Override
		public boolean isFabricated()
		{
			return false;
		}
	};

	/** {@link #getUses()} for an ingredient a craft consumes outright. */
	long CONSUMED = 0;

	/**
	 * What may go in this slot, most preferred first, the encoded ingredient itself at the front. Each
	 * amount is what stands in for <em>one</em> of the encoded ingredient - one for an item, a bucket's
	 * worth for a fluid - so a caller multiplies by the slot's own count.
	 * <p>
	 * Empty only for a slot the pattern leaves empty. A pattern that does not substitute still answers with
	 * the one thing it was encoded with, so a caller that does not care about substitution can read this and
	 * be right either way.
	 */
	List<GenericStack> getOptions();

	/**
	 * Whether the container in this slot is assembled for the craft out of a key taken from the network,
	 * instead of being pulled out of storage as an item - a recipe calling for a bucket of water drawing the
	 * water and nothing else.
	 * <p>
	 * Such a container never existed before the craft and must not survive it, so whoever performs the craft
	 * has to leave nothing behind for that slot. Note that this is decided by the pattern alone and not by
	 * what happens to sit in the slot: a slot that answers true is <em>only ever</em> supplied that way,
	 * which is what lets a molecular assembler still holding a half-finished craft work it out again after a
	 * reload.
	 */
	boolean isFabricated();

	/**
	 * The single thing the network supplies for a {@link #isFabricated() fabricated} slot. Meaningless on any
	 * other slot, where the options are alternatives rather than the one source.
	 */
	default GenericStack getSupplied()
	{
		return this.getOptions().get( 0 );
	}

	/**
	 * What one craft hands back for this slot, or null when it keeps what it took.
	 * <p>
	 * This is the emptied bucket, and nothing else: a tool that comes back worn is not a thing returned but a
	 * thing partly spent, and is answered by {@link #getUses()} instead. The two are told apart by whether
	 * what comes back is the same item that went in.
	 */
	@Nullable
	default GenericStack getReturned()
	{
		return null;
	}

	/**
	 * How many crafts one of this input serves before it is spent, or {@link #CONSUMED} when a craft uses one
	 * up outright.
	 * <p>
	 * This is how a tool is counted. Each craft wears it a little, and every stage of that wear is a
	 * different key - counting whole tools instead keeps a plan to "two hammers" rather than sixty
	 * near-identical entries, and keeps the network from having to hold a hundred hammers to run a hundred
	 * crafts. What a craft costs in wear is not guessed: it is read from the very item the craft hands back,
	 * so a tool that loses ten points a craft is counted at ten and not at one.
	 */
	default long getUses()
	{
		return CONSUMED;
	}
}
