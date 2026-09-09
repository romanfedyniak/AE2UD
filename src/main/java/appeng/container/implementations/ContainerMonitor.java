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


import appeng.api.config.SecurityPermissions;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotFakeTypeOnly;
import appeng.helpers.InventoryAction;
import appeng.parts.reporting.AbstractPartMonitor;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;


/**
 * The window every monitor opens on a sneaking click.
 * <p>
 * What a monitor watches used to be set by holding the thing and clicking, and locked by clicking with an
 * empty hand - one gesture per setting, and no gesture left for a third. Both live here now, alongside
 * whatever else the particular monitor has to offer, and the clicks in the world still work as they did.
 */
public class ContainerMonitor extends AEBaseContainer {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 158;

    /** Where the watched key sits, which is also where the screen draws its well. */
    public static final int CONFIG_X = 80;
    public static final int CONFIG_Y = 26;

    private final AbstractPartMonitor monitor;

    private final int configSlot;

    @GuiSync(0)
    public boolean locked;

    public ContainerMonitor(final InventoryPlayer ip, final AbstractPartMonitor monitor) {
        super(ip, null, monitor);

        this.monitor = monitor;

        this.configSlot = this.inventorySlots.size();
        this.addSlotToContainer(new SlotFakeTypeOnly(monitor.getConfigInventory(), 0, CONFIG_X, CONFIG_Y));

        this.bindPlayerInventory(ip, 0, HEIGHT - 82);
    }

    /**
     * A locked monitor keeps the key it has. Refused here rather than in the slot: a slot that says it is
     * disabled is not drawn at all, and a locked monitor must still show what it is watching.
     */
    @Override
    public void doAction(final EntityPlayerMP player, final InventoryAction action, final int slot, final long id) {
        if (slot == this.configSlot && this.monitor.isLocked()) {
            return;
        }

        super.doAction(player, action, slot, id);
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            this.locked = this.monitor.isLocked();
        }

        this.verifyPermissions(SecurityPermissions.BUILD, false);

        super.detectAndSendChanges();
    }

    public boolean isLocked() {
        return this.locked;
    }

    public void toggleLock() {
        this.monitor.setLocked(!this.monitor.isLocked());
    }
}
