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

import appeng.container.me.GridInventoryEntry;
import appeng.container.implementations.TerminalCraftingPin;

public final class InternalPinSlotME extends InternalSlotME {
    private final ItemRepo pinRepo;
    private final int pinIndex;
    private final boolean crafting;

    public InternalPinSlotME(ItemRepo repo, int pinIndex, boolean crafting, int x, int y) {
        super(repo, 0, x, y);
        this.pinRepo = repo;
        this.pinIndex = pinIndex;
        this.crafting = crafting;
    }

    @Override
    GridInventoryEntry getEntry() {
        return crafting ? pinRepo.getCraftingPinEntry(pinIndex) : pinRepo.getPlayerPinEntry(pinIndex);
    }

    @Override
    public SlotME createSlot() {
        return new PinSlotME(this, pinIndex, crafting);
    }

    TerminalCraftingPin getCraftingStatus() {
        return crafting ? pinRepo.getCraftingPinStatus(pinIndex) : null;
    }
}
