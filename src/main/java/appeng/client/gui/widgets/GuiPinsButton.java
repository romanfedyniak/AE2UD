/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui.widgets;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;

import appeng.core.AppEng;

public final class GuiPinsButton extends GuiButton implements ITooltip {
    private static final ResourceLocation STATES = new ResourceLocation(AppEng.MOD_ID, "textures/guis/states.png");
    private int craftingRows;
    private int playerRows;

    public GuiPinsButton(int x, int y) {
        super(0, x, y, 16, 16, "P");
    }

    public void setRows(int craftingRows, int playerRows) {
        this.craftingRows = craftingRows;
        this.playerRows = playerRows;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        mc.getTextureManager().bindTexture(STATES);
        GlStateManager.color(enabled ? 1.0F : 0.6F, enabled ? 1.0F : 0.6F, enabled ? 1.0F : 0.6F, 1.0F);
        drawTexturedModalRect(x, y, 256 - 16, 256 - 16, 16, 16);
        drawTexturedModalRect(x, y, 14 * 16, 5 * 16, 16, 16);
        mouseDragged(mc, mouseX, mouseY);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public String getMessage() {
        return I18n.translateToLocal("gui.appliedenergistics2.pins") + "\n"
                + I18n.translateToLocalFormatted("gui.appliedenergistics2.playerPinRows", playerRows) + "\n"
                + I18n.translateToLocalFormatted("gui.appliedenergistics2.craftingPinRows", craftingRows);
    }

    @Override
    public int xPos() {
        return x;
    }

    @Override
    public int yPos() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }
}
