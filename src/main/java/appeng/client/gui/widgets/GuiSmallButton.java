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


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;


/**
 * A button in the vanilla frame that may be smaller than the 20x20 that frame was drawn at.
 * <p>
 * {@link GuiButton} splits the texture left and right only, and takes the full {@code height} from its top,
 * so anything shorter than 20 loses its bottom border and looks cut off. This takes each border from the
 * edge it belongs to, in four pieces, so the frame closes at any size up to 20x20.
 */
public class GuiSmallButton extends GuiButton {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/widgets.png");

    private static final int FRAME = 20;
    private static final int FRAME_WIDTH = 200;
    private static final int FRAME_TOP = 46;

    public GuiSmallButton(final int id, final int x, final int y, final int width, final int height,
            final String text) {
        super(id, x, y, width, height, text);
    }

    @Override
    public void drawButton(final Minecraft minecraft, final int mouseX, final int mouseY, final float partial) {
        if (!this.visible) {
            return;
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        minecraft.renderEngine.bindTexture(TEXTURE);
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;

        final int v = FRAME_TOP + this.getHoverState(this.hovered) * FRAME;

        final int leftWidth = Math.min(this.width, FRAME_WIDTH) / 2;
        final int rightWidth = Math.min(this.width, FRAME_WIDTH) - leftWidth;
        final int topHeight = Math.min(this.height, FRAME) / 2;
        final int bottomHeight = Math.min(this.height, FRAME) - topHeight;

        final int rightU = FRAME_WIDTH - rightWidth;
        final int bottomV = v + FRAME - bottomHeight;

        this.drawTexturedModalRect(this.x, this.y, 0, v, leftWidth, topHeight);
        this.drawTexturedModalRect(this.x + leftWidth, this.y, rightU, v, rightWidth, topHeight);
        this.drawTexturedModalRect(this.x, this.y + topHeight, 0, bottomV, leftWidth, bottomHeight);
        this.drawTexturedModalRect(this.x + leftWidth, this.y + topHeight, rightU, bottomV, rightWidth,
                bottomHeight);

        this.mouseDragged(minecraft, mouseX, mouseY);

        int color = 14737632;
        if (!this.enabled) {
            color = 10526880;
        } else if (this.hovered) {
            color = 16777120;
        }

        this.drawCenteredString(minecraft.fontRenderer, this.displayString, this.x + this.width / 2,
                this.y + (this.height - 8) / 2, color);
    }
}
