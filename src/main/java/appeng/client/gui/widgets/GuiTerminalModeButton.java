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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;

import appeng.api.features.IWirelessTerminalMode;
import appeng.client.gui.AEBaseGui;
import appeng.core.localization.ButtonToolTips;

/**
 * One wireless terminal mode, drawn as the terminal part it is the wireless form of, on the standard button
 * plate.
 *
 * <p>The picture comes from the mode rather than from the mod's sprite sheet, because a mode registered by an
 * addon has no place on a sheet that ships with AE2.</p>
 */
public class GuiTerminalModeButton extends GuiButton implements ITooltip {

    private static final ResourceLocation STATES = new ResourceLocation("appliedenergistics2", "textures/guis/states.png");

    private final AEBaseGui parent;
    private final IWirelessTerminalMode mode;
    private final boolean unlocked;
    private final boolean current;

    public GuiTerminalModeButton(final AEBaseGui parent, final IWirelessTerminalMode mode, final boolean unlocked,
            final boolean current) {
        super(0, 0, 0, 16, 16, "");
        this.parent = parent;
        this.mode = mode;
        this.unlocked = unlocked;
        this.current = current;
        this.enabled = unlocked;
    }

    public IWirelessTerminalMode getMode() {
        return this.mode;
    }

    public boolean isUnlocked() {
        return this.unlocked;
    }

    @Override
    public void drawButton(final Minecraft mc, final int mouseX, final int mouseY, final float partial) {
        if (!this.visible) {
            return;
        }

        AEBaseGui.enableSpriteBlending();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        mc.renderEngine.bindTexture(STATES);
        this.drawTexturedModalRect(this.x, this.y, 256 - 16, 256 - 16, 16, 16);

        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;

        final ItemStack icon = this.mode.getIcon();
        if (!icon.isEmpty()) {
            this.parent.drawItem(this.x, this.y, icon);
        }

        // A mode that has not been bought is still shown, so the terminal says what it could become. Darkened
        // over the top of the item rather than by tinting it, because an item is drawn through a model whose
        // own colours ignore whatever tint is set before it.
        if (!this.unlocked) {
            drawRect(this.x, this.y, this.x + this.width, this.y + this.height, 0xAA202020);
            AEBaseGui.enableSpriteBlending();
        }

        this.mouseDragged(mc, mouseX, mouseY);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public String getMessage() {
        final String name = I18n.translateToLocal(this.mode.getUnlocalizedName());

        if (this.current) {
            return name + '\n' + ButtonToolTips.TerminalModeSwitch.getLocal();
        }

        if (this.unlocked) {
            return name;
        }

        final ItemStack ingredient = this.mode.getUnlockIngredient();
        if (ingredient.isEmpty()) {
            return name + '\n' + ButtonToolTips.TerminalModeUnobtainable.getLocal();
        }

        return name + '\n' + String.format(ButtonToolTips.TerminalModeLocked.getLocal(), ingredient.getDisplayName());
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
