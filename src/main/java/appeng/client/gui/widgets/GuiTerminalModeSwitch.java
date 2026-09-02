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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTerminalMode;
import appeng.client.gui.AEBaseGui;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchTerminalMode;
import appeng.helpers.WirelessTerminalModes;

/**
 * The control that changes which mode a wireless terminal is showing: one button carrying the mode it is in
 * now, which opens out into the rest of them.
 *
 * <p>A fan rather than a column of its own, because the column down the left of a terminal already carries up
 * to seven buttons, and another five under them would run off the bottom of a small screen. Modes that have
 * not been unlocked are in the fan too, greyed - otherwise a fresh terminal shows one button and nothing on
 * the screen says that modes exist at all.</p>
 */
public final class GuiTerminalModeSwitch {

    private static final int SIZE = 16;
    private static final int STEP = 20;
    private static final int COLUMN_HEIGHT = 4;

    private final AEBaseGui parent;
    private final List<GuiTerminalModeButton> fan = new ArrayList<>();

    private GuiTerminalModeButton toggle;
    private ItemStack terminal = ItemStack.EMPTY;
    private boolean open;

    private ResourceLocation builtFor;
    private List<ResourceLocation> builtUnlocked;
    private int builtLeft;
    private int builtTop;

    public GuiTerminalModeSwitch(final AEBaseGui parent) {
        this.parent = parent;
    }

    /**
     * Puts the switch into a screen's button list, building it first if this is the first time or if anything
     * it was built from has changed.
     *
     * <p>Call after the screen has added its own buttons: the switch goes under whatever column they form and
     * finds the bottom of it by looking rather than by counting, so a hidden button, a gap or an odd spacing
     * all answer correctly. Safe to call every frame, which the interface terminal needs, because it empties
     * its button list on each one.</p>
     */
    public void attach(final List<GuiButton> buttonList, final ItemStack terminal, final int guiLeft,
            final int guiTop) {
        this.terminal = terminal;

        // Ours come out before the column is measured, or the switch would walk itself down the screen.
        buttonList.remove(this.toggle);
        buttonList.removeAll(this.fan);

        if (this.needsRebuild(terminal, guiLeft, guiTop)) {
            this.rebuild(buttonList, terminal, guiLeft, guiTop);
        }

        if (this.toggle == null) {
            return;
        }

        buttonList.add(this.toggle);
        buttonList.addAll(this.fan);
    }

    /**
     * The mode a terminal is in reaches the client in the item's own NBT, which arrives a tick after the
     * screen that was opened for it. Rebuilding when it changes is what stops the button showing the mode
     * before last.
     */
    private boolean needsRebuild(final ItemStack terminal, final int guiLeft, final int guiTop) {
        if (this.toggle == null || guiLeft != this.builtLeft || guiTop != this.builtTop) {
            return true;
        }

        final IWirelessTerminalMode active = WirelessTerminalModes.getActiveMode(terminal);
        final ResourceLocation id = active == null ? null : active.getId();

        return !Objects.equals(id, this.builtFor)
                || !WirelessTerminalModes.getUnlocked(terminal).equals(this.builtUnlocked);
    }

    private void rebuild(final List<GuiButton> buttonList, final ItemStack terminal, final int guiLeft,
            final int guiTop) {
        this.fan.clear();
        this.toggle = null;
        this.builtLeft = guiLeft;
        this.builtTop = guiTop;
        this.builtUnlocked = WirelessTerminalModes.getUnlocked(terminal);

        final IWirelessTerminalMode active = WirelessTerminalModes.getActiveMode(terminal);
        this.builtFor = active == null ? null : active.getId();
        if (active == null) {
            return;
        }

        final int left = guiLeft - 18;
        int top = guiTop + 8;
        for (final GuiButton button : buttonList) {
            if (button.x == left && button.y > top) {
                top = button.y;
            }
        }
        top += STEP;

        this.toggle = new GuiTerminalModeButton(this.parent, active, true, true);
        this.toggle.x = left;
        this.toggle.y = top;

        int i = 0;
        for (final IWirelessTerminalMode mode : AEApi.instance().registries().wirelessTerminalModes().getModes()) {
            if (mode.getId().equals(active.getId())) {
                continue;
            }

            final GuiTerminalModeButton button = new GuiTerminalModeButton(this.parent, mode,
                    WirelessTerminalModes.isUnlocked(terminal, mode.getId()), false);
            button.x = left - STEP * (1 + i / COLUMN_HEIGHT);
            button.y = top + STEP * (i % COLUMN_HEIGHT);

            this.fan.add(button);
            i++;
        }

        this.setOpen(this.open);
    }

    /**
     * @return true when the click belonged to the switch, and the screen should make nothing else of it.
     */
    public boolean actionPerformed(final GuiButton button) {
        if (button == this.toggle) {
            this.setOpen(!this.open);
            return true;
        }

        if (!(button instanceof GuiTerminalModeButton) || !this.fan.contains(button)) {
            return false;
        }

        final ResourceLocation mode = ((GuiTerminalModeButton) button).getMode().getId();

        // Written here as well as on the server, because the screen this opens is built from the client's own
        // copy of the item, and the server's answer to that copy is a tick behind the screen it asks for.
        if (!this.terminal.isEmpty()) {
            WirelessTerminalModes.setModeId(this.terminal, mode);
        }

        try {
            NetworkHandler.instance().sendToServer(new PacketSwitchTerminalMode(mode));
        } catch (final IOException e) {
            AELog.debug(e);
        }

        this.setOpen(false);
        return true;
    }

    /** Shut, so that another panel opening over the same strip of screen does not collide with this one. */
    public void close() {
        this.setOpen(false);
    }

    private void setOpen(final boolean open) {
        this.open = open;

        for (final GuiTerminalModeButton button : this.fan) {
            button.visible = open;
            button.enabled = open && button.isUnlocked();
        }
    }

    /**
     * What HEI has to keep its item list off. Only the toggle while the fan is shut - reporting the whole
     * block all the time would fence off a piece of screen that has nothing on it.
     */
    public void addExclusionAreas(final List<Rectangle> areas) {
        if (this.toggle == null) {
            return;
        }

        areas.add(new Rectangle(this.toggle.x, this.toggle.y, SIZE, SIZE));

        if (!this.open) {
            return;
        }

        for (final GuiTerminalModeButton button : this.fan) {
            areas.add(new Rectangle(button.x, button.y, SIZE, SIZE));
        }
    }
}
