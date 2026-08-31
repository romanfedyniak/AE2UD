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
import java.util.List;

import net.minecraft.client.gui.Gui;

import appeng.client.gui.AEBaseGui;

/**
 * The plate a wireless terminal wears against the right edge of its window, holding its upgrade slots.
 *
 * <p>Drawn in three pieces out of the one-slot texture - the edge above, one band per slot, the edge below -
 * so the plate is as tall as the terminal has slots without a texture per count. All of it lies outside the
 * window, so a screen wearing one has to report it to HEI as well as draw it.</p>
 */
public final class GuiWirelessUpgradePlate {

    /** The plate's own edge, above the first slot and below the last. */
    private static final int EDGE = 7;
    private static final int SLOT = 18;
    private static final int WIDTH = 32;

    private GuiWirelessUpgradePlate() {
    }

    public static int height(final int slots) {
        return EDGE * 2 + slots * SLOT;
    }

    public static void draw(final AEBaseGui gui, final int x, final int y, final int slots) {
        gui.bindTexture("guis/wirelessupgrades.png");

        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, WIDTH, EDGE, WIDTH, WIDTH);

        for (int slot = 0; slot < slots; slot++) {
            Gui.drawModalRectWithCustomSizedTexture(x, y + EDGE + slot * SLOT, 0, EDGE, WIDTH, SLOT, WIDTH, WIDTH);
        }

        Gui.drawModalRectWithCustomSizedTexture(x, y + EDGE + slots * SLOT, 0, WIDTH - EDGE, WIDTH, EDGE,
                WIDTH, WIDTH);
    }

    public static void addExclusionArea(final List<Rectangle> areas, final int x, final int y, final int slots) {
        areas.add(new Rectangle(x, y, WIDTH, height(slots)));
    }
}
