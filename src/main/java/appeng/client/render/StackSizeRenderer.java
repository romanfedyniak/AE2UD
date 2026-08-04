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
import appeng.api.stacks.GenericStack;
import appeng.container.me.GridInventoryEntry;
import appeng.core.AEConfig;
import appeng.util.ISlimReadableNumberConverter;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.ReadableNumberConverter;
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
    private static final ISlimReadableNumberConverter SLIM_CONVERTER = ReadableNumberConverter.INSTANCE;
    private static final IWideReadableNumberConverter WIDE_CONVERTER = ReadableNumberConverter.INSTANCE;

    /**
     * The ME-slot flavour: carries the craftable flag alongside the amount. Replaces the old
     * {@code renderStackSize(FontRenderer, IAEItemStack, int, int)} at the one call site that fed it a
     * slot's live {@code IAEItemStack} ({@link appeng.client.gui.AEBaseGui#drawSlot}).
     */
    public void renderStackSize(FontRenderer fontRenderer, @Nullable GridInventoryEntry entry, int xPos, int yPos) {
        if (entry != null) {
            this.renderStackSize(fontRenderer, entry.getStoredAmount(), entry.isCraftable(), xPos, yPos);
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
     */
    public void renderStackSize(FontRenderer fontRenderer, @Nullable GenericStack stack, boolean craftable, int xPos, int yPos) {
        if (stack == null) {
            return;
        }
        if (stack.amount() == 1 && stack.what() instanceof AEItemKey) {
            if (craftable) {
                drawCraftableMark(fontRenderer, xPos, yPos);
            }
            return;
        }

        this.renderStackSize(fontRenderer, stack.amount(), craftable, xPos, yPos);
    }

    private void renderStackSize(FontRenderer fontRenderer, long amount, boolean craftable, int xPos, int yPos) {
        final float scaleFactor = AEConfig.instance().useTerminalUseLargeFont() ? 0.85f : 0.5f;
        final int offset = AEConfig.instance().useTerminalUseLargeFont() ? 0 : -1;

        final boolean unicodeFlag = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(false);

        if ((amount == 0 || GuiScreen.isAltKeyDown()) && craftable) {
            // Modern AE2's convention: "+" where the count goes, rather than the word "Craft".
            drawLabel(fontRenderer, "+", xPos, yPos, scaleFactor, offset);
        } else if (amount > 0) {
            drawLabel(fontRenderer, this.getToBeRenderedStackSize(amount), xPos, yPos, scaleFactor, offset);
            if (craftable) {
                drawCraftableMark(fontRenderer, xPos, yPos);
            }
        }

        fontRenderer.setUnicodeFlag(unicodeFlag);
    }

    /**
     * The small "+" that marks a slot as craftable without replacing whatever else it shows - stocked ME
     * rows that are also craftable, and any fake slot's configured key (pattern grid, pattern output, and
     * - via the HEI recipe screen mixin - a recipe ingredient the open terminal can already autocraft).
     * <p>
     * Fixed scale regardless of the large-font setting: an accent mark in the corner should stay small even
     * when the main numbers are big.
     */
    public static void drawCraftableMark(final FontRenderer fontRenderer, final int xPos, final int yPos) {
        final boolean unicodeFlag = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(false);

        // Left/top-aligned to the icon's own corner, unlike drawLabel's count - which is deliberately
        // right/bottom-aligned to xPos+16/yPos+16, the wrong anchor for a mark that belongs in the opposite
        // corner. Same offset the count uses, but subtracted rather than added: the count's offset nudges
        // it inward (toward xPos+16), so nudging this mark inward (toward xPos) means the opposite sign.
        final float scaleFactor = 0.5f;
        final float inverseScaleFactor = 1.0f / scaleFactor;
        final int offset = AEConfig.instance().useTerminalUseLargeFont() ? 0 : -1;
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleFactor, scaleFactor, scaleFactor);
        fontRenderer.drawStringWithShadow("+", (int) ((xPos - offset) * inverseScaleFactor), (int) ((yPos - offset) * inverseScaleFactor), 16777215);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();

        fontRenderer.setUnicodeFlag(unicodeFlag);
    }

    private static void drawLabel(final FontRenderer fontRenderer, final String text, final int xPos, final int yPos,
            final float scaleFactor, final int offset) {
        final float inverseScaleFactor = 1.0f / scaleFactor;

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleFactor, scaleFactor, scaleFactor);
        final int X = (int) (((float) xPos + offset + 16.0f - fontRenderer.getStringWidth(text) * scaleFactor) * inverseScaleFactor);
        final int Y = (int) (((float) yPos + offset + 16.0f - 7.0f * scaleFactor) * inverseScaleFactor);
        fontRenderer.drawStringWithShadow(text, X, Y, 16777215);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
    }

    private String getToBeRenderedStackSize(final long originalSize) {
        if (AEConfig.instance().useTerminalUseLargeFont()) {
            return SLIM_CONVERTER.toSlimReadableForm(originalSize);
        } else {
            return WIDE_CONVERTER.toWideReadableForm(originalSize);
        }
    }

}
