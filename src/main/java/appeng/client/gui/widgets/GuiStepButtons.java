/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
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

package appeng.client.gui.widgets;


import appeng.core.AmountSteps;
import appeng.core.AmountSteps.Group;
import appeng.core.AmountSteps.Mode;
import com.google.common.math.LongMath;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.List;


/**
 * The two rows of step buttons an amount screen is built around: four that raise the amount above the
 * field, four that lower it below. Every screen that types an amount draws the same eight, so they are
 * made, labelled and read here rather than eight fields at a time.
 */
public final class GuiStepButtons {

    /** Where the four columns start, and how wide each is - wider to the right, as the steps grow. */
    private static final int[] COLUMN_X = {20, 48, 82, 120};
    private static final int[] COLUMN_WIDTH = {22, 28, 32, 38};
    private static final int HEIGHT = 20;

    private static final String PLUS = "+";
    private static final String MINUS = "-";
    private static final String TIMES = "×";
    private static final String OVER = "÷";

    private final GuiStepButton[] buttons = new GuiStepButton[2 * AmountSteps.COUNT];

    /** The group the labels were written for, so they are only rewritten when it changes. */
    private Group labelled;

    public void addTo(final List<GuiButton> buttonList, final int left, final int topRow, final int bottomRow) {
        for (int i = 0; i < AmountSteps.COUNT; i++) {
            this.buttons[i] = new GuiStepButton(left + COLUMN_X[i], topRow, COLUMN_WIDTH[i], HEIGHT, i, true);
            this.buttons[i + AmountSteps.COUNT] = new GuiStepButton(left + COLUMN_X[i], bottomRow, COLUMN_WIDTH[i],
                    HEIGHT, i, false);
        }

        this.labelled = null;
        this.update();

        for (final GuiStepButton button : this.buttons) {
            buttonList.add(button);
        }
    }

    /**
     * Puts the labels in step with whichever modifier is held, so what a press will do is on the button
     * before it is pressed. Screens call this once a frame.
     */
    public void update() {
        final Group group = active();
        if (group == this.labelled) {
            return;
        }

        this.labelled = group;
        final Mode mode = AmountSteps.mode(group);

        for (final GuiStepButton button : this.buttons) {
            button.setStep(prefix(mode, button.isUp()), AmountSteps.step(group, button.getIndex()));
        }
    }

    public void setEnabled(final boolean enabled) {
        for (final GuiStepButton button : this.buttons) {
            button.enabled = enabled;
        }
    }

    public boolean isStep(final GuiButton btn) {
        return btn instanceof GuiStepButton;
    }

    /** Whether a press right now adds rather than multiplies. */
    public boolean isAdditive() {
        return AmountSteps.mode(active()) == Mode.ADD;
    }

    /** The step behind a button, before any unit is applied to it. */
    public long stepOf(final GuiButton btn) {
        return AmountSteps.step(active(), ((GuiStepButton) btn).getIndex());
    }

    /**
     * @param scale the unit the amount is being read in. A step is written in that unit too - "+10" on a
     *              screen reading buckets adds ten buckets - while a factor is a factor in any unit.
     */
    public long apply(final GuiButton btn, final long current, final long min, final long max, final int scale) {
        final Group group = active();
        final Mode mode = AmountSteps.mode(group);
        final GuiStepButton button = (GuiStepButton) btn;
        final long step = AmountSteps.step(group, button.getIndex());

        return AmountSteps.apply(current, mode == Mode.ADD ? LongMath.saturatedMultiply(step, scale) : step,
                mode, button.isUp(), min, max);
    }

    private static Group active() {
        if (GuiScreen.isCtrlKeyDown()) {
            return Group.CTRL;
        }
        if (GuiScreen.isAltKeyDown()) {
            return Group.ALT;
        }
        return GuiScreen.isShiftKeyDown() ? Group.SHIFT : Group.NORMAL;
    }

    private static String prefix(final Mode mode, final boolean up) {
        if (mode == Mode.ADD) {
            return up ? PLUS : MINUS;
        }
        return up ? TIMES : OVER;
    }
}
