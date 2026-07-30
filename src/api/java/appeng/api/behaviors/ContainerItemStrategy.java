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

package appeng.api.behaviors;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

/**
 * How a key type is carried inside an ordinary {@link ItemStack} - the fluid in a bucket, and whatever an addon's
 * key type has an equivalent of. This is what lets a terminal fill a held container from the network and empty one
 * into it, without the terminal knowing that fluids exist.
 * <p>
 * Sixth member of the strategy family (see {@link StackWorldBehaviors}), and the same rule applies: a key type
 * that registers no strategy here simply cannot be put into or taken out of a container item, and every part that
 * offers the interaction skips it for that type. Items themselves never register one - an item <em>is</em> the
 * container, so there is nothing to unwrap.
 * <p>
 * <b>Why a context object rather than plain methods.</b> Forge's {@code IFluidHandlerItem} works on a copy of the
 * stack and hands the modified container back through {@code getContainer()}, so a fill or drain cannot be
 * expressed as a mutation of the stack that was passed in. {@link Context} holds that working copy for the
 * duration of one interaction and {@link Context#getContainer()} answers the result. Modern upstream AE2 solves
 * the same problem the same way; ae-gtnh instead returns a (container, amount) pair from each call, which forces
 * the caller to thread the container through by hand and cannot express "several transfers into one container".
 */
public interface ContainerItemStrategy {

    /**
     * @return what the given stack contains, or null if this strategy's key type is not in there. Used to decide
     *         whether a held item can be emptied at all, and to show what it would deposit.
     */
    @Nullable
    GenericStack getContainedStack(ItemStack stack);

    /**
     * Begins an interaction with a single container item.
     *
     * @param container a stack of size one, which the context is free to keep a copy of.
     * @return null if this strategy cannot act on that stack at all.
     */
    @Nullable
    Context openContext(ItemStack container);

    /**
     * The empty container a terminal may borrow from the network when the player clicks this key with an empty
     * hand - a bucket, for a fluid. The caller pulls one out of storage, fills it, and puts it back if the fill
     * turned out to be impossible.
     * <p>
     * Upstream AE2 hardcodes {@code Items.BUCKET} into its terminal menu for this. Keeping it here instead is
     * what stops the terminal from knowing that fluids exist - the same rule that put the fuzzy-range decision
     * on {@code AEKeyType} rather than on the parts.
     *
     * @return {@link ItemStack#EMPTY} if this type has no such container, which is also the default.
     */
    default ItemStack getEmptyContainerFor(AEKey what) {
        return ItemStack.EMPTY;
    }

    /**
     * One in-progress interaction with one container item. Not reusable across containers, and not stored: a
     * caller opens it, performs its transfers, reads {@link #getContainer()} and drops it.
     */
    interface Context {

        /**
         * Puts up to {@code amount} of {@code what} into the container.
         *
         * @return how much was accepted.
         */
        long insert(AEKey what, long amount, Actionable mode);

        /**
         * Takes up to {@code amount} of {@code what} out of the container.
         *
         * @return how much was removed.
         */
        long extract(AEKey what, long amount, Actionable mode);

        /**
         * @return what could be drained out of the container right now, or null if nothing can.
         */
        @Nullable
        GenericStack getExtractableContent();

        /**
         * @return the container as it now stands - an empty bucket after a drain, a filled one after an insert.
         *         Never the stack that was passed to {@link #openContext(ItemStack)}; always the result.
         */
        ItemStack getContainer();
    }

    static void register(AEKeyType type, ContainerItemStrategy strategy) {
        ContainerItemStrategies.register(type, strategy);
    }
}
