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
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.Collections;
import java.util.List;


/**
 * A crystal seed growing where it lies. The input slot walks the seed through its stages, so a half-grown
 * one is recognisable; the accelerator is shown as what it is - something standing nearby, not an
 * ingredient - and the liquid the seed has to be lying in is shown beside it.
 */
class CertusGrowthCategory extends InWorldCategory {

    static final String UID = "appliedenergistics2.certus_growth";

    private static final int FLUID_X = 4;
    private static final int SEED_X = 28;
    private static final int ARROW_X = 52;
    private static final int OUT_X = 90;
    private static final int ACCELERATOR_X = 122;

    private static final int ITEM = 16;

    CertusGrowthCategory(final IGuiHelper helper, final ItemStack icon) {
        super(helper, UID, "gui.appliedenergistics2.category.CertusGrowth", icon, GuiText.JeiGrowth.getLocal());
    }

    InWorldRecipe recipe(final List<ItemStack> stages, final ItemStack crystal, final ItemStack accelerator) {
        return this.entry()
                .inFluid(new FluidStack(FluidRegistry.WATER, Fluid.BUCKET_VOLUME))
                .in(stages)
                .in(accelerator.isEmpty() ? Collections.emptyList() : Collections.singletonList(accelerator))
                .out(crystal)
                .caption(GuiText.JeiGrowth.getLocal())
                .build();
    }

    @Override
    protected int[] slotColumns(final InWorldRecipe recipe) {
        return new int[] { FLUID_X, SEED_X, OUT_X, ACCELERATOR_X };
    }

    @Override
    protected int arrowColumn() {
        return ARROW_X;
    }

    @Override
    public void setRecipe(final IRecipeLayout layout, final InWorldRecipe recipe, final IIngredients ingredients) {
        final int row = this.rowY(recipe);

        // A fluid is placed where it is drawn, with none of the padding an item slot adds for itself.
        final IGuiFluidStackGroup fluids = layout.getFluidStacks();
        fluids.init(0, true, FLUID_X + SLOT_INSET, row + SLOT_INSET, ITEM, ITEM, Fluid.BUCKET_VOLUME, false, null);
        fluids.set(ingredients);

        final IGuiItemStackGroup items = layout.getItemStacks();
        items.init(0, true, SEED_X, row);
        items.init(1, true, ACCELERATOR_X, row);
        items.init(2, false, OUT_X, row);
        items.set(ingredients);
    }
}
