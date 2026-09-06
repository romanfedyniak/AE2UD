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

import mezz.jei.api.gui.IGuiFluidStackGroup;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.List;


/**
 * What the Entropy Manipulator does to a block it is clicked on. Half of the table is water and lava, which
 * have no item to put in a slot, so those sides are drawn as fluids; heating water gives nothing back at
 * all, and that entry simply has no output.
 */
class EntropyCategory extends InWorldCategory {

    static final String UID = "appliedenergistics2.entropy";

    private static final int IN_X = 30;
    private static final int ARROW_X = 56;
    private static final int OUT_X = 102;

    EntropyCategory(final IGuiHelper helper, final ItemStack icon) {
        super(helper, UID, "item.appliedenergistics2.entropy_manipulator.name", icon, GuiText.JeiHeat.getLocal(), GuiText.JeiCool.getLocal());
    }

    private static final int ITEM = 16;

    InWorldRecipe recipe(@Nullable final ItemStack inItem, @Nullable final FluidStack inFluid,
            final List<ItemStack> outItems, @Nullable final FluidStack outFluid, final boolean heat) {
        final InWorldRecipe.Builder builder = this.entry();

        if (inItem != null) {
            builder.in(inItem);
        }
        if (inFluid != null) {
            builder.inFluid(inFluid);
        }
        for (final ItemStack out : outItems) {
            builder.out(out);
        }
        if (outFluid != null) {
            builder.outFluid(outFluid);
        }

        return builder.caption((heat ? GuiText.JeiHeat : GuiText.JeiCool).getLocal()).build();
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
        // A fluid is placed where it is drawn, with none of the padding an item slot adds for itself.
        final int fluidY = this.rowY(recipe) + SLOT_INSET;
        final IGuiFluidStackGroup fluids = layout.getFluidStacks();
        fluids.init(0, true, IN_X + SLOT_INSET, fluidY, ITEM, ITEM, Fluid.BUCKET_VOLUME, false, null);
        fluids.init(1, false, OUT_X + SLOT_INSET, fluidY, ITEM, ITEM, Fluid.BUCKET_VOLUME, false, null);
        fluids.set(ingredients);

        final IGuiItemStackGroup items = layout.getItemStacks();
        items.init(0, true, IN_X, this.rowY(recipe));
        items.init(1, false, OUT_X, this.rowY(recipe));
        items.set(ingredients);
    }
}
