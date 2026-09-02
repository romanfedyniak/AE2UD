/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.container.implementations;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotFake;

/**
 * The read-only view of one storage cell, opened over whatever screen the player was already in.
 * <p>
 * It never reaches a server, for the same reason {@link ContainerPatternView} does not: a cell carries its
 * whole contents in its own NBT, and the stack under the cursor is one the client already has. The
 * container exists so the screen can hang the mod's ordinary ME slots off something - the rows are drawn,
 * measured and given tooltips by machinery that expects a container - and for nothing else.
 */
public class ContainerCellView extends AEBaseContainer {

    public ContainerCellView(final InventoryPlayer ip, final ItemStack cell, final int x, final int y) {
        super(ip, null, null);

        // The cell itself, in a slot, is the whole heading: it names itself and carries its own tooltip -
        // bytes, types, filter and cards - which no line of text in the window could fit without running
        // into something.
        final ItemStackHandler shown = new ItemStackHandler(1);
        shown.setStackInSlot(0, cell);
        this.addSlotToContainer(new SlotFake(shown, 0, x, y));
    }

    /**
     * Nothing was ever opened on the server, so nothing may be closed there either - the screen this one was
     * summoned over is still the player's real container and has to be left exactly as it was found.
     */
    @Override
    public void onContainerClosed(final EntityPlayer player) {
    }

    @Override
    public boolean canInteractWith(final EntityPlayer player) {
        return true;
    }
}
