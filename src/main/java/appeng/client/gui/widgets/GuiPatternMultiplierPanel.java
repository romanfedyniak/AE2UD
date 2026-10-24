/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's pattern multi-tool (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui.widgets;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.client.gui.AEBaseGui;
import appeng.container.implementations.ContainerPatternMultiplier;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.patternmultiplier.IPatternMultiplierPanelHost;
import appeng.helpers.patternmultiplier.PatternEdits;
import appeng.helpers.patternmultiplier.PatternMultiplierHost;
import appeng.helpers.patternmultiplier.PatternMultiplierPanel;

/**
 * The carried pattern multi-tool beside a screen: its pattern columns, drawn behind the container's slots, and
 * the buttons that scale or clear what it holds. A screen keeps one, adds its buttons when it lays itself out,
 * draws it, reports its area to HEI and lets clicks on it count as inside the window.
 */
public final class GuiPatternMultiplierPanel {

    private static final String TEXTURE = "guis/pattern_multiplier_toolbox.png";
    private static final int BUTTONS_Y = PatternMultiplierPanel.SLOTS_INSET + 9 * 18 + 3;

    private final PatternMultiplierHost tool;
    private final List<PatternMultiplierPanel.PanelSlot> slots = new ArrayList<>();
    private final int top;
    private boolean hidden;
    private final List<GuiButton> buttons = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();
    private int guiLeft;
    private int guiTop;

    private GuiPatternMultiplierPanel(final PatternMultiplierHost tool, final int top) {
        this.tool = tool;
        this.top = top;
    }

    /**
     * The panel of this screen's container, or null when the player carries no tool.
     */
    @Nullable
    public static GuiPatternMultiplierPanel of(final Container container) {
        if (!(container instanceof IPatternMultiplierPanelHost)) {
            return null;
        }
        final PatternMultiplierHost tool = ((IPatternMultiplierPanelHost) container).getPatternMultiplier();
        if (tool == null) {
            return null;
        }
        final GuiPatternMultiplierPanel panel = new GuiPatternMultiplierPanel(tool, 0);
        for (final Slot slot : container.inventorySlots) {
            if (slot instanceof PatternMultiplierPanel.PanelSlot) {
                panel.slots.add((PatternMultiplierPanel.PanelSlot) slot);
            }
        }
        return panel;
    }

    /**
     * Out of sight, slots and buttons too, while the settings drawer or the terminal mode fan opens over the
     * same place. Asked every frame.
     */
    public void setHidden(final boolean hidden) {
        if (hidden == this.hidden) {
            return;
        }
        this.hidden = hidden;
        for (final PatternMultiplierPanel.PanelSlot slot : this.slots) {
            slot.setHidden(hidden);
        }
        for (final GuiButton button : this.buttons) {
            button.visible = !hidden;
        }
    }

    /**
     * Puts its buttons into the screen's list; safe to call every frame.
     */
    public void addTo(final List<GuiButton> buttonList, final int guiLeft, final int guiTop) {
        if (this.buttons.isEmpty() || guiLeft != this.guiLeft || guiTop != this.guiTop) {
            this.guiLeft = guiLeft;
            this.guiTop = guiTop;
            this.build();
        }
        for (final GuiButton button : this.buttons) {
            button.visible = !this.hidden;
            if (!buttonList.contains(button)) {
                buttonList.add(button);
            }
        }
    }

    private void build() {
        this.buttons.clear();
        this.actions.clear();
        final int left = this.guiLeft + PatternMultiplierPanel.LEFT + PatternMultiplierPanel.SLOTS_INSET;
        final int y = this.guiTop + this.top + BUTTONS_Y;
        this.button(new GuiImgButton(left, y, Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO),
                PatternEdits.Scale.MULTIPLY_BY_TWO.name());
        this.button(new GuiImgButton(left + 18, y, Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO),
                PatternEdits.Scale.DIVIDE_BY_TWO.name());
        this.button(new GuiImgButton(left + 36, y, Settings.ACTIONS, ActionItems.INCREASE_BY_ONE),
                PatternEdits.Scale.INCREASE_BY_ONE.name());
        this.button(new GuiImgButton(left + 54, y, Settings.ACTIONS, ActionItems.DECREASE_BY_ONE),
                PatternEdits.Scale.DECREASE_BY_ONE.name());
    }

    private void button(final GuiButton button, final String action) {
        this.buttons.add(button);
        this.actions.add(action);
    }

    /**
     * @return whether the button was the panel's
     */
    public boolean actionPerformed(final GuiButton button) {
        final int index = this.buttons.indexOf(button);
        if (index < 0) {
            return false;
        }
        try {
            NetworkHandler.instance().sendToServer(
                    new PacketValueConfig(ContainerPatternMultiplier.ACTION, this.actions.get(index)));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return true;
    }

    /**
     * The frame and the open columns; the slots draw themselves.
     */
    public void draw(final AEBaseGui screen, final int offsetX, final int offsetY) {
        if (this.hidden) {
            return;
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        screen.bindTexture(TEXTURE);
        final int x = offsetX + PatternMultiplierPanel.LEFT;
        final int y = offsetY + this.top;
        screen.drawTexturedModalRect(x, y, 0, 0, PatternMultiplierPanel.WIDTH, PatternMultiplierPanel.HEIGHT);
        // The texture shows every column past the first as locked; an open one is drawn over as the first.
        for (int column = 1; column < this.tool.getRows(); column++) {
            screen.drawTexturedModalRect(x + PatternMultiplierPanel.SLOTS_INSET + column * 18,
                    y + PatternMultiplierPanel.SLOTS_INSET, PatternMultiplierPanel.SLOTS_INSET,
                    PatternMultiplierPanel.SLOTS_INSET, 17, 9 * 18);
        }
    }

    public Rectangle getArea() {
        return new Rectangle(this.guiLeft + PatternMultiplierPanel.LEFT, this.guiTop + this.top,
                PatternMultiplierPanel.WIDTH, PatternMultiplierPanel.HEIGHT);
    }

    public void addExclusionArea(final List<Rectangle> areas) {
        if (!this.hidden) {
            areas.add(this.getArea());
        }
    }

    public boolean contains(final int mouseX, final int mouseY) {
        return !this.hidden && this.getArea().contains(mouseX, mouseY);
    }
}
