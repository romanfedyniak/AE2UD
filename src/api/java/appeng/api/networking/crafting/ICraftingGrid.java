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


import java.util.Set;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;

import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import appeng.api.config.CraftingMode;
import appeng.api.stacks.GenericStack;


public interface ICraftingGrid extends IGridCache
{

	/**
	 * @param whatToCraft requested craft
	 * @param world crafting world
	 * @param slot slot index
	 * @param details pattern details
	 *
	 * @return a collection of crafting patterns for the item in question.
	 */
	ImmutableCollection<ICraftingPatternDetails> getCraftingFor( AEKey whatToCraft, ICraftingPatternDetails details, int slot, World world );

	/**
	 * The machines a pattern would be pushed to, in the order they would be tried. Empty when nothing on the
	 * network can run it - a pattern can outlive the machine it was encoded for.
	 */
	List<ICraftingMedium> getMediums( ICraftingPatternDetails pattern );

	/**
	 * Every machine on the network that can be handed a pattern, whatever pattern that is. For pointing a
	 * player at a machine rather than for crafting.
	 */
	Collection<ICraftingMedium> getMediums();

	/**
	 * Begin calculating a crafting job.
	 *
	 * @param world crafting world
	 * @param grid network
	 * @param actionSrc source
	 * @param craftWhat result
	 * @param callback callback
	 * -- optional
	 *
	 * @return a future which will at an undetermined point in the future get you the {@link ICraftingJob} do not wait
	 * on this, your be waiting forever.
	 */
	default Future<ICraftingJob> beginCraftingJob( World world, IGrid grid, IActionSource actionSrc, GenericStack craftWhat, ICraftingCallback callback )
	{
		return beginCraftingJob( world, grid, actionSrc, craftWhat, CraftingMode.STANDARD, callback );
	}

	/**
	 * As {@link #beginCraftingJob(World, IGrid, IActionSource, GenericStack, ICraftingCallback)}, saying what
	 * the job should do about an ingredient the network can neither supply nor make.
	 *
	 * @param mode {@link CraftingMode#IGNORE_MISSING} plans a real job around what is lacking instead of
	 * returning a simulation
	 */
	Future<ICraftingJob> beginCraftingJob( World world, IGrid grid, IActionSource actionSrc, GenericStack craftWhat, CraftingMode mode, ICraftingCallback callback );

	/**
	 * Begin calculating a crafting job for an ad-hoc combination that has no pattern registered
	 * anywhere in the network - e.g. a crafting-table recipe assembled on the fly from whatever
	 * ingredients are missing for it. {@code rootPattern} is used to satisfy {@code craftWhat}
	 * directly, skipping the usual network pattern lookup for that single step; everything
	 * {@code rootPattern} itself needs is still resolved the normal way.
	 *
	 * @param world crafting world
	 * @param grid network
	 * @param actionSrc source
	 * @param craftWhat result
	 * @param rootPattern the synthetic pattern that produces {@code craftWhat}
	 * @param callback optional
	 *
	 * @return a future which will at an undetermined point in the future get you the {@link ICraftingJob} do not wait
	 * on this, your be waiting forever.
	 */
	Future<ICraftingJob> beginCraftingJobFromDetails( World world, IGrid grid, IActionSource actionSrc, GenericStack craftWhat, ICraftingPatternDetails rootPattern, ICraftingCallback callback );

	/**
	 * Submit the job to the Crafting system for processing.
	 *
	 * @param job - the crafting job from beginCraftingJob
	 * @param requestingMachine - a machine if its being requested via automation, may be null.
	 * @param target - can be null
	 * @param prioritizePower - if cpu is null, this determine if the system should prioritize power, or if it should
	 * find the lower
	 * end cpus, automatic processes generally should pick lower end cpus.
	 * @param src - the action source to use when starting the job, this will be used for extracting items, should
	 * usually be the same as the one provided to beginCraftingJob.
	 *
	 * @return the outcome, never null. On success and only when you sent a requestingMachine,
	 * {@link ICraftingSubmitResult#link()} holds a link you need to keep track of, handling its nbt saving and
	 * loading as well as the {@link ICraftingRequester} methods; if you sent null, discard it after checking
	 * the result. On failure {@link ICraftingSubmitResult#errorCode()} says why the job did not start.
	 */
	default ICraftingSubmitResult submitJob( ICraftingJob job, ICraftingRequester requestingMachine, ICraftingCPU target, boolean prioritizePower, IActionSource src )
	{
		return this.submitJob( job, requestingMachine, target, prioritizePower, src, 0 );
	}

	/**
	 * Submit the job the way {@link #submitJob(ICraftingJob, ICraftingRequester, ICraftingCPU, boolean, IActionSource)}
	 * does, at a priority.
	 *
	 * @param priority orders the running jobs against each other when they compete for the same machine: the
	 * job with the higher priority is offered a freed machine first, and jobs of equal priority take turns.
	 * It does not change which cpu is picked, and a job that competes with nothing is not slowed down by a
	 * low priority. The cpu carries it for as long as the job runs and returns to 0 when it ends.
	 */
	ICraftingSubmitResult submitJob( ICraftingJob job, ICraftingRequester requestingMachine, ICraftingCPU target, boolean prioritizePower, IActionSource src, int priority );

	/**
	 * @return list of all the crafting cpus on the grid
	 */
	ImmutableSet<ICraftingCPU> getCpus();

	/**
	 * @param what to be requested item
	 *
	 * @return true if the item can be requested via a crafting emitter.
	 */
	boolean canEmitFor( AEKey what );

	/**
	 * Everything the network currently knows how to craft.
	 *
	 * In the old model craftability was a boolean flag carried by the stack itself. Keys carry no
	 * such flag, so the crafting grid answers the question instead. This is what terminals use to
	 * list craftable-but-not-stored entries.
	 *
	 * @param filter restricts the result, for instance to one key type.
	 */
	Set<AEKey> getCraftables( AEKeyFilter filter );

	/**
	 * The same set, unfiltered, and cheap to ask for repeatedly.
	 *
	 * An implementation should return an immutable set and keep handing back the <b>same instance</b> for
	 * as long as nothing about the network's patterns or emitters changes. A caller holding the previous
	 * answer can then recognise "nothing changed" by identity rather than diffing two sets every tick,
	 * which is what an open terminal would otherwise do. A different instance only means the answer
	 * <em>may</em> have changed, so the default below - which builds a fresh set each time - is merely
	 * conservative, never wrong.
	 *
	 * @return an immutable set. Do not modify it, and do not assume it stays fixed once patterns change.
	 */
	default Set<AEKey> getCraftables()
	{
		return getCraftables( AEKeyFilter.all() );
	}

	/**
	 * @return true if the network has a pattern producing this key.
	 */
	default boolean isCraftable( AEKey what )
	{
		return getCraftables( key -> key.equals( what ) ).contains( what );
	}

	/**
	 * is this item being crafted?
	 *
	 * @param what item being crafted
	 *
	 * @return true if it is being crafting
	 */
	boolean isRequesting( AEKey what );

	/**
	 * The total amount being requested across all crafting cpus of a grid.
	 *
	 * @param what item being requested, ignores stacksize
	 *
	 * @return The total amount being requested.
	 */
	long requesting( AEKey what );
}
