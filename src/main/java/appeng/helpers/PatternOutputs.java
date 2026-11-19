/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.stacks.AEItemKey;
import appeng.core.AppEng;

/**
 * What a pattern is drawn as while the view key is held, remembered between frames.
 * <p>
 * Decoding a pattern reads its tag and looks its recipe up, which is far too much to do for every plate on
 * screen every frame. One cache serves every pattern item rather than one per item class: the key is the
 * whole stack, so two mods' patterns can never be mistaken for one another.
 * <p>
 * Not client-only, although only the client draws: {@link #forget} is called from the path that turns a
 * pattern back into a blank, which runs on both sides.
 */
public final class PatternOutputs {

    /** Plenty for every pattern a screen shows; past it the least recently drawn is dropped. */
    private static final int LIMIT = 1024;

    /**
     * Keyed by an immutable key rather than by the caller's stack: a stack used as a key and then renamed or
     * otherwise changed in place could never be found again, and stayed in the map for good. Synchronized,
     * since {@link #forget} also runs on the integrated server's thread.
     */
    private static final Map<AEItemKey, ItemStack> CACHE = new LinkedHashMap<AEItemKey, ItemStack>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<AEItemKey, ItemStack> eldest) {
            return this.size() > LIMIT;
        }
    };

    /**
     * The key and output last found for a stack, by identity: a slot draws the same stack every frame, and making
     * its key copies the whole pattern tag. Checked against the stack on every use, so one changed in place is
     * looked up again.
     */
    private static final Map<ItemStack, Seen> SEEN = new WeakHashMap<>();

    private PatternOutputs() {
    }

    /** What to draw in place of {@code stack}, or an empty stack for anything that is not a pattern. */
    public static ItemStack of(final ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ICraftingPatternItem)) {
            return ItemStack.EMPTY;
        }

        synchronized (CACHE) {
            final Seen seen = SEEN.get(stack);
            if (seen != null && seen.key.matches(stack)) {
                return seen.output;
            }
        }

        final AEItemKey key = AEItemKey.of(stack);

        synchronized (CACHE) {
            final ItemStack cached = CACHE.get(key);
            if (cached != null) {
                SEEN.put(stack, new Seen(key, cached));
                return cached;
            }
        }

        final World world = AppEng.proxy.getWorld();

        // No world yet, so nothing is cached either: the next frame asks again.
        if (world == null) {
            return ItemStack.EMPTY;
        }

        final ItemStack out = ((ICraftingPatternItem) stack.getItem()).getOutput(stack, world);

        synchronized (CACHE) {
            CACHE.put(key, out);
            SEEN.put(stack, new Seen(key, out));
        }
        return out;
    }

    /** Called when a pattern stops being one, since the stack it was is about to hold something else. */
    public static void forget(final ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        synchronized (CACHE) {
            CACHE.remove(AEItemKey.of(stack));
            SEEN.remove(stack);
        }
    }

    /** Recipes changed or the world was left: what the patterns show may have changed with them. */
    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
            SEEN.clear();
        }
    }

    private static final class Seen {
        final AEItemKey key;
        final ItemStack output;

        Seen(final AEItemKey key, final ItemStack output) {
            this.key = key;
            this.output = output;
        }
    }
}
