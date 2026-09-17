/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.integrations.hei;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * Where an addon registers its {@link IngredientConverter}s, and where the mod's own recipe-viewer
 * integration asks what an ingredient is.
 * <p>
 * Items and fluids are registered by the mod itself, so a key type of its own is no more of a special case
 * than an addon's is. Register during mod initialisation, before any screen opens.
 */
public final class IngredientConverters {

    private static volatile List<IngredientConverter<?>> converters = ImmutableList.of();

    private IngredientConverters() {
    }

    /**
     * @return false if a converter for that ingredient class was already registered, in which case the one
     *         already there is kept.
     */
    public static synchronized boolean register(final IngredientConverter<?> converter) {
        for (final IngredientConverter<?> existing : converters) {
            if (existing.getIngredientClass() == converter.getIngredientClass()) {
                return false;
            }
        }

        converters = ImmutableList.<IngredientConverter<?>>builder().addAll(converters).add(converter).build();
        return true;
    }

    public static List<IngredientConverter<?>> getConverters() {
        return converters;
    }

    /**
     * The viewer's ingredient for a stack of the mod's own, from the first converter that speaks for it.
     *
     * @return null for a key type nothing is registered for; the caller then falls back to the placeholder
     *         stack the key wraps itself in.
     */
    @Nullable
    public static Object toIngredient(final GenericStack stack) {
        for (final IngredientConverter<?> converter : converters) {
            final Object ingredient = converter.getIngredientFromStack(stack);
            if (ingredient != null) {
                return ingredient;
            }
        }
        return null;
    }

    /**
     * @see #toIngredient(GenericStack)
     */
    @Nullable
    public static Object toIngredient(final AEKey what, final long amount) {
        return toIngredient(new GenericStack(what, amount));
    }

    /**
     * What one of the viewer's ingredients stands for.
     *
     * @return null for an ingredient no converter speaks for, and for one that stands for nothing.
     */
    @Nullable
    public static GenericStack toStack(@Nullable final Object ingredient) {
        if (ingredient == null) {
            return null;
        }

        for (final IngredientConverter<?> converter : converters) {
            final GenericStack stack = convert(converter, ingredient);
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T> GenericStack convert(final IngredientConverter<T> converter, final Object ingredient) {
        return converter.getIngredientClass().isInstance(ingredient)
                ? converter.getStackFromIngredient((T) ingredient)
                : null;
    }
}
