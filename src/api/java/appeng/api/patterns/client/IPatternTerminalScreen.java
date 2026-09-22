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

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.api.patterns.IPatternEncodingHost;

/**
 * The pattern terminal as a {@link PatternModePanel} sees it. Coordinates are relative to the window, with x
 * from its left edge and y from its top, the way a slot's position is written.
 */
public interface IPatternTerminalScreen {

    /** The terminal's own buttons, which every mode needs somewhere. */
    enum TerminalButton {
        ENCODE,
        CLEAR,
        UPLOAD,
        /** The tab that swaps crafting and processing; every mode's sits in the same place. */
        MODE_TAB,
        /** The button that opens the list of every mode, present only where an addon registered one. */
        MODES
    }

    /** The terminal's own slots, which every mode needs somewhere. */
    enum TerminalSlot {
        /** The blank patterns encoding spends. */
        BLANK_PATTERN,
        /** Where a freshly encoded pattern lands, and where one is put to be read back. */
        ENCODED_PATTERN
    }

    IPatternEncodingHost getHost();

    /** The ghost slots of one of the active mode's grids, in slot order. */
    List<Slot> getGridSlots(String grid);

    /**
     * The terminal's view cell slots, in the order the terminal lists them, for a panel that has taken their
     * column off the window. Empty on a terminal that carries no view cells.
     */
    List<Slot> getViewCellSlots();

    int getGuiLeft();

    int getGuiTop();

    int getXSize();

    /**
     * How wide the drawn window is, which is what a panel has to lay itself out in. Not the same as
     * {@link #getXSize()}: a terminal carrying view cells counts their column in that, and it stands well to
     * the right of the window a panel lives in.
     */
    int getPanelWidth();

    int getYSize();

    /** The top of the room set aside for the panel; the player's inventory begins below it. */
    int getPanelTop();

    /** Places one of the mode's ghost slots. */
    void placeSlot(Slot slot, int x, int y);

    /**
     * Places one of the terminal's own slots. A panel that leaves them where they are gets the places the
     * built-in modes use, which only suit a panel of their size.
     */
    void placeSlot(TerminalSlot slot, int x, int y);

    void placeButton(TerminalButton button, int x, int y);

    void setButtonVisible(TerminalButton button, boolean visible);

    /** Tells the server the player did something on this panel; the mode is handed it in onAction. */
    void sendModeAction(String action);

    /** Lays the screen out again once the current mouse event is over, e.g. when the panel changes height. */
    void refreshPanelLayout();

    FontRenderer getFontRenderer();

    void drawPanelBackground(int x, int y, int width, int height);

    /** The sunken square a ghost slot sits in, for a panel that draws itself rather than wearing a texture. */
    void drawSlotBackground(int x, int y);

    /** The same sunken well at any size, for the wider frame an output slot wears. */
    void drawWellBackground(int x, int y, int width, int height);

    /**
     * The pale square a slot wears under the cursor, for one the panel draws itself rather than holds - a
     * result it works out, say - so that it lights up the way every other slot on the screen does.
     */
    void drawSlotHighlight(int x, int y);

    /**
     * A flat rectangle, for the small decoration a panel that paints itself would otherwise need a texture
     * of its own for - an arrow between a grid and what it makes, a rule between two halves.
     *
     * @param colour packed ARGB
     */
    void drawRectangle(int x, int y, int width, int height, int colour);

    /** The item with everything a slot draws over it: how many there are, and how worn it is. */
    void drawItemStack(int x, int y, ItemStack stack);

    /**
     * How many there are, over a square the terminal draws itself - so a panel can say that a slot holding
     * one item stands for more than one. Drawn exactly as a slot draws a stack's size, and left out for a
     * count of one, which a slot does not draw either.
     */
    void drawItemCount(int x, int y, int count);

    void drawText(String text, int x, int y, int colour);

    void drawTooltip(List<String> lines, int x, int y);

    /** Binds a texture of any mod, given as modid and the path under {@code textures/}. */
    void bindTexture(String modId, String path);

    /** Draws from the bound texture, sized in pixels of a 256x256 sheet. */
    void drawTexture(int x, int y, int u, int v, int width, int height);
}
