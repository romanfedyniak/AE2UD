/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.patterns.client;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import appeng.api.patterns.PatternEncodingMode;
import appeng.api.stacks.AEKey;

/**
 * What one {@link PatternEncodingMode} looks like in the pattern terminal: where its slots go, what it draws
 * around them and which buttons it owns. Built when the terminal switches to that mode and thrown away when it
 * leaves, so a panel may keep whatever state belongs to the screen.
 */
public abstract class PatternModePanel {

    private final PatternEncodingMode mode;
    private final IPatternTerminalScreen screen;

    protected PatternModePanel(final PatternEncodingMode mode, final IPatternTerminalScreen screen) {
        this.mode = mode;
        this.screen = screen;
    }

    public final PatternEncodingMode getMode() {
        return this.mode;
    }

    public final IPatternTerminalScreen getScreen() {
        return this.screen;
    }

    /**
     * How much room the terminal sets aside above the player's inventory. The list of what the network holds
     * takes whatever is left, so a tall panel leaves few rows of it. Zero leaves the window as the terminal
     * would draw it without a mode at all, name and all, for a panel that draws everything of its own
     * somewhere else.
     */
    public abstract int getHeight();

    /**
     * How wide the plate under the panel is drawn. Zero, the default, is the window's own width; anything
     * more hangs off its right edge, which is where a panel too wide for the window puts what will not fit.
     */
    public int getWidth() {
        return 0;
    }

    /**
     * Where else the panel draws outside the window - a plate beside it, say. Coordinates are the window's,
     * as a slot's are, and what a wide panel hangs off the right of its own band is already counted.
     * <p>
     * The terminal centres the window and everything named here together, so that a panel drawing beside the
     * window does not push half of it off the edge of the screen, and keeps a recipe viewer's list off it.
     */
    public List<Rectangle> getOutsideAreas() {
        return Collections.emptyList();
    }

    /**
     * Whether the terminal keeps the column the view cells are drawn in beside the window. A panel that says
     * no has that column's width taken off the window and puts the cells somewhere of its own - it is handed
     * them by {@code IPatternTerminalScreen.getViewCellSlots}, and a cell it never places is left where the
     * terminal built it.
     */
    public boolean showsViewCellColumn() {
        return true;
    }

    /**
     * The window texture behind the whole terminal, as a path under {@code textures/guis/}; null keeps the one
     * the terminal wears, which a panel that draws its own background wants.
     */
    @Nullable
    public String getBackground() {
        return null;
    }

    /** Places the mode's slots, and hides any of them this panel is not showing. Runs every frame. */
    public abstract void layOut();

    /** A whole pattern was just laid out over the grids, by HEI or by a pattern put into the terminal. */
    public void onPatternLoaded() {
    }

    /** The panel's own buttons, added when the screen is built. */
    public void addButtons(final List<GuiButton> buttons) {
    }

    /** Which of the panel's buttons are on screen, and where. Runs every frame, before anything is drawn. */
    public void updateButtons() {
    }

    /** @return true if the button was this panel's and has been dealt with. */
    public boolean actionPerformed(final GuiButton button) {
        return false;
    }

    /** Drawn behind the slots. */
    public void drawBackground(final int mouseX, final int mouseY) {
    }

    /** Drawn over the slots, where a tooltip or a hint belongs. */
    public void drawForeground(final int mouseX, final int mouseY) {
    }

    /**
     * What the panel is drawing under the cursor where no slot is - a result it works out itself, say - so
     * that a recipe viewer's look-up keys reach it. Null anywhere the panel has nothing of its own.
     */
    @Nullable
    public AEKey getKeyUnderMouse(final int x, final int y) {
        return null;
    }

    /** @return true if the click was the panel's, and the terminal should not go on to look at its slots. */
    public boolean mouseClicked(final int x, final int y, final int button) {
        return false;
    }

    public void mouseDragged(final int x, final int y, final int button) {
    }

    /** @return true if the notch was the panel's. */
    public boolean mouseWheel(final int x, final int y, final int wheel) {
        return false;
    }

    /**
     * A screen of the mode's own, shown instead of the terminal while this mode is on, for a layout too big to
     * fit above the player's inventory. It draws over the same container, so encoding and the grids work
     * exactly as they do in a panel; it is the screen's own business to offer a way back.
     */
    @Nullable
    public GuiScreen createOwnScreen() {
        return null;
    }
}
