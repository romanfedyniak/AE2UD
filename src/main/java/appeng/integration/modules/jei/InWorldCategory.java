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


import appeng.core.AppEng;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;


/**
 * What the categories describing something outside a machine have in common: a title, an item to stand for
 * them in the list, a row of slots, and room above them for a line of explanation.
 * <p>
 * The panel is made tall enough for the longest line this category can draw, wrapped to its width - fixing
 * on two lines would be right in English and wrong in the next language along. Each entry then centres what
 * it has in that panel, so one with nothing to say sits in the middle rather than under a gap.
 */
abstract class InWorldCategory implements IRecipeCategory<InWorldRecipe> {

    /** As wide as JEI lays a category out before the list beside it starts to suffer. */
    static final int WIDTH = 150;

    /** The square JEI reserves for one ingredient, border included. */
    static final int SLOT = 18;

    /** Where the item sits inside that square, which a fluid has to be placed at by hand. */
    static final int SLOT_INSET = 1;

    /**
     * The shortest a category may be. JEI stacks its three per-entry buttons upwards from an entry's bottom
     * edge, so a shorter category leaves them hanging above the entry they belong to.
     */
    private static final int BUTTON_COLUMN = 13 * 3 + 4;

    private final String uid;
    private final String title;
    private final IDrawable icon;
    private final int height;

    protected final CategoryPanel panel;

    InWorldCategory(final IGuiHelper helper, final String uid, final String titleKey, final ItemStack icon,
            final String... captions) {
        this.uid = uid;
        this.title = I18n.format(titleKey);
        this.icon = icon.isEmpty() ? null : helper.createDrawableIngredient(icon);

        int tallest = Math.max(SLOT, BUTTON_COLUMN);
        for (final String caption : captions) {
            tallest = Math.max(tallest, InWorldRecipe.contentHeight(caption, WIDTH));
        }

        this.height = tallest;
        this.panel = new CategoryPanel(helper, WIDTH, this.height);
    }

    /**
     * Where this entry's slots sit across the panel. Asked per entry rather than per category because a
     * category can hold entries of different widths - a recipe made of two things should not draw a third,
     * empty slot just because its neighbour needs one.
     */
    protected abstract int[] slotColumns(InWorldRecipe recipe);

    /** Where the arrow goes, or -1 for a category that has none. */
    protected int arrowColumn() {
        return -1;
    }

    CategoryPanel panel() {
        return this.panel;
    }

    int height() {
        return this.height;
    }

    /** An entry of this category, which knows where to draw itself because it knows the category. */
    protected InWorldRecipe.Builder entry() {
        return InWorldRecipe.builder(this);
    }

    /** The top of the row of slots for one entry, once its own caption has been allowed for. */
    protected int rowY(final InWorldRecipe recipe) {
        return recipe.rowY(this.height, WIDTH);
    }

    @Override
    public String getUid() {
        return this.uid;
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    public String getModName() {
        return AppEng.MOD_NAME;
    }

    @Override
    public IDrawable getBackground() {
        return this.panel.background();
    }

    @Override
    @Nullable
    public IDrawable getIcon() {
        return this.icon;
    }
}
