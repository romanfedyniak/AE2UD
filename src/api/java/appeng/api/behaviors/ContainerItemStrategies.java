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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;

/**
 * Registry of {@link ContainerItemStrategy}, one per {@link AEKeyType}. Sibling of
 * {@link StackWorldBehaviors}, kept separate for the same reason upstream does: this is about item stacks in a
 * player's hand, not about blocks in the world.
 */
public final class ContainerItemStrategies {

    private static final Map<AEKeyType, ContainerItemStrategy> strategies = new LinkedHashMap<>();

    private ContainerItemStrategies() {
    }

    public static void register(AEKeyType type, ContainerItemStrategy strategy) {
        if (type == AEKeyType.items()) {
            // An item is its own container; unwrapping one into "the item it contains" is meaningless and
            // would make every ordinary stack look emptiable.
            throw new IllegalArgumentException("Items cannot have a container-item strategy");
        }
        strategies.putIfAbsent(type, strategy);
    }

    public static boolean isTypeSupported(@Nullable AEKeyType type) {
        return type != null && strategies.containsKey(type);
    }

    public static boolean isKeySupported(@Nullable AEKey key) {
        return key != null && isTypeSupported(key.getType());
    }

    public static Set<AEKeyType> getSupportedKeyTypes() {
        return Collections.unmodifiableSet(strategies.keySet());
    }

    /**
     * Asks every registered strategy in turn what the stack contains.
     *
     * @return the first non-null answer, or null if the stack is not a container of any registered type.
     */
    @Nullable
    public static GenericStack getContainedStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        for (final AEKeyType type : AEKeyTypes.getAll()) {
            final ContainerItemStrategy strategy = strategies.get(type);
            if (strategy != null) {
                final GenericStack contained = strategy.getContainedStack(stack);
                if (contained != null) {
                    return contained;
                }
            }
        }
        return null;
    }

    @Nullable
    public static GenericStack getContainedStack(ItemStack stack, @Nullable AEKeyType type) {
        if (stack.isEmpty() || type == null) {
            return null;
        }
        final ContainerItemStrategy strategy = strategies.get(type);
        return strategy == null ? null : strategy.getContainedStack(stack);
    }

    /**
     * @return the empty container the network could supply to carry {@code what} away, or
     *         {@link ItemStack#EMPTY} if this key type has none.
     */
    public static ItemStack getEmptyContainerFor(@Nullable AEKey what) {
        if (what == null) {
            return ItemStack.EMPTY;
        }
        final ContainerItemStrategy strategy = strategies.get(what.getType());
        return strategy == null ? ItemStack.EMPTY : strategy.getEmptyContainerFor(what);
    }

    /**
     * Opens an interaction context for a held container.
     *
     * @param type the key type the caller wants to move, or null to accept whichever strategy claims the stack -
     *             which is what emptying does, since there the container decides what comes out.
     */
    @Nullable
    public static ContainerItemStrategy.Context openContext(ItemStack container, @Nullable AEKeyType type) {
        if (container.isEmpty()) {
            return null;
        }

        if (type != null) {
            final ContainerItemStrategy strategy = strategies.get(type);
            return strategy == null ? null : strategy.openContext(container);
        }

        for (final AEKeyType candidate : AEKeyTypes.getAll()) {
            final ContainerItemStrategy strategy = strategies.get(candidate);
            if (strategy != null && strategy.getContainedStack(container) != null) {
                final ContainerItemStrategy.Context context = strategy.openContext(container);
                if (context != null) {
                    return context;
                }
            }
        }
        return null;
    }
}
