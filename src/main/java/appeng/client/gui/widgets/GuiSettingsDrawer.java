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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;

/**
 * The settings a screen carries that are set once and then left alone, kept behind one button instead of in
 * the column down its side.
 *
 * <p>The column had grown to ten buttons on a wireless terminal, which is taller than the window they stand
 * beside and taller than the screen on a small display. What goes in here rather than staying out is a rule
 * rather than a taste: settings that belong to the whole client - the search box mode, whether a query is
 * kept, the terminal style - and the rare ones that belong to the item or the host, while everything that
 * works this particular screen stays in the column where it can be reached in one click.</p>
 *
 * <p>Opens as a column to the left of its own button, which is why that button is the first of a column and
 * not the last: the drawer then grows from the top of the window downwards and cannot hang off the bottom of
 * the screen it was meant to fit on.</p>
 */
public final class GuiSettingsDrawer {

    private static final int SIZE = 16;
    private static final int STEP = 20;

    private final List<GuiButton> contents = new ArrayList<>();

    private GuiImgButton toggle;
    private boolean open;
    private boolean opensLeft = true;

    /** For a column down the left of a window, which is where all but three screens keep theirs. */
    public int attach(final List<GuiButton> buttonList, final int left, final int top) {
        return this.attach(buttonList, left, top, true);
    }

    /**
     * Puts the drawer's button at the top of a column and answers where the rest of that column carries on.
     * Call before handing anything to {@link #take}, and once per layout. A column standing to the right of
     * its window opens away from it, or the drawer would cover the screen it belongs to.
     */
    public int attach(final List<GuiButton> buttonList, final int left, final int top, final boolean opensLeft) {
        this.contents.clear();
        this.opensLeft = opensLeft;

        if (this.toggle == null || this.toggle.x != left || this.toggle.y != top) {
            this.toggle = new GuiImgButton(left, top, Settings.ACTIONS, ActionItems.SETTINGS);
        }

        buttonList.add(this.toggle);
        return top + STEP;
    }

    /**
     * Puts the drawer's buttons back into a list, for the two screens that empty theirs on every frame. What
     * is in the drawer keeps the place and the visibility {@link #take} gave it.
     */
    public void addTo(final List<GuiButton> buttonList) {
        if (this.toggle == null) {
            return;
        }

        buttonList.add(this.toggle);
        buttonList.addAll(this.contents);
    }

    /**
     * Takes a button out of the column and into the drawer, placing it. The screen still adds it to its own
     * button list, so it is drawn, reported to HEI and clicked exactly like any other - it is simply
     * somewhere else, and invisible while the drawer is shut.
     */
    public <T extends GuiButton> T take(final T button) {
        button.x = this.toggle.x + (this.opensLeft ? -STEP : STEP);
        button.y = this.toggle.y + STEP * this.contents.size();
        button.visible = this.open;
        button.enabled = this.open;

        this.contents.add(button);
        return button;
    }

    /** What the drawer covers outside its window, so that HEI keeps its list off it. */
    public void addExclusionAreas(final List<Rectangle> areas) {
        if (this.toggle == null) {
            return;
        }

        areas.add(new Rectangle(this.toggle.x, this.toggle.y, SIZE, SIZE));

        if (!this.open) {
            return;
        }

        for (final GuiButton button : this.contents) {
            areas.add(new Rectangle(button.x, button.y, SIZE, SIZE));
        }
    }

    /**
     * @return true when the click was the drawer's own button, and the screen should make nothing else of it.
     */
    public boolean actionPerformed(final GuiButton button) {
        if (this.toggle == null || button != this.toggle) {
            return false;
        }

        this.setOpen(!this.open);
        return true;
    }

    /** Shut, so that another panel opening over the same strip of screen does not collide with this one. */
    public void close() {
        this.setOpen(false);
    }

    public boolean isOpen() {
        return this.open;
    }

    private void setOpen(final boolean open) {
        this.open = open;

        for (final GuiButton button : this.contents) {
            button.visible = open;
            button.enabled = open;
        }
    }
}
