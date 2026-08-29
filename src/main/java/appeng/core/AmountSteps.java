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

package appeng.core;


import com.google.common.math.LongMath;
import com.google.common.primitives.Longs;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;


/**
 * What the step buttons of an amount screen do, and by how much.
 * <p>
 * One set serves every screen that types an amount, rather than one set per screen: they are the same four
 * buttons wherever they are drawn. A modifier key names a group of four steps and the one operation they
 * all perform, so Ctrl can multiply where an unmodified press adds.
 */
public final class AmountSteps {

    /** Steps in a group, which is also how many columns every amount screen has room for. */
    public static final int COUNT = 4;

    /**
     * The modifier a group belongs to. A press consults them in this order and takes the first one held,
     * so a stray modifier on top of another never silently changes the step.
     */
    public enum Group {
        NORMAL("", "no modifier is"),
        SHIFT("Shift", "Shift is"),
        CTRL("Ctrl", "Ctrl is"),
        ALT("Alt", "Alt is");

        private final String key;
        private final String held;

        Group(final String key, final String held) {
            this.key = key;
            this.held = held;
        }

        /** The modifier's own name, which is not a word in any language and so is not translated. */
        public String modifier() {
            return this.key;
        }
    }

    public enum Mode {
        ADD,
        MULTIPLY
    }

    private static final int[][] DEFAULT_STEPS = {
            {1, 10, 100, 1000},
            {1, 16, 32, 64},
            {2, 3, 5, 10},
            {10, 100, 1000, 10000}
    };

    private static final Mode[] DEFAULT_MODES = {Mode.ADD, Mode.ADD, Mode.MULTIPLY, Mode.MULTIPLY};

    private static final int[][] STEPS = new int[Group.values().length][COUNT];
    private static final Mode[] MODES = new Mode[Group.values().length];

    static {
        for (final Group group : Group.values()) {
            System.arraycopy(DEFAULT_STEPS[group.ordinal()], 0, STEPS[group.ordinal()], 0, COUNT);
            MODES[group.ordinal()] = DEFAULT_MODES[group.ordinal()];
        }
    }

    private AmountSteps() {
    }

    public static int step(final Group group, final int index) {
        return STEPS[group.ordinal()][index];
    }

    public static Mode mode(final Group group) {
        return MODES[group.ordinal()];
    }

    public static int[] defaultSteps(final Group group) {
        return DEFAULT_STEPS[group.ordinal()].clone();
    }

    public static Mode defaultMode(final Group group) {
        return DEFAULT_MODES[group.ordinal()];
    }

    /**
     * Takes the whole table at once, because the screen that edits one group edits all of them, and puts it
     * in the config file. A step below one is refused rather than stored: it would leave a button that does
     * nothing, or one that empties the field it is meant to raise.
     */
    public static void save(final int[][] values, final Mode[] modes) {
        final AEConfig config = AEConfig.instance();

        for (final Group group : Group.values()) {
            final int g = group.ordinal();

            for (int i = 0; i < COUNT; i++) {
                STEPS[g][i] = Math.max(1, values[g][i]);
                config.get("Client", "amountStep" + group.key + (i + 1), DEFAULT_STEPS[g][i]).set(STEPS[g][i]);
            }

            MODES[g] = modes[g];
            config.get("Client", "amountStep" + group.key + "Mode", DEFAULT_MODES[g].name()).set(MODES[g].name());
        }

        config.save();
    }

    /**
     * One press, applied to the amount the screen is showing.
     * <p>
     * Nothing here wraps: an addition or a factor that would run off the end of a {@code long} stops at it,
     * and the result is then held to whatever the screen itself allows. A division truncates towards zero,
     * so a negative amount loses as much of itself as its positive twin would.
     *
     * @param step the group's step, already written in whatever unit {@code current} is read in
     * @param up   the upper row, which adds or multiplies; the lower one subtracts or divides
     */
    public static long apply(final long current, final long step, final Mode mode, final boolean up,
            final long min, final long max) {
        final long result;

        if (mode == Mode.ADD) {
            result = LongMath.saturatedAdd(current, up ? step : -step);
        } else if (up) {
            result = LongMath.saturatedMultiply(current, step);
        } else {
            result = step == 0 ? current : current / step;
        }

        return Longs.constrainToRange(result, min, max);
    }

    static void load(final AEConfig config) {
        for (final Group group : Group.values()) {
            for (int i = 0; i < COUNT; i++) {
                final int fallback = DEFAULT_STEPS[group.ordinal()][i];
                final Property step = config.get("Client", "amountStep" + group.key + (i + 1), fallback);

                step.setComment("Step button " + (i + 1) + " on an amount screen, while " + group.held + " held.");
                STEPS[group.ordinal()][i] = Math.max(1, Math.abs(step.getInt(fallback)));
            }

            final Mode fallback = DEFAULT_MODES[group.ordinal()];
            final Property mode = config.get("Client", "amountStep" + group.key + "Mode", fallback.name());

            mode.setComment("What those four buttons do with their steps: ADD or MULTIPLY.");
            MODES[group.ordinal()] = parse(mode.getString(), fallback);
        }

        dropOldKeys(config);
    }

    private static Mode parse(final String name, final Mode fallback) {
        for (final Mode mode : Mode.values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return fallback;
    }

    /**
     * The keys the three separate per-screen sets used to live under. Nothing reads them any more, and a
     * key that is written back to the file every save but governs nothing is worse than no key at all.
     */
    private static void dropOldKeys(final AEConfig config) {
        final ConfigCategory client = config.getCategory("Client");

        for (int i = 1; i <= COUNT; i++) {
            client.remove("craftAmtButton" + i);
            client.remove("priorityAmtButton" + i);
            client.remove("levelAmtButton" + i);
        }
    }
}
