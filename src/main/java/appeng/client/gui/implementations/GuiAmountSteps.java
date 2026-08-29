/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;


import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiNumberBox;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerAmountSteps;
import appeng.core.AmountSteps;
import appeng.core.AmountSteps.Group;
import appeng.core.AmountSteps.Mode;
import appeng.core.localization.GuiText;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Keyboard;

import java.io.IOException;


/**
 * What the step buttons of an amount screen do, laid out one modifier to a row.
 * <p>
 * Nothing here reaches a server: the steps are the client's own, and the screen is summoned over whichever
 * amount screen sent for it and puts that one back when it leaves. Leaving is also when it writes - what
 * stands in the fields is what the buttons will do, so there is no such thing as an unsaved change here,
 * and a field that reads as nothing at all is refused and left as it was.
 */
public class GuiAmountSteps extends AEBaseGui {

    private static final int MARGIN = 8;
    private static final int TITLE_Y = 6;

    private static final int LABEL_X = MARGIN;
    private static final int MODE_X = 42;
    private static final int MODE_WIDTH = 26;

    private static final int FIELD_X = 74;
    private static final int FIELD_WIDTH = 32;
    private static final int FIELD_GAP = 4;
    private static final int FIELD_HEIGHT = 12;

    private static final int ROW_TOP = 22;
    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 20;

    private static final int DEFAULTS_WIDTH = 66;

    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int REFUSED_COLOR = 0xFF5555;
    private static final int HEADING_COLOR = 4210752;

    private static final String ADD_LABEL = "+/-";
    private static final String MULTIPLY_LABEL = "×/÷";

    private static final int MAX_DIGITS = 10;

    /** The same icon the screens that open this one wear, so the way back is the way in. */
    private static final int ICON = 2 + 4 * 16;

    private static final int TAB_WIDTH = 22;

    private final GuiScreen parent;

    private final GuiTextField[][] fields = new GuiTextField[Group.values().length][AmountSteps.COUNT];
    private final GuiButton[] modeButtons = new GuiButton[Group.values().length];
    private final Mode[] modes = new Mode[Group.values().length];

    /** What the fields held before this screen was built, so a re-layout does not lose a half-typed step. */
    private final String[][] typed = new String[Group.values().length][AmountSteps.COUNT];

    private GuiTabButton back;
    private GuiButton defaults;

    public GuiAmountSteps(final GuiScreen parent, final InventoryPlayer inventoryPlayer) {
        super(new ContainerAmountSteps(inventoryPlayer));

        this.parent = parent;

        for (final Group group : Group.values()) {
            this.modes[group.ordinal()] = AmountSteps.mode(group);

            for (int i = 0; i < AmountSteps.COUNT; i++) {
                this.typed[group.ordinal()][i] = Integer.toString(AmountSteps.step(group, i));
            }
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        this.remember();

        this.xSize = FIELD_X + AmountSteps.COUNT * (FIELD_WIDTH + FIELD_GAP) - FIELD_GAP + MARGIN;
        this.ySize = ROW_TOP + Group.values().length * ROW_HEIGHT + BUTTON_HEIGHT + MARGIN;

        super.initGui();

        for (final Group group : Group.values()) {
            final int row = group.ordinal();
            final int top = this.guiTop + ROW_TOP + row * ROW_HEIGHT;

            this.modeButtons[row] = new GuiButton(0, this.guiLeft + MODE_X, top, MODE_WIDTH, BUTTON_HEIGHT, "");
            this.buttonList.add(this.modeButtons[row]);

            for (int i = 0; i < AmountSteps.COUNT; i++) {
                final GuiTextField field = new GuiNumberBox(this.fontRenderer, this.guiLeft + this.fieldX(i) + 2,
                        top + 6, FIELD_WIDTH - 4, this.fontRenderer.FONT_HEIGHT, Integer.class);

                field.setEnableBackgroundDrawing(false);
                field.setMaxStringLength(MAX_DIGITS);
                field.setTextColor(TEXT_COLOR);
                field.setVisible(true);
                field.setText(this.typed[row][i]);

                this.fields[row][i] = field;
            }
        }

        this.fields[0][0].setFocused(true);

        this.defaults = new GuiButton(0, this.guiLeft + MARGIN,
                this.guiTop + ROW_TOP + Group.values().length * ROW_HEIGHT, DEFAULTS_WIDTH, BUTTON_HEIGHT,
                GuiText.AmountStepsDefaults.getLocal());
        this.buttonList.add(this.defaults);

        this.back = new GuiTabButton(this.guiLeft + this.xSize - TAB_WIDTH, this.guiTop, ICON,
                GuiText.AmountSteps.getLocal(), this.itemRender);
        this.buttonList.add(this.back);

        this.updateModeLabels();
        this.validate();
    }

    /** Keeps what stands in the fields across a re-layout, which a change of window size runs. */
    private void remember() {
        for (int row = 0; row < this.fields.length; row++) {
            for (int i = 0; i < AmountSteps.COUNT; i++) {
                if (this.fields[row][i] != null) {
                    this.typed[row][i] = this.fields[row][i].getText();
                }
            }
        }
    }

    /**
     * The steps are put away here rather than on the way out of any one control, so that every way out of
     * this screen - the tab, Escape, the inventory key - saves the same way.
     */
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);

        final int[][] steps = new int[Group.values().length][AmountSteps.COUNT];

        for (final Group group : Group.values()) {
            for (int i = 0; i < AmountSteps.COUNT; i++) {
                final int typed = this.parse(group.ordinal(), i);
                steps[group.ordinal()][i] = typed > 0 ? typed : AmountSteps.step(group, i);
            }
        }

        AmountSteps.save(steps, this.modes);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        drawPanel(offsetX, offsetY, this.xSize, this.ySize);

        for (final Group group : Group.values()) {
            final int top = offsetY + ROW_TOP + group.ordinal() * ROW_HEIGHT;

            for (int i = 0; i < AmountSteps.COUNT; i++) {
                drawFieldWell(offsetX + this.fieldX(i), top + 4, FIELD_WIDTH, FIELD_HEIGHT);
            }
        }

        // drawRect leaves its colour set, and buttons are drawn textured after this.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        for (final GuiTextField[] row : this.fields) {
            for (final GuiTextField field : row) {
                field.drawTextBox();
            }
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // Already translated to the window origin, so these are window coordinates.
        this.fontRenderer.drawString(GuiText.AmountSteps.getLocal(), MARGIN, TITLE_Y, HEADING_COLOR);

        for (final Group group : Group.values()) {
            final int top = ROW_TOP + group.ordinal() * ROW_HEIGHT;
            this.fontRenderer.drawString(label(group), LABEL_X, top + 6, HEADING_COLOR);
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.back) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }

        if (btn == this.defaults) {
            this.resetToDefaults();
            return;
        }

        for (final Group group : Group.values()) {
            if (btn == this.modeButtons[group.ordinal()]) {
                final int row = group.ordinal();
                this.modes[row] = this.modes[row] == Mode.ADD ? Mode.MULTIPLY : Mode.ADD;
                this.updateModeLabels();
                return;
            }
        }
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int btn) throws IOException {
        for (final GuiTextField[] row : this.fields) {
            for (final GuiTextField field : row) {
                field.mouseClicked(mouseX, mouseY, btn);
            }
        }

        super.mouseClicked(mouseX, mouseY, btn);
    }

    /**
     * Escape and the inventory key would ordinarily tell the server to close a window, but this screen never
     * opened one - the player's real container is still the screen underneath. Put that back instead.
     */
    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || key == this.mc.gameSettings.keyBindInventory.getKeyCode()) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }

        for (final GuiTextField[] row : this.fields) {
            for (final GuiTextField field : row) {
                if (field.isFocused() && field.textboxKeyTyped(character, key)) {
                    this.validate();
                    return;
                }
            }
        }

        super.keyTyped(character, key);
    }

    @Override
    public boolean isTextFieldFocused() {
        for (final GuiTextField[] row : this.fields) {
            for (final GuiTextField field : row) {
                if (field.isFocused()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void resetToDefaults() {
        for (final Group group : Group.values()) {
            final int row = group.ordinal();
            this.modes[row] = AmountSteps.defaultMode(group);

            final int[] steps = AmountSteps.defaultSteps(group);
            for (int i = 0; i < AmountSteps.COUNT; i++) {
                this.fields[row][i].setText(Integer.toString(steps[i]));
            }
        }

        this.updateModeLabels();
        this.validate();
    }

    /** A step of nothing, or of zero, is refused: the field keeps what the buttons already do. */
    private void validate() {
        for (int row = 0; row < this.fields.length; row++) {
            for (int i = 0; i < AmountSteps.COUNT; i++) {
                this.fields[row][i].setTextColor(this.parse(row, i) > 0 ? TEXT_COLOR : REFUSED_COLOR);
            }
        }
    }

    private void updateModeLabels() {
        for (final Group group : Group.values()) {
            this.modeButtons[group.ordinal()].displayString =
                    this.modes[group.ordinal()] == Mode.ADD ? ADD_LABEL : MULTIPLY_LABEL;
        }
    }

    private int parse(final int row, final int index) {
        try {
            return Integer.parseInt(this.fields[row][index].getText());
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private int fieldX(final int index) {
        return FIELD_X + index * (FIELD_WIDTH + FIELD_GAP);
    }

    private static String label(final Group group) {
        return group == Group.NORMAL ? GuiText.AmountStepsNormal.getLocal() : group.modifier();
    }
}
