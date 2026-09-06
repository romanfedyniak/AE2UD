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

import javax.annotation.Nullable;
import java.util.List;


/**
 * The Charger: certus quartz comes out charged, and anything that holds power comes out full.
 */
class ChargerCategory extends InWorldCategory {

    static final String UID = "appliedenergistics2.charger";

    private static final int IN_X = 30;
    private static final int ARROW_X = 56;
    private static final int OUT_X = 102;

    ChargerCategory(final IGuiHelper helper, final ItemStack icon) {
        super(helper, UID, "tile.appliedenergistics2.charger.name", icon, GuiText.JeiCharging.getLocal());
    }

    InWorldRecipe recipe(final List<ItemStack> inputs, final List<ItemStack> outputs,
            @Nullable final String caption) {
        return this.entry().in(inputs).out(outputs).caption(caption).build();
    }


    @Override
    protected int[] slotColumns(final InWorldRecipe recipe) {
        return new int[] { IN_X, OUT_X };
    }

    @Override
    protected int arrowColumn() {
        return ARROW_X;
    }

    @Override
    public void setRecipe(final IRecipeLayout layout, final InWorldRecipe recipe, final IIngredients ingredients) {
        final IGuiItemStackGroup items = layout.getItemStacks();
        items.init(0, true, IN_X, this.rowY(recipe));
        items.init(1, false, OUT_X, this.rowY(recipe));
        items.set(ingredients);
    }
}
