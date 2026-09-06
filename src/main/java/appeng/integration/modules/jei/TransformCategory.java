/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.integration.modules.jei;


import appeng.core.localization.GuiText;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.item.ItemStack;

import java.util.List;


/**
 * Items dropped on the ground that turn into something else. What sets each one off differs - a liquid for
 * fluix, an explosion for the singularity - so the trigger is written across the top of the entry rather
 * than drawn as an ingredient: neither of them is consumed.
 */
class TransformCategory extends InWorldCategory {

    static final String UID = "appliedenergistics2.transform";

    private static final int ARROW_X = 68;
    private static final int OUT_X = 104;

    /** Between the last ingredient and the arrow. */
    private static final int ARROW_GAP = 6;

    TransformCategory(final IGuiHelper helper, final ItemStack icon) {
        super(helper, UID, "gui.appliedenergistics2.category.Transform", icon, GuiText.JeiInLiquid.getLocal(),
                GuiText.JeiOnExplosion.getLocal());
    }

    InWorldRecipe recipe(final List<List<ItemStack>> inputs, final ItemStack output, final String trigger) {
        final InWorldRecipe.Builder builder = this.entry();

        for (final List<ItemStack> input : inputs) {
            builder.in(input);
        }

        return builder.out(output).caption(trigger).build();
    }

    /**
     * The ingredients end where the arrow begins, so a recipe made of two of them sits beside the arrow
     * rather than leaving the gap where a third would have been.
     */
    private static int firstInput(final int inputs) {
        return ARROW_X - ARROW_GAP - inputs * SLOT;
    }

    @Override
    protected int[] slotColumns(final InWorldRecipe recipe) {
        final int inputs = recipe.itemInputCount();
        final int[] columns = new int[inputs + 1];
        final int first = firstInput(inputs);

        for (int i = 0; i < inputs; i++) {
            columns[i] = first + i * SLOT;
        }

        columns[inputs] = OUT_X;
        return columns;
    }

    @Override
    protected int arrowColumn() {
        return ARROW_X;
    }

    @Override
    public void setRecipe(final IRecipeLayout layout, final InWorldRecipe recipe, final IIngredients ingredients) {
        final IGuiItemStackGroup items = layout.getItemStacks();
        final int row = this.rowY(recipe);
        final int inputs = recipe.itemInputCount();
        final int first = firstInput(inputs);

        for (int i = 0; i < inputs; i++) {
            items.init(i, true, first + i * SLOT, row);
        }

        items.init(inputs, false, OUT_X, row);
        items.set(ingredients);
    }
}
