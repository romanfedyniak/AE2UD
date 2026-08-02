/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.storage;

import javax.annotation.Nullable;

import appeng.api.stacks.AEKey;

/** Mutable pin settings for one player on one terminal host. */
public interface IPlayerTerminalPins {

    int MAX_ROWS = 16;
    int SLOTS_PER_ROW = 9;
    int MAX_PINS = MAX_ROWS * SLOTS_PER_ROW;

    int getCraftingRows();

    void setCraftingRows(int rows);

    int getPlayerRows();

    void setPlayerRows(int rows);

    @Nullable
    AEKey getPin(int slot);

    void setPin(int slot, @Nullable AEKey key);
}
