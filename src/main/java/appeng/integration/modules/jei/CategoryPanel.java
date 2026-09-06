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


import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

import java.awt.Color;
import java.util.List;


/**
 * What a recipe category draws when it has no machine screen to cut a background out of.
 * <p>
 * The slot and the arrow are cut from the furnace, because that is what JEI's own vanilla categories do and
 * what the three older ones here do with the grindstone, condenser and inscriber. {@code getSlotDrawable()}
 * would have been shorter, but it hands back the thin slot HEI wears in its own item list rather than the
 * one a recipe screen is made of.
 */
final class CategoryPanel {

    private static final ResourceLocation FURNACE = new ResourceLocation("minecraft",
            "textures/gui/container/furnace.png");

    /** The furnace's own input slot, and the arrow beside it. */
    private static final int SLOT_U = 55;
    private static final int SLOT_V = 16;
    private static final int ARROW_U = 79;
    private static final int ARROW_V = 35;

    static final int ARROW_WIDTH = 24;
    static final int ARROW_HEIGHT = 17;

    private final IDrawable background;
    private final IDrawable slot;
    private final IDrawable arrow;

    CategoryPanel(final IGuiHelper helper, final int width, final int height) {
        this.background = helper.createBlankDrawable(width, height);
        this.slot = helper.createDrawable(FURNACE, SLOT_U, SLOT_V, InWorldCategory.SLOT, InWorldCategory.SLOT);
        this.arrow = helper.createDrawable(FURNACE, ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT);
    }

    IDrawable background() {
        return this.background;
    }

    /**
     * Takes the same corner {@code IGuiItemStackGroup.init} is given: that is the slot's, not the item's -
     * JEI lays out an eighteen pixel square there and draws the item one pixel inside it.
     */
    void drawSlot(final Minecraft minecraft, final int x, final int y) {
        this.slot.draw(minecraft, x, y);
    }

    void drawArrow(final Minecraft minecraft, final int x, final int y) {
        this.arrow.draw(minecraft, x, y);
    }

    /**
     * A line of explanation across the panel, for what slots alone cannot say. Wrapped rather than measured
     * against the English: the same sentence is longer in most other languages, and would run off the edge.
     */
    static void drawCaption(final String text, final int width, final int y) {
        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int line = y;

        for (final String part : wrap(text, width)) {
            font.drawString(part, (width - font.getStringWidth(part)) / 2, line, Color.gray.getRGB());
            line += InWorldRecipe.LINE_HEIGHT;
        }
    }

    @SuppressWarnings("unchecked")
    static List<String> wrap(final String text, final int width) {
        return Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(text, width);
    }
}
