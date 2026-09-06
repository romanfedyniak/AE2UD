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


import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * One entry in any of the categories that describe something happening outside a machine.
 * <p>
 * They share a wrapper because they differ only in where the slots sit, which is the category's business;
 * what goes in them is a list of items or fluids either way. Every ingredient is a list of alternatives, so
 * a slot can cycle - every stage a seed passes through, every item that attunes one kind of tunnel.
 * <p>
 * The entry draws its own slots rather than leaving them to {@code drawExtras}, because how far down they
 * belong depends on how much this entry has to say, and the category is shared by all of them.
 */
class InWorldRecipe implements IRecipeWrapper {

    static final int LINE_HEIGHT = 10;

    /** Between the last line of the caption and the top of the slots. */
    private static final int CAPTION_GAP = 4;

    private final InWorldCategory category;

    private final List<List<ItemStack>> itemInputs;
    private final List<List<ItemStack>> itemOutputs;
    private final List<FluidStack> fluidInputs;
    private final List<FluidStack> fluidOutputs;

    @Nullable
    private final String caption;

    private InWorldRecipe(final Builder builder) {
        this.category = builder.category;
        this.itemInputs = builder.itemInputs;
        this.itemOutputs = builder.itemOutputs;
        this.fluidInputs = builder.fluidInputs;
        this.fluidOutputs = builder.fluidOutputs;
        this.caption = builder.caption;
    }

    static Builder builder(final InWorldCategory category) {
        return new Builder(category);
    }

    /** How tall an entry with this caption is: its lines, the gap under them, and one row of slots. */
    static int contentHeight(@Nullable final String caption, final int width) {
        return captionHeight(caption, width) + InWorldCategory.SLOT;
    }

    private static int captionHeight(@Nullable final String caption, final int width) {
        if (caption == null) {
            return 0;
        }
        return CategoryPanel.wrap(caption, width).size() * LINE_HEIGHT + CAPTION_GAP;
    }

    /** How many item inputs this entry has, which is how many input slots it wants drawn. */
    int itemInputCount() {
        return this.itemInputs.size();
    }

    /** Where this entry's slots go, with what it has centred in the panel the category settled on. */
    int rowY(final int panelHeight, final int panelWidth) {
        return this.top(panelHeight, panelWidth) + captionHeight(this.caption, panelWidth);
    }

    private int top(final int panelHeight, final int panelWidth) {
        return Math.max(0, (panelHeight - contentHeight(this.caption, panelWidth)) / 2);
    }

    @Override
    public void getIngredients(final IIngredients ingredients) {
        if (!this.itemInputs.isEmpty()) {
            ingredients.setInputLists(ItemStack.class, this.itemInputs);
        }
        if (!this.itemOutputs.isEmpty()) {
            ingredients.setOutputLists(ItemStack.class, this.itemOutputs);
        }
        if (!this.fluidInputs.isEmpty()) {
            ingredients.setInputs(FluidStack.class, this.fluidInputs);
        }
        if (!this.fluidOutputs.isEmpty()) {
            ingredients.setOutputs(FluidStack.class, this.fluidOutputs);
        }
    }

    @Override
    public void drawInfo(final Minecraft minecraft, final int recipeWidth, final int recipeHeight, final int mouseX,
            final int mouseY) {
        final int row = this.rowY(recipeHeight, recipeWidth);
        final CategoryPanel panel = this.category.panel();

        for (final int x : this.category.slotColumns(this)) {
            panel.drawSlot(minecraft, x, row);
        }

        final int arrow = this.category.arrowColumn();
        if (arrow >= 0) {
            // The arrow is a pixel shorter than a slot, so it rides half a pixel lower to line up.
            panel.drawArrow(minecraft, arrow, row + (InWorldCategory.SLOT - CategoryPanel.ARROW_HEIGHT) / 2);
        }

        if (this.caption != null) {
            CategoryPanel.drawCaption(this.caption, recipeWidth, this.top(recipeHeight, recipeWidth));
        }
    }

    static class Builder {

        private final InWorldCategory category;

        private final List<List<ItemStack>> itemInputs = new ArrayList<>();
        private final List<List<ItemStack>> itemOutputs = new ArrayList<>();
        private final List<FluidStack> fluidInputs = new ArrayList<>();
        private final List<FluidStack> fluidOutputs = new ArrayList<>();

        @Nullable
        private String caption;

        Builder(final InWorldCategory category) {
            this.category = category;
        }

        Builder in(final ItemStack stack) {
            return this.in(Collections.singletonList(stack));
        }

        Builder in(final List<ItemStack> alternatives) {
            this.itemInputs.add(alternatives);
            return this;
        }

        Builder out(final ItemStack stack) {
            return this.out(Collections.singletonList(stack));
        }

        Builder out(final List<ItemStack> alternatives) {
            this.itemOutputs.add(alternatives);
            return this;
        }

        Builder inFluid(final FluidStack fluid) {
            this.fluidInputs.add(fluid);
            return this;
        }

        Builder outFluid(final FluidStack fluid) {
            this.fluidOutputs.add(fluid);
            return this;
        }

        Builder caption(@Nullable final String text) {
            this.caption = text;
            return this;
        }

        InWorldRecipe build() {
            return new InWorldRecipe(this);
        }
    }
}
