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

import javax.annotation.Nullable;

import appeng.api.stacks.GenericStack;

/**
 * Teaches the mod's recipe-viewer integration about one kind of ingredient, so a key type an addon
 * registered is understood wherever the viewer and the mod meet:
 * <ul>
 * <li>moving a recipe into a pattern,</li>
 * <li>the recipe keybinds over a slot or a terminal row,</li>
 * <li>dragging an ingredient out of the viewer into a filter, a pattern or a search box.</li>
 * </ul>
 * Without one, a key type still appears in the terminal, but the viewer sees only the placeholder stack it
 * is wrapped in, which has no recipes of its own.
 * <p>
 * Register with {@link IngredientConverters#register}. The interface names the ingredient by its class
 * rather than by a type object of the viewer's, so nothing of the viewer's API reaches this one.
 *
 * @param <T> the viewer's ingredient class, e.g. {@code FluidStack}.
 */
public interface IngredientConverter<T> {

    /**
     * The ingredient class this converter speaks for. It must be the class the recipe viewer itself
     * registered, since that is what a recipe's ingredients are looked up by.
     */
    Class<T> getIngredientClass();

    /**
     * Turns a stack of the mod's own into the viewer's ingredient.
     * <p>
     * An ingredient that reads an amount of zero as "empty" has to come back holding at least one, or the
     * viewer drops it and the type is lost - a row in a terminal asks with an amount of zero, because its
     * amount is drawn separately.
     *
     * @return null if this converter does not speak for that key type.
     */
    @Nullable
    T getIngredientFromStack(GenericStack stack);

    /**
     * Turns the viewer's ingredient back into a stack of the mod's own.
     *
     * @return null for an ingredient that stands for nothing, such as an empty stack.
     */
    @Nullable
    GenericStack getStackFromIngredient(T ingredient);
}
