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

package appeng.container.implementations;


import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotInaccessible;
import appeng.tile.inventory.AppEngInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;


/**
 * Holds nothing but the picture the craft priority screen shows. That screen is summoned over the one that
 * was open rather than opened in its place, so no container of its own ever reaches a server, and the
 * number it types goes to the container underneath.
 */
public class ContainerCraftPriority extends AEBaseContainer {

    public ContainerCraftPriority(final InventoryPlayer ip, final ItemStack icon) {
        super(ip, null, null);

        final AppEngInternalInventory display = new AppEngInternalInventory(null, 1);
        display.setStackInSlot(0, icon);
        this.addSlotToContainer(new SlotInaccessible(display, 0, 34, 53));
    }

    @Override
    public boolean canInteractWith(final EntityPlayer player) {
        return true;
    }
}
