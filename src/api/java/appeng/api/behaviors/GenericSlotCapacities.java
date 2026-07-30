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

import javax.annotation.Nullable;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

/**
 * How much of a key type fits in one slot of a generic inventory - an interface's stock, and anything else
 * that holds a bounded amount per slot rather than an unbounded network total.
 * <p>
 * One slot of items is a stack; one slot of fluids is four buckets. The numbers are upstream AE2's, and the
 * registry exists for the same reason the strategy registries do: a key type an addon adds says how big its
 * own slot is, and nothing else has to know the type exists.
 */
public final class GenericSlotCapacities {

    /** What a type gets when it has registered nothing - one, so an unregistered type is at least usable. */
    private static final long DEFAULT_CAPACITY = 1;

    private static final Map<AEKeyType, Long> capacities = new LinkedHashMap<>();

    static {
        register(AEKeyType.items(), 64L);
        register(AEKeyType.fluids(), 4L * AEFluidKey.AMOUNT_BUCKET);
    }

    private GenericSlotCapacities() {
    }

    public static void register(AEKeyType type, long capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        capacities.putIfAbsent(type, capacity);
    }

    public static long get(@Nullable AEKeyType type) {
        if (type == null) {
            return DEFAULT_CAPACITY;
        }
        final Long capacity = capacities.get(type);
        return capacity == null ? DEFAULT_CAPACITY : capacity;
    }

    public static long get(@Nullable AEKey what) {
        return what == null ? DEFAULT_CAPACITY : get(what.getType());
    }

    public static Map<AEKeyType, Long> getMap() {
        return Collections.unmodifiableMap(capacities);
    }
}
