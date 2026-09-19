/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui.implementations.pattern;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.inventory.Slot;

import appeng.api.config.ActionItems;
import appeng.api.config.PatternSlotConfig;
import appeng.api.config.Settings;
import appeng.api.patterns.PatternEncodingMode;
import appeng.api.patterns.client.IPatternTerminalScreen;
import appeng.api.patterns.client.PatternModePanel;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.container.slot.AppEngSlot;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.PatternHelper;
import appeng.helpers.encoding.IProcessingEncodingHost;
import appeng.helpers.encoding.ProcessingEncodingMode;

/** Two grids of any keys, paged, with the cluster of amount buttons in the gap between them. */
public class ProcessingModePanel extends PatternModePanel {

    private static final int HEIGHT = 81;

    private static final String BACKGROUND = "guis/pattern3.png";
    private static final String BACKGROUND_INVERTED = "guis/pattern4.png";

    // Where the grids sit on the two textures. The inputs start at the same column either way; only the
    // outputs move, from a single column on the right to the wide grid.
    private static final int GRID_TOP_FROM_BOTTOM = 164;
    private static final int INPUT_X = 15;
    private static final int OUTPUT_COMPACT_X = 112;
    private static final int OUTPUT_EXPANDED_X = 58;

    /** The button cluster lives in the gap between the two grids, and inverting moves that gap three slots left. */
    private static final int BUTTONS_LEFT = 88;
    private static final int BUTTONS_INVERTED_SHIFT = -18 * 3;

    private final GuiScrollbar pageScrollBar = new GuiScrollbar();

    private GuiImgButton invertBtn;
    private GuiImgButton x2Btn;
    private GuiImgButton x3Btn;
    private GuiImgButton plusOneBtn;
    private GuiImgButton divTwoBtn;
    private GuiImgButton divThreeBtn;
    private GuiImgButton minusOneBtn;

    public ProcessingModePanel(final PatternEncodingMode mode, final IPatternTerminalScreen screen) {
        super(mode, screen);
        this.pageScrollBar.setLeft(6).setWidth(7).setHeight(18 * PatternHelper.PROCESSING_GRID_DIMENSION - 2);
        this.pageScrollBar.setRange(0, PatternHelper.PROCESSING_PAGES - 1, 1);
        this.pageScrollBar.setTexture(AppEng.MOD_ID, BACKGROUND, 243, 0);
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public String getBackground() {
        return this.isInverted() ? BACKGROUND_INVERTED : BACKGROUND;
    }

    private boolean isInverted() {
        return this.getScreen().getHost() instanceof IProcessingEncodingHost processing && processing.isInverted();
    }

    private int activePage() {
        return this.pageScrollBar.getCurrentScroll();
    }

    /**
     * A recipe always fills the grid from the start, so arriving on the second page shows empty slots and reads
     * as a transfer that did nothing.
     */
    @Override
    public void onPatternLoaded() {
        this.pageScrollBar.setCurrentScroll(0);
    }

    @Override
    public void layOut() {
        this.pageScrollBar.setTop(this.getScreen().getYSize() - GRID_TOP_FROM_BOTTOM);

        final boolean inverted = this.isInverted();
        final List<Slot> inputs = this.getScreen().getGridSlots(ProcessingEncodingMode.INPUTS);
        final List<Slot> outputs = this.getScreen().getGridSlots(ProcessingEncodingMode.OUTPUTS);

        for (int i = 0; i < inputs.size(); i++) {
            this.place(inputs.get(i), i, inverted, INPUT_X);
        }

        for (int i = 0; i < outputs.size(); i++) {
            this.place(outputs.get(i), i, !inverted, inverted ? OUTPUT_EXPANDED_X : OUTPUT_COMPACT_X);
        }
    }

    /**
     * Places one slot of a grid. The expanded side fills the whole four-by-four grid and pages through it; the
     * compact side shows a single column, one page's worth at a time.
     */
    private void place(final Slot slot, final int index, final boolean compact, final int left) {
        final int dimension = PatternHelper.PROCESSING_GRID_DIMENSION;
        final int page = index / (dimension * dimension);
        final int x = index % dimension;
        final int y = index / dimension % dimension;
        final int top = this.getScreen().getYSize() - GRID_TOP_FROM_BOTTOM;

        if (compact) {
            ((AppEngSlot) slot).setHidden(page != 0 || y != this.activePage());
            this.getScreen().placeSlot(slot, left, top + 18 * x);
        } else {
            ((AppEngSlot) slot).setHidden(page != this.activePage());
            this.getScreen().placeSlot(slot, left + 18 * x, top + 18 * y);
        }
    }

    @Override
    public void addButtons(final List<GuiButton> buttons) {
        final int left = this.getScreen().getGuiLeft();
        final int bottom = this.getScreen().getGuiTop() + this.getScreen().getYSize();

        this.invertBtn = halfSize(new GuiImgButton(left, bottom - 165, Settings.ACTIONS, PatternSlotConfig.C_32_8));
        this.x3Btn = halfSize(new GuiImgButton(left, bottom - 158, Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE));
        this.x2Btn = halfSize(new GuiImgButton(left, bottom - 148, Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO));
        this.plusOneBtn = halfSize(new GuiImgButton(left, bottom - 138, Settings.ACTIONS, ActionItems.INCREASE_BY_ONE));
        this.divThreeBtn = halfSize(new GuiImgButton(left, bottom - 158, Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE));
        this.divTwoBtn = halfSize(new GuiImgButton(left, bottom - 148, Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO));
        this.minusOneBtn = halfSize(new GuiImgButton(left, bottom - 138, Settings.ACTIONS, ActionItems.DECREASE_BY_ONE));

        buttons.add(this.invertBtn);
        buttons.add(this.x3Btn);
        buttons.add(this.x2Btn);
        buttons.add(this.plusOneBtn);
        buttons.add(this.divThreeBtn);
        buttons.add(this.divTwoBtn);
        buttons.add(this.minusOneBtn);
    }

    private static GuiImgButton halfSize(final GuiImgButton button) {
        button.setHalfSize(true);
        return button;
    }

    @Override
    public void updateButtons() {
        final boolean inverted = this.isInverted();
        final int left = BUTTONS_LEFT + (inverted ? BUTTONS_INVERTED_SHIFT : 0);
        final int right = left + 10;
        final int top = this.getScreen().getYSize() - 165;

        this.getScreen().placeButton(IPatternTerminalScreen.TerminalButton.CLEAR, left, top);
        this.move(this.invertBtn, right, top);
        this.move(this.divTwoBtn, left, top + 10);
        this.move(this.x2Btn, right, top + 10);
        this.move(this.divThreeBtn, left, top + 20);
        this.move(this.x3Btn, right, top + 20);
        this.move(this.minusOneBtn, left, top + 30);
        this.move(this.plusOneBtn, right, top + 30);

        this.invertBtn.set(inverted ? PatternSlotConfig.C_8_32 : PatternSlotConfig.C_32_8);
    }

    private void move(final GuiImgButton button, final int x, final int y) {
        button.x = this.getScreen().getGuiLeft() + x;
        button.y = this.getScreen().getGuiTop() + y;
    }

    @Override
    public boolean actionPerformed(final GuiButton button) {
        final String name;
        final String value = "1";

        if (button == this.invertBtn) {
            this.send("PatternTerminal.Invert", this.isInverted() ? "0" : "1");
            return true;
        } else if (button == this.x2Btn) {
            name = "PatternTerminal.MultiplyByTwo";
        } else if (button == this.x3Btn) {
            name = "PatternTerminal.MultiplyByThree";
        } else if (button == this.divTwoBtn) {
            name = "PatternTerminal.DivideByTwo";
        } else if (button == this.divThreeBtn) {
            name = "PatternTerminal.DivideByThree";
        } else if (button == this.plusOneBtn) {
            name = "PatternTerminal.IncreaseByOne";
        } else if (button == this.minusOneBtn) {
            name = "PatternTerminal.DecreaseByOne";
        } else {
            return false;
        }

        this.send(name, value);
        return true;
    }

    private void send(final String name, final String value) {
        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig(name, value));
        } catch (final IOException e) {
            AELog.error(e);
        }
    }

    @Override
    public void drawForeground(final int mouseX, final int mouseY) {
        this.pageScrollBar.draw((GuiPatternTerm) this.getScreen());
    }

    @Override
    public boolean mouseClicked(final int x, final int y, final int button) {
        this.pageScrollBar.click((GuiPatternTerm) this.getScreen(), x, y);
        return false;
    }

    @Override
    public void mouseDragged(final int x, final int y, final int button) {
        this.pageScrollBar.click((GuiPatternTerm) this.getScreen(), x, y);
    }

    @Override
    public boolean mouseWheel(final int x, final int y, final int wheel) {
        if (this.pageScrollBar.contains(x, y)) {
            this.pageScrollBar.wheel(wheel);
            return true;
        }
        return false;
    }
}
