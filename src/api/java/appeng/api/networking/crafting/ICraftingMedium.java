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


import appeng.api.util.DimensionalCoord;
import net.minecraft.inventory.InventoryCrafting;


/**
 * A place to send Items for crafting purposes, this is considered part of AE's External crafting system.
 */
public interface ICraftingMedium
{

	/**
	 * What this medium looks like to a player - the name a terminal lists it under and an item to draw for
	 * it. Defaults to nothing, so a medium that has no useful answer is simply not drawn.
	 */
	default MachineIdentity getMachineIdentity()
	{
		return MachineIdentity.NOTHING;
	}

	/**
	 * Where this medium is, so a screen can point a player at it. Null when it is not in the world at all.
	 */
	default DimensionalCoord getMachineLocation()
	{
		return null;
	}

	/**
	 * instruct a medium to create the item represented by the pattern+details, the items on the table, and where if
	 * possible the output should be directed.
	 *
	 * @param patternDetails details
	 * @param table crafting table
	 *
	 * @return if the pattern was successfully pushed.
	 */
	boolean pushPattern( ICraftingPatternDetails patternDetails, InventoryCrafting table );

	/**
	 * Same as {@link #pushPattern(ICraftingPatternDetails, InventoryCrafting)}, but also carrying the
	 * pattern's ingredients that an {@link InventoryCrafting} cannot express - a fluid, or anything else an
	 * addon registers a key type for.
	 * <p>
	 * The two halves arrive together and are all-or-nothing: a medium that cannot place <em>every</em>
	 * ingredient must accept none of them, because the crafting CPU has already taken them out of the network
	 * and puts them back only when this returns false.
	 * <p>
	 * Defaults to refusing the pattern outright when there is anything in {@code extraInputs}, so an existing
	 * medium keeps working unchanged and never silently drops an ingredient it cannot see.
	 *
	 * @param extraInputs the non-item ingredients, never null and never containing null.
	 */
	default boolean pushPattern( ICraftingPatternDetails patternDetails, InventoryCrafting table,
			appeng.api.stacks.GenericStack[] extraInputs )
	{
		return extraInputs.length == 0 && this.pushPattern( patternDetails, table );
	}

	/**
	 * How many copies of this pattern the medium would take in one go right now - for a machine that runs
	 * many identical crafts side by side, so a crafting CPU can hand it a thousand of them in one call instead
	 * of a thousand calls.
	 * <p>
	 * Asked only once {@link #isBusy()} and {@link #acceptsWhileBusy} have let the pattern through, and has to
	 * be as cheap. A batch still costs the CPU one operation per copy: this changes how many calls a job
	 * takes, never how fast it runs. Answering less than one skips the medium for this pattern.
	 * <p>
	 * Defaults to one, which keeps a medium on {@link #pushPattern(ICraftingPatternDetails, InventoryCrafting,
	 * appeng.api.stacks.GenericStack[])} and never calls the batch version.
	 */
	default int maxCopies( ICraftingPatternDetails patternDetails )
	{
		return 1;
	}

	/**
	 * Pushes {@code copies} identical copies of a pattern at once. {@code table} and {@code extraInputs} hold
	 * <em>one</em> copy's ingredients, and every copy is made of exactly those; the CPU has taken
	 * {@code copies} times as much out of the network.
	 * <p>
	 * All-or-nothing, like the single push: accept every copy or none. Called only with
	 * {@code 1 < copies <= maxCopies(patternDetails)}, and never for a medium that
	 * {@link #isFakeCrafting() settles jobs itself}.
	 */
	default boolean pushPattern( ICraftingPatternDetails patternDetails, InventoryCrafting table,
			appeng.api.stacks.GenericStack[] extraInputs, int copies )
	{
		return copies == 1 && this.pushPattern( patternDetails, table, extraInputs );
	}

	/**
	 * @return if this is false, the crafting engine will refuse to send new jobs to this medium.
	 */
	boolean isBusy();

	/**
	 * Whether this medium will take one particular pattern even though {@link #isBusy()} says it is busy.
	 * Asked only after that, so a medium answering false behaves exactly as it did before this existed.
	 * <p>
	 * The two are split because they cost differently: a busy answer is worth working out once a tick and
	 * remembering, while this one depends on the pattern and has to be cheap.
	 */
	default boolean acceptsWhileBusy( ICraftingPatternDetails details )
	{
		return false;
	}

	/**
	 * Whether the last push this medium refused failed only because a machine that makes the pattern was busy.
	 * Such a refusal is cheap to meet again, so the crafting CPU asks again next tick instead of waiting longer.
	 */
	default boolean refusedAsBusy()
	{
		return false;
	}

	/**
	 * Whether a pattern pushed here is finished the moment it leaves - nothing comes back, and the job is
	 * settled as if it had. For a machine chain that carries its own results, or one that consumes them.
	 * <p>
	 * A medium that answers true is used only for the pattern producing a job's final output, and only for a
	 * job a player asked for: a dependency whose result never arrives would leave the job waiting forever,
	 * and a machine that ordered the craft would never be handed anything and would order it again.
	 */
	default boolean isFakeCrafting()
	{
		return false;
	}
}
