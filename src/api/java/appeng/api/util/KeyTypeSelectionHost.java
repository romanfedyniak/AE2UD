/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.util;

/**
 * A machine that lets the player choose which key types it acts on.
 * <p>
 * Implementing this is what puts the type selection screen within reach of a machine; the screen reads and
 * writes the {@link KeyTypeSelection} this returns and needs nothing else from the host.
 * <p>
 * In this port an implementor must also be an {@code appeng.helpers.ISubMenuHost} so the screen knows where
 * to send the player back to. That half is not here because returning from a sub-screen is done with a
 * {@code GuiBridge}, which lives outside the API.
 */
public interface KeyTypeSelectionHost {

    KeyTypeSelection getKeyTypeSelection();

    /**
     * What the selection is for. It decides how the screen names the choice, and, by default, who is
     * allowed to change it.
     */
    Purpose getKeyTypeSelectionPurpose();

    /**
     * Whether changing this selection configures how a machine acts on the world. Display-only filters,
     * such as a terminal's visible key types, may be changed by anyone who can open the terminal.
     */
    default boolean requiresBuildPermissionForKeyTypeSelection() {
        return this.getKeyTypeSelectionPurpose() != Purpose.DISPLAY;
    }

    /**
     * The kinds of machine that let the player choose key types. Each reads its selection differently
     * enough to need words of its own, and the screen has nothing else to tell them apart by.
     */
    enum Purpose {
        /** A terminal, where the selection decides only what is shown. */
        DISPLAY,
        /** An import bus, where it decides what is moved into the network. */
        IMPORT,
        /** A storage bus, where it decides what is stored and what is reported to the network. */
        STORAGE,
        /** A formation plane, where it decides what it puts into the world. */
        PLACE,
        /** An annihilation plane, where it decides what it takes out of the world. */
        PICK_UP
    }
}
