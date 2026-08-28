/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.render;


import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.container.me.GridInventoryEntry;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;

import javax.annotation.Nullable;


/**
 * @author AlgorithmX2
 * @author thatsIch
 * @version rv2
 * @since rv0
 */
public class StackSizeRenderer {
    /**
     * The scale nothing is drawn larger than - four digits of the vanilla font fill a 16x16 slot exactly
     * at this size. Anything wider is drawn smaller; see {@link #fittingScale}.
     */
    private static final float SCALE = 0.666f;
    private static final int OFFSET = -1;

    /** The characters an amount is made of, less whatever unit a key type appends to it. */
    private static final String AMOUNT_CHARS = "0123456789.KMGTPEm";

    private static int maxCharWidth;
    private static int measuredAt = -1;

    private static final int WHITE = 0xFFFFFF;
    /** Amber, for a craftable whose result would never arrive - the same amber the terminals mark it with. */
    public static final int FAKE_CRAFTABLE_COLOR = 0xFFAA00;

    /**
     * The ME-slot flavour: carries the craftable flag alongside the amount. Replaces the old
     * {@code renderStackSize(FontRenderer, IAEItemStack, int, int)} at the one call site that fed it a
     * slot's live {@code IAEItemStack} ({@link appeng.client.gui.AEBaseGui#drawSlot}).
     */
    public void renderStackSize(FontRenderer fontRenderer, @Nullable GridInventoryEntry entry, int xPos, int yPos) {
        if (entry != null) {
            // Alt trades a stocked row's amount for the "+", showing what an Alt click on it would do.
            this.renderStackSize(fontRenderer, entry.getWhat(), entry.getStoredAmount(), entry.isCraftable(),
                    GuiScreen.isAltKeyDown(), xPos, yPos, entry.isFakeCraftable() ? FAKE_CRAFTABLE_COLOR : WHITE);
        }
    }

    /**
     * The plain flavour: a bare amount with no craftable flag. Replaces the old call sites that built a
     * throwaway {@code AEItemStack.fromItemStack(...)} purely to carry a count (the drag-splitting preview
     * and the encoded-pattern output preview in {@link appeng.client.gui.AEBaseGui#drawSlot}).
     * <p>
     * A single item draws nothing, as it does anywhere in Minecraft. This is what keeps a filter, a plane or
     * a cell workbench from labelling every configured item "1" - slots that carry no amount at all. One
     * millibucket still draws, because for a type measured in thousands it is a real reading rather than the
     * absence of one.
     */
    public void renderStackSize(FontRenderer fontRenderer, @Nullable GenericStack stack, int xPos, int yPos) {
        this.renderStackSize(fontRenderer, stack, false, xPos, yPos);
    }

    /**
     * The fake-slot flavour: a bare amount, plus the craftable flag for a slot that only ever displays a
     * key rather than stocking it - a pattern's crafting grid or output. Replaces the old call sites that
     * built a throwaway {@code AEItemStack.fromItemStack(...)} purely to carry a count.
     * <p>
     * A single item draws no amount, as it does anywhere in Minecraft - a filter, a plane or a cell
     * workbench does not label every configured item "1". The craftable mark is independent of that: it
     * still draws on a single-item slot, since craftability is not an amount.
     * <p>
     * Alt does not swap the amount for the mark here as it does on a stocked ME row: there is no Alt click
     * on a slot that only displays a key, so hiding a pattern's amounts while the key is held is pure loss.
     */
    public void renderStackSize(FontRenderer fontRenderer, @Nullable GenericStack stack, boolean craftable, int xPos, int yPos) {
        this.renderStackSize(fontRenderer, stack, craftable, false, xPos, yPos);
    }

    /**
     * As above, with {@code fakeCraftable} colouring the mark for a key nothing but a fake pattern makes.
     */
    public void renderStackSize(FontRenderer fontRenderer, @Nullable GenericStack stack, boolean craftable, boolean fakeCraftable, int xPos, int yPos) {
        if (stack == null) {
            return;
        }
        final int markColor = fakeCraftable ? FAKE_CRAFTABLE_COLOR : WHITE;
        if (stack.amount() == 1 && stack.what() instanceof AEItemKey) {
            if (craftable) {
                drawCraftableMark(fontRenderer, xPos, yPos, markColor);
            }
            return;
        }

        this.renderStackSize(fontRenderer, stack.what(), stack.amount(), craftable, false, xPos, yPos, markColor);
    }

    private void renderStackSize(FontRenderer fontRenderer, AEKey what, long amount, boolean craftable, boolean markInsteadOfAmount, int xPos, int yPos, int markColor) {
        final boolean unicodeFlag = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(false);

        if ((amount == 0 || markInsteadOfAmount) && craftable) {
            // Modern AE2's convention: "+" where the count goes, rather than the word "Craft". Left-inset
            // like drawCraftableMark's own "+", not nudged right the way a multi-digit count is.
            drawLabel(fontRenderer, "+", null, xPos, yPos, 0.0f, markColor);
        } else if (amount > 0) {
            // The reading is the key's own: a bucket of something is not a thousand of it.
            drawLabel(fontRenderer, what.formatAmount(amount, AmountFormat.SLOT), what, xPos, yPos, 1.3f, WHITE);
            if (craftable) {
                drawCraftableMark(fontRenderer, xPos, yPos, markColor);
            }
        }

        fontRenderer.setUnicodeFlag(unicodeFlag);
    }

    /**
     * The small "+" that marks a slot as craftable without replacing whatever else it shows - stocked ME
     * rows that are also craftable, and any fake slot's configured key (pattern grid, pattern output, and
     * - via the HEI recipe screen mixin - a recipe ingredient the open terminal can already autocraft).
     */
    public static void drawCraftableMark(final FontRenderer fontRenderer, final int xPos, final int yPos) {
        drawCraftableMark(fontRenderer, xPos, yPos, WHITE);
    }

    public static void drawCraftableMark(final FontRenderer fontRenderer, final int xPos, final int yPos, final int color) {
        final boolean unicodeFlag = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(false);

        // Left/top-aligned to the icon's own corner, unlike drawLabel's count - which is deliberately
        // right/bottom-aligned to xPos+16/yPos+16, the wrong anchor for a mark that belongs in the opposite
        // corner. Same offset the count uses, but subtracted rather than added: the count's offset nudges
        // it inward (toward xPos+16), so nudging this mark inward (toward xPos) means the opposite sign.
        // Its own size, not the amount's: the mark is a sign rather than a reading, and shrinking it
        // because a long number happens to share the slot only makes it harder to see.
        final float scale = fittingScale(fontRenderer, "+", null, SCALE);
        final float inverseScale = 1.0f / scale;
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        fontRenderer.drawStringWithShadow("+", (int) ((xPos - OFFSET) * inverseScale), (int) ((yPos - OFFSET) * inverseScale), color);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();

        fontRenderer.setUnicodeFlag(unicodeFlag);
    }

    private static void drawLabel(final FontRenderer fontRenderer, final String text, @Nullable final AEKey what, final int xPos, final int yPos, final float xAdjust, final int color) {
        final float scale = fittingScale(fontRenderer, text, what, SCALE);
        final float inverseScale = 1.0f / scale;

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        final int X = (int) (((float) xPos + OFFSET + 16.0f + xAdjust - fontRenderer.getStringWidth(text) * scale) * inverseScale);
        final int Y = (int) (((float) yPos + OFFSET + 16.0f - 7.0f * scale) * inverseScale);
        fontRenderer.drawStringWithShadow(text, X, Y, color);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
    }

    /**
     * The largest scale, up to {@code baseScale}, at which {@code text} still fits a 16-pixel slot. Ask
     * the font rather than count characters: a resource pack or a mod can hand the game a font whose
     * digits are nothing like six pixels wide, and four of those drew straight over the next slot.
     */
    public static float fittingScale(final FontRenderer fontRenderer, final String text, @Nullable final AEKey what, final float baseScale) {
        return fittingScale(fontRenderer, text, what, baseScale, 16);
    }

    /**
     * As above, for a box of some other width.
     * <p>
     * The estimate is the widest character an amount can use, times how many of them there are, rather
     * than the width of this particular string: it keeps two amounts of the same length the same size,
     * where measuring each would have "111" drawn larger than "999". A key type that would rather have
     * one size for all of its amounts says so with {@link appeng.api.stacks.AEKeyType#getWidestSlotAmount()}.
     */
    public static float fittingScale(final FontRenderer fontRenderer, final String text, @Nullable final AEKey what, final float baseScale, final int width) {
        float needed = text.length() * (float) maxCharWidth(fontRenderer, text);

        final String widest = what == null ? null : what.getType().getWidestSlotAmount();
        if (widest != null) {
            needed = Math.max(needed, fontRenderer.getStringWidth(widest));
        }

        return needed <= 0.0f ? baseScale : Math.min(baseScale, width / needed);
    }

    /**
     * Measured once and kept, because a mod that hooks the font is asked this for every amount on screen.
     * The width of a zero is the probe: a resource pack reload changes the widths inside the same
     * {@link FontRenderer}, and so does the unicode flag, and both move that one too.
     */
    private static int maxCharWidth(final FontRenderer fontRenderer, final String text) {
        final int probe = fontRenderer.getCharWidth('0');

        if (probe != measuredAt) {
            int widest = 0;
            for (int i = 0; i < AMOUNT_CHARS.length(); i++) {
                widest = Math.max(widest, fontRenderer.getCharWidth(AMOUNT_CHARS.charAt(i)));
            }
            maxCharWidth = widest;
            measuredAt = probe;
        }

        // A key type appends a unit of its own choosing, which the alphabet above cannot know about.
        int widest = maxCharWidth;
        for (int i = 0; i < text.length(); i++) {
            widest = Math.max(widest, fontRenderer.getCharWidth(text.charAt(i)));
        }
        return widest;
    }

}
