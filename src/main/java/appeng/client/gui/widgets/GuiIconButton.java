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


import appeng.client.gui.AEBaseGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;


/**
 * A square icon button in the same frame {@link GuiImgButton} uses, for an action rather than a setting -
 * that one draws whatever picture a setting's current value maps to, and an action has no value to map.
 */
public class GuiIconButton extends GuiButton implements ITooltip {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("appliedenergistics2", "textures/guis/states.png");
    private static final int SIZE = 16;

    private final int iconIndex;
    private final String message;

    public GuiIconButton(final int x, final int y, final int iconIndex, final String message) {
        super(0, 0, SIZE, "");

        this.x = x;
        this.y = y;
        this.width = SIZE;
        this.height = SIZE;
        this.iconIndex = iconIndex;
        this.message = message;
    }

    @Override
    public void drawButton(final Minecraft minecraft, final int mouseX, final int mouseY, final float partial) {
        if (!this.visible) {
            return;
        }

        AEBaseGui.enableSpriteBlending();
        GlStateManager.color(this.enabled ? 1.0f : 0.5f, this.enabled ? 1.0f : 0.5f,
                this.enabled ? 1.0f : 0.5f, 1.0f);
        minecraft.renderEngine.bindTexture(TEXTURE);
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;

        final int v = this.iconIndex / 16;
        final int u = this.iconIndex - v * 16;

        this.drawTexturedModalRect(this.x, this.y, 256 - SIZE, 256 - SIZE, SIZE, SIZE);
        this.drawTexturedModalRect(this.x, this.y, u * SIZE, v * SIZE, SIZE, SIZE);
        this.mouseDragged(minecraft, mouseX, mouseY);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public String getMessage() {
        return this.message;
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
