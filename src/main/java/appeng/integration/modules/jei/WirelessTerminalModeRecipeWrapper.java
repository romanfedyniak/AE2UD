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

import java.util.Arrays;

import javax.annotation.Nullable;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.wrapper.ICraftingRecipeWrapper;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.recipes.game.WirelessTerminalModeRecipe;

/**
 * Shows a wireless terminal mode being crafted into a terminal.
 *
 * <p>HEI only knows how to draw the four vanilla and Forge recipe classes; anything else reaches its list and
 * is then dropped for want of something to draw it with. This is that something.</p>
 */
class WirelessTerminalModeRecipeWrapper implements ICraftingRecipeWrapper {

    private final WirelessTerminalModeRecipe recipe;

    WirelessTerminalModeRecipeWrapper(final WirelessTerminalModeRecipe recipe) {
        this.recipe = recipe;
    }

    @Nullable
    @Override
    public ResourceLocation getRegistryName() {
        return this.recipe.getRegistryName();
    }

    @Override
    public void getIngredients(final IIngredients ingredients) {
        ingredients.setInputs(ItemStack.class,
                Arrays.asList(this.recipe.getTerminal(), this.recipe.getMode().getUnlockIngredient()));
        ingredients.setOutput(ItemStack.class, this.recipe.getRecipeOutput());
    }
}
