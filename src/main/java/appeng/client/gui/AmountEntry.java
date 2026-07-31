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

package appeng.client.gui;


import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.AEKey;
import appeng.core.AEConfig;
import appeng.core.AELog;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * Converts between an amount and the text a player types for it, in whichever unit the client is set to
 * read. Shared by every screen with an amount field, so that "1.5" means the same thing in all of them.
 * <p>
 * Purely a display concern: packets always carry the base unit, so two players on one server can disagree
 * about what they are looking at without anything diverging.
 */
public final class AmountEntry {

    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(Long.MAX_VALUE);

    private static final int SUFFIX_GAP = 1;
    private static final int SUFFIX_COLOR = 0xFFFFFF;

    /**
     * Where a text box starts drawing from. Private, and there is no accessor: {@code drawTextBox}
     * renders {@code text.substring(lineScrollOffset)} trimmed to the box width, so without it there is
     * no way to know how wide the amount was actually drawn - only how wide the whole string would be.
     */
    private static final Field LINE_SCROLL_OFFSET = findLineScrollOffset();

    private static Field findLineScrollOffset() {
        try {
            return ReflectionHelper.findField(GuiTextField.class, "lineScrollOffset", "field_146225_q");
        } catch (final RuntimeException e) {
            AELog.debug(e);
            return null;
        }
    }

    private AmountEntry() {
    }

    /**
     * The scale {@code what} could be read in, ignoring whether the player has asked for it. One for a
     * type that has no larger unit.
     * <p>
     * Only powers of ten qualify. The field has to survive a round trip through decimal text, and a unit
     * of, say, 144 does not divide into one - the amount would drift every time it was re-read.
     */
    public static int unitOf(@Nullable final AEKey what) {
        if (what == null) {
            return 1;
        }

        final int perUnit = what.getAmountPerUnit();
        for (long power = 10; power <= perUnit; power *= 10) {
            if (power == perUnit) {
                return perUnit;
            }
        }
        return 1;
    }

    /**
     * The scale a field showing {@code what} reads in right now: {@link #unitOf} when unit entry is on,
     * otherwise 1 for the base unit.
     */
    public static int scaleOf(@Nullable final AEKey what) {
        return unitsEnabled() ? unitOf(what) : 1;
    }

    public static boolean unitsEnabled() {
        return AEConfig.instance().getConfigManager().getSetting(Settings.AMOUNT_ENTRY_UNITS) == YesNo.YES;
    }

    public static void toggleUnits() {
        AEConfig.instance().getConfigManager()
                .putSetting(Settings.AMOUNT_ENTRY_UNITS, unitsEnabled() ? YesNo.NO : YesNo.YES);
    }

    /**
     * Renders a raw amount for an entry field. Above the base unit the fractional part is written out in
     * full, so reading the field back gives the same number: 1001 mB is "1.001", never "1".
     */
    public static String format(final long amount, final int scale) {
        if (scale <= 1) {
            return Long.toString(amount);
        }

        final long remainder = amount % scale;
        if (remainder == 0) {
            return Long.toString(amount / scale);
        }

        String fraction = String.format("%0" + (Long.toString(scale).length() - 1) + "d", Math.abs(remainder));
        while (fraction.endsWith("0")) {
            fraction = fraction.substring(0, fraction.length() - 1);
        }
        return amount / scale + "." + fraction;
    }

    /**
     * Reads an entry field back into a raw amount.
     * <p>
     * Rounding to a whole amount happens last, after the scale is applied: rounding first would turn one
     * and a half buckets into two of them instead of 1500 mB. The result is finished, so no caller can
     * get that order wrong.
     *
     * @return zero when the field does not hold a usable positive number.
     */
    public static long parse(final String text, final int scale) {
        final double value = MathExpressionParser.parse(text);
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) {
            return 0;
        }

        final BigDecimal scaled = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(scale));
        return scaled.min(MAX_AMOUNT).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * The symbol to draw beside a field reading in {@code scale}, or empty when there is nothing to say.
     */
    public static String symbol(@Nullable final AEKey what, final int scale) {
        return scale > 1 && what != null ? what.getUnitSymbol() : "";
    }

    /**
     * Room a field has to give up so its symbol has somewhere to go. Includes the caret's width, which is
     * reserved whether or not it is blinking at that moment, so the symbol neither sits under it nor
     * jumps as it blinks.
     */
    public static int reservedWidth(final FontRenderer fr, final String symbol) {
        return symbol.isEmpty() ? 0 : fr.getStringWidth(symbol) + fr.getStringWidth("_") + SUFFIX_GAP;
    }

    /**
     * Draws a field's unit symbol immediately after the amount, following it as it is typed.
     */
    public static void drawSymbol(final FontRenderer fr, final GuiTextField field, final String symbol) {
        if (symbol.isEmpty()) {
            return;
        }

        final int x = field.x + drawnWidth(fr, field) + fr.getStringWidth("_") + SUFFIX_GAP;
        fr.drawString(symbol, x, field.y, SUFFIX_COLOR);
    }

    /**
     * How wide a field's contents are on screen. Not the width of the whole text: once it outgrows the
     * box the field scrolls, and deleting from the end shrinks what is drawn without scrolling back.
     */
    private static int drawnWidth(final FontRenderer fr, final GuiTextField field) {
        final String text = field.getText();
        int offset = 0;

        if (LINE_SCROLL_OFFSET != null) {
            try {
                offset = LINE_SCROLL_OFFSET.getInt(field);
            } catch (final IllegalAccessException e) {
                AELog.debug(e);
            }
        }

        offset = Math.max(0, Math.min(offset, text.length()));
        return fr.getStringWidth(fr.trimStringToWidth(text.substring(offset), field.getWidth()));
    }
}
