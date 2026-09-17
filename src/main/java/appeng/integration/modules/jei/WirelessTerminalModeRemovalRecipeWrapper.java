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

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.wrapper.ICraftingRecipeWrapper;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.core.localization.GuiText;
import appeng.recipes.game.WirelessTerminalModeRemovalRecipe;

/**
 * Shows a mode being taken back out of a wireless terminal.
 */
class WirelessTerminalModeRemovalRecipeWrapper implements ICraftingRecipeWrapper {

    // The arrow of HEI's crafting category, where the explanation is read on hover
    private static final int ARROW_X = 60;
    private static final int ARROW_Y = 18;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 18;

    private final WirelessTerminalModeRemovalRecipe recipe;

    WirelessTerminalModeRemovalRecipeWrapper(final WirelessTerminalModeRemovalRecipe recipe) {
        this.recipe = recipe;
    }

    @Nullable
    @Override
    public ResourceLocation getRegistryName() {
        return this.recipe.getRegistryName();
    }

    @Override
    public void getIngredients(final IIngredients ingredients) {
        ingredients.setInputs(ItemStack.class, Collections.singletonList(this.recipe.getTerminal()));
        ingredients.setOutput(ItemStack.class, this.recipe.getRecipeOutput());
    }

    @Override
    public List<String> getTooltipStrings(final int mouseX, final int mouseY) {
        if (mouseX >= ARROW_X && mouseX < ARROW_X + ARROW_WIDTH && mouseY >= ARROW_Y
                && mouseY < ARROW_Y + ARROW_HEIGHT) {
            return Collections.singletonList(GuiText.WirelessModeTakeOutRecipe.getLocal());
        }
        return Collections.emptyList();
    }
}
