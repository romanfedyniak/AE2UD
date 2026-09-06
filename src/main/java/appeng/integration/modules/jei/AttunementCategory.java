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
 * Which item turns a ME P2P Tunnel into which kind. One entry per kind, with every item that names it
 * cycling through the input slot - including the ones named by a whole mod or by a capability, which
 * cannot be listed any other way.
 */
class AttunementCategory extends InWorldCategory {

    static final String UID = "appliedenergistics2.attunement";

    private static final int IN_X = 30;
    private static final int ARROW_X = 56;
    private static final int OUT_X = 102;

    AttunementCategory(final IGuiHelper helper, final ItemStack icon) {
        super(helper, UID, "gui.appliedenergistics2.category.Attunement", icon, GuiText.JeiAttune.getLocal());
    }

    InWorldRecipe recipe(final List<ItemStack> triggers, final ItemStack tunnel) {
        return this.entry().in(triggers).out(tunnel).caption(GuiText.JeiAttune.getLocal()).build();
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
