/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
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

package appeng.client.gui.widgets;


import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AmountFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

import javax.annotation.Nullable;


/**
 * One button of {@link GuiStepButtons}. It carries its step rather than wearing it: the label is fitted to
 * the button, and a fitted label is no longer a number that could be read back out of it.
 */
public class GuiStepButton extends GuiButton implements ITooltip {

    /** Air either side of the label, so it never sits against the button's frame. */
    private static final int PADDING = 4;

    /** What vanilla paints a button's label in, which this one draws for itself. */
    private static final int COLOR_NORMAL = 0xE0E0E0;
    private static final int COLOR_HOVERED = 0xFFFFA0;
    private static final int COLOR_DISABLED = 0xA0A0A0;

    /** Vanilla's own idea of how tall a line is when it centres one. */
    private static final int LINE_HEIGHT = 8;

    private final int index;
    private final boolean up;

    /** The step written out in full, when the label is shorter than it, and null when nothing was lost. */
    @Nullable
    private String full;

    GuiStepButton(final int x, final int y, final int width, final int height, final int index, final boolean up) {
        super(0, x, y, width, height, "");

        this.index = index;
        this.up = up;
    }

    int getIndex() {
        return this.index;
    }

    boolean isUp() {
        return this.up;
    }

    /**
     * Writes the label, shortening the step if the font gives it no room. The step is the player's and so
     * is the font, so neither can be assumed to fit: "10000" is five characters of whatever width the pack
     * in use draws them at, in a button as narrow as 22 pixels.
     */
    void setStep(final String prefix, final long step) {
        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        final String plain = prefix + step;

        if (font.getStringWidth(plain) + PADDING <= this.width) {
            this.displayString = plain;
            this.full = null;
            return;
        }

        this.displayString = prefix + AEKeyType.items().formatAmount(step, AmountFormat.SLOT);
        this.full = plain;
    }

    /**
     * Vanilla centres the label at whatever width the font gives it and lets it run over the frame. The
     * short form is four characters, which fits every column with the vanilla font, but a font that draws
     * them wider is still a font this button has to hold, so what is left over is taken in scale.
     */
    @Override
    public void drawButton(final Minecraft mc, final int mouseX, final int mouseY, final float partial) {
        if (!this.visible) {
            return;
        }

        final String label = this.displayString;
        this.displayString = "";
        super.drawButton(mc, mouseX, mouseY, partial);
        this.displayString = label;

        final FontRenderer font = mc.fontRenderer;
        final int width = font.getStringWidth(label);
        final float scale = width + PADDING <= this.width ? 1.0f : (this.width - PADDING) / (float) width;

        GlStateManager.pushMatrix();
        GlStateManager.translate(this.x + this.width / 2.0f, this.y + (this.height - LINE_HEIGHT * scale) / 2.0f, 0);
        GlStateManager.scale(scale, scale, 1.0f);
        font.drawString(label, -width / 2.0f, 0, this.labelColor(), true);
        GlStateManager.popMatrix();

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private int labelColor() {
        if (!this.enabled) {
            return COLOR_DISABLED;
        }
        return this.hovered ? COLOR_HOVERED : COLOR_NORMAL;
    }

    /** Only where the label is not the whole story - an unshortened step explains itself. */
    @Nullable
    @Override
    public String getMessage() {
        return this.full;
    }

    @Override
    public int xPos() {
        return this.x;
    }

    @Override
    public int yPos() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }
}
