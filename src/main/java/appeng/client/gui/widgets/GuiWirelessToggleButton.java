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

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.features.WirelessTerminalToggle;
import appeng.api.features.WirelessTerminalToggles;
import appeng.client.gui.AEBaseGui;
import appeng.container.interfaces.IWirelessTerminalContainer;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketWirelessToggle;

/**
 * An addon's {@link WirelessTerminalToggle}, as a button in a wireless screen's settings drawer. It reads the
 * terminal every frame, so it shows what the item says even after the server has answered.
 */
public class GuiWirelessToggleButton extends GuiButton implements ITooltip {

    private static final ResourceLocation STATES = new ResourceLocation("appliedenergistics2", "textures/guis/states.png");

    private final WirelessTerminalToggle toggle;
    private final IWirelessTerminalContainer container;

    private GuiWirelessToggleButton(final WirelessTerminalToggle toggle, final IWirelessTerminalContainer container) {
        super(0, 0, 0, 16, 16, "");
        this.toggle = toggle;
        this.container = container;
    }

    /**
     * @return a button for every registered toggle, or none when the screen is not a wireless terminal's.
     */
    public static List<GuiWirelessToggleButton> forScreen(final Container container) {
        final List<GuiWirelessToggleButton> buttons = new ArrayList<>();
        if (container instanceof IWirelessTerminalContainer wireless) {
            for (final WirelessTerminalToggle toggle : WirelessTerminalToggles.getAll()) {
                buttons.add(new GuiWirelessToggleButton(toggle, wireless));
            }
        }
        return buttons;
    }

    private boolean isOn() {
        final ItemStack terminal = this.container.getTerminal();
        return terminal.isEmpty() ? this.toggle.getDefaultValue() : this.toggle.isOn(terminal);
    }

    /** Flips the switch here at once, and asks the server to do the same to the real item. */
    public void press() {
        final boolean next = !this.isOn();
        final ItemStack terminal = this.container.getTerminal();
        if (!terminal.isEmpty()) {
            this.toggle.set(terminal, next);
        }
        NetworkHandler.instance().sendToServer(new PacketWirelessToggle(this.toggle.getId(), next));
    }

    @Override
    public void drawButton(final Minecraft mc, final int mouseX, final int mouseY, final float partial) {
        if (!this.visible) {
            return;
        }
        final boolean on = this.isOn();
        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
        AEBaseGui.enableSpriteBlending();
        GlStateManager.color(1, 1, 1, 1);
        mc.renderEngine.bindTexture(STATES);
        this.drawTexturedModalRect(this.x, this.y, 256 - 16, 256 - 16, 16, 16);
        mc.renderEngine.bindTexture(this.toggle.getTexture());
        this.drawTexturedModalRect(this.x, this.y, this.toggle.getU(on), this.toggle.getV(on), 16, 16);
    }

    @Override
    public String getMessage() {
        final String state = this.toggle.getStateKey(this.isOn());
        final String title = I18n.format(this.toggle.getTitleKey());
        return state == null ? title : title + '\n' + wrap(I18n.format(state));
    }

    /** Broken at the first space past every thirty characters, as the mod's other setting buttons do. */
    private static String wrap(final String text) {
        final StringBuilder sb = new StringBuilder(text);
        int i = Math.max(0, sb.lastIndexOf("\n"));
        while (i + 30 < sb.length() && (i = sb.lastIndexOf(" ", i + 30)) != -1) {
            sb.replace(i, i + 1, "\n");
        }
        return sb.toString();
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
