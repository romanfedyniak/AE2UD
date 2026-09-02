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
 * Every value of one setting, laid out at once and opened by the button that carries the value in use.
 *
 * <p>Stepping through a setting one click at a time is fine for three values and poor for eight, and worse
 * when half of them differ from a neighbour in one detail rather than in kind. The panel is given the shape
 * it should read as - a row to a thing, the columns being what varies about it - so which column is which is
 * learnt once rather than read off each icon.</p>
 *
 * <p>The buttons are ordinary {@link GuiImgButton}s, so every value already has its icon and its tooltip from
 * the registry that button keeps; a panel for a new setting is a layout and nothing else. Deliberately not
 * folded together with {@link GuiTerminalModeSwitch}: that one owns its toggle, draws icons an addon supplies,
 * knows which of its entries have been bought and sends a packet when one is picked, and abstracting over all
 * four for a single caller would cost more than the thirty lines the two have in common.</p>
 */
public final class GuiSettingPanel {

    private static final int STEP = 20;

    private final Settings setting;
    private final Enum[][] layout;
    private final List<ValueButton> panel = new ArrayList<>();

    private Enum builtFor;
    private int builtLeft = Integer.MIN_VALUE;
    private int builtTop = Integer.MIN_VALUE;
    private boolean open;

    public GuiSettingPanel(final Settings setting, final Enum[][] layout) {
        this.setting = setting;
        this.layout = layout;
    }

    /** The eight search box modes: a row to a mode, the plain one left and the one that keeps the text beside it. */
    public static GuiSettingPanel searchModes() {
        return new GuiSettingPanel(Settings.SEARCH_MODE, new Enum[][] {
                { SearchBoxMode.AUTOSEARCH, SearchBoxMode.AUTOSEARCH_KEEP },
                { SearchBoxMode.MANUAL_SEARCH, SearchBoxMode.MANUAL_SEARCH_KEEP },
                { SearchBoxMode.JEI_AUTOSEARCH, SearchBoxMode.JEI_AUTOSEARCH_KEEP },
                { SearchBoxMode.JEI_MANUAL_SEARCH, SearchBoxMode.JEI_MANUAL_SEARCH_KEEP },
        });
    }

    public Settings getSetting() {
        return this.setting;
    }

    /**
     * Puts the panel into a screen's button list, building it first if the button it hangs off has moved or
     * the value in use has changed. Safe to call every frame.
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

        for (int row = 0; row < this.layout.length; row++) {
            final Enum[] values = this.layout[row];

            for (int column = 0; column < values.length; column++) {
                this.panel.add(new ValueButton(toggle.x - STEP * (values.length - column),
                        toggle.y + STEP * row, this.setting, values[column], values[column] == current));
            }
        }

        this.setOpen(this.open);
    }

    public void toggleOpen() {
        this.setOpen(!this.open);
    }

    /** Shut, so that another panel opening over the same strip of screen does not collide with this one. */
    public void close() {
        this.setOpen(false);
    }

    /** The value a click chose, or null when the click was not the panel's. */
    @Nullable
    public Enum choose(final GuiButton button) {
        if (!(button instanceof ValueButton) || !this.panel.contains(button)) {
            return null;
        }

        this.setOpen(false);
        return ((ValueButton) button).value;
    }

    private void setOpen(final boolean open) {
        this.open = open;

        for (final ValueButton button : this.panel) {
            button.visible = open;
            button.enabled = open && !button.current;
        }
    }

    /** One value. The one in use is greyed and dead, which is what a button that would change nothing to
     * should look like. */
    private static final class ValueButton extends GuiImgButton {

        private final Enum value;
        private final boolean current;

        ValueButton(final int x, final int y, final Settings setting, final Enum value, final boolean current) {
            super(x, y, setting, value);
            this.value = value;
            this.current = current;
        }
    }
}
