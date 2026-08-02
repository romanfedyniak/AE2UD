/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.me;

import appeng.container.implementations.TerminalCraftingPin;

public final class PinSlotME extends SlotME {
    private final InternalPinSlotME internal;
    private final int pinIndex;
    private final boolean crafting;

    PinSlotME(InternalPinSlotME slot, int pinIndex, boolean crafting) {
        super(slot);
        this.internal = slot;
        this.pinIndex = pinIndex;
        this.crafting = crafting;
    }

    public int getPinIndex() {
        return pinIndex;
    }

    public boolean isCraftingPin() {
        return crafting;
    }

    public TerminalCraftingPin getCraftingStatus() {
        return internal.getCraftingStatus();
    }
}
