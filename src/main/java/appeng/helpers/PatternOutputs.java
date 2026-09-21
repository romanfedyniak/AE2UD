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

import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.core.AppEng;
import appeng.util.item.ItemStackHashStrategy;

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

    private static final Map<ItemStack, ItemStack> CACHE =
            new Object2ObjectOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());

    private PatternOutputs() {
    }

    /** What to draw in place of {@code stack}, or an empty stack for anything that is not a pattern. */
    public static ItemStack of(final ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ICraftingPatternItem)) {
            return ItemStack.EMPTY;
        }

        final ItemStack cached = CACHE.get(stack);

        if (cached != null) {
            return cached;
        }

        final World world = AppEng.proxy.getWorld();

        // No world yet, so nothing is cached either: the next frame asks again.
        if (world == null) {
            return ItemStack.EMPTY;
        }

        final ItemStack out = ((ICraftingPatternItem) stack.getItem()).getOutput(stack, world);

        CACHE.put(stack, out);
        return out;
    }

    /** Called when a pattern stops being one, since the stack it was is about to hold something else. */
    public static void forget(final ItemStack stack) {
        CACHE.remove(stack);
    }
}
