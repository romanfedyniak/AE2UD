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

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiButton;

import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;

/**
 * The panel of search box modes, opened by the button that carries the one in use.
 *
 * <p>There are eight of them, and they are four modes each of which can also keep what was typed. Stepping
 * through eight settings one click at a time to find the one wanted is bad enough; doing it when half of them
 * differ from their neighbour only in that they keep the text is why this exists. Laid out as it reads - a row
 * to a mode, the plain one on the left and the keeping one beside it - so which column is which can be learnt
 * once rather than read off each icon.</p>
 */
public final class GuiSearchModeSwitch {

    private static final int STEP = 20;

    private static final SearchBoxMode[][] LAYOUT = {
            { SearchBoxMode.AUTOSEARCH, SearchBoxMode.AUTOSEARCH_KEEP },
            { SearchBoxMode.MANUAL_SEARCH, SearchBoxMode.MANUAL_SEARCH_KEEP },
            { SearchBoxMode.JEI_AUTOSEARCH, SearchBoxMode.JEI_AUTOSEARCH_KEEP },
            { SearchBoxMode.JEI_MANUAL_SEARCH, SearchBoxMode.JEI_MANUAL_SEARCH_KEEP },
    };

    private final List<ModeButton> panel = new ArrayList<>();

    private Enum builtFor;
    private int builtLeft = Integer.MIN_VALUE;
    private int builtTop = Integer.MIN_VALUE;
    private boolean open;

    /**
     * Puts the panel into a screen's button list, building it first if the button it hangs off has moved or
     * the mode in use has changed. Safe to call every frame.
     */
    public void attach(final List<GuiButton> buttonList, final GuiButton toggle, final Enum current) {
        buttonList.removeAll(this.panel);

        if (toggle == null) {
            return;
        }

        if (current != this.builtFor || toggle.x != this.builtLeft || toggle.y != this.builtTop) {
            this.rebuild(toggle, current);
        }

        buttonList.addAll(this.panel);
    }

    private void rebuild(final GuiButton toggle, final Enum current) {
        this.panel.clear();
        this.builtFor = current;
        this.builtLeft = toggle.x;
        this.builtTop = toggle.y;

        for (int row = 0; row < LAYOUT.length; row++) {
            for (int column = 0; column < LAYOUT[row].length; column++) {
                final SearchBoxMode mode = LAYOUT[row][column];
                this.panel.add(new ModeButton(toggle.x - STEP * (LAYOUT[row].length - column),
                        toggle.y + STEP * row, mode, mode == current));
            }
        }

        this.setOpen(this.open);
    }

    public void toggleOpen() {
        this.setOpen(!this.open);
    }

    public void close() {
        this.setOpen(false);
    }

    /** The mode a click chose, or null when the click was not the panel's. */
    @Nullable
    public SearchBoxMode choose(final GuiButton button) {
        if (!(button instanceof ModeButton) || !this.panel.contains(button)) {
            return null;
        }

        this.setOpen(false);
        return ((ModeButton) button).mode;
    }

    private void setOpen(final boolean open) {
        this.open = open;

        for (final ModeButton button : this.panel) {
            button.visible = open;
            button.enabled = open && !button.current;
        }
    }

    /** One mode. The one in use is greyed and dead, which is what a button that would change nothing to
     * should look like. */
    private static final class ModeButton extends GuiImgButton {

        private final SearchBoxMode mode;
        private final boolean current;

        ModeButton(final int x, final int y, final SearchBoxMode mode, final boolean current) {
            super(x, y, Settings.SEARCH_MODE, mode);
            this.mode = mode;
            this.current = current;
        }
    }
}
