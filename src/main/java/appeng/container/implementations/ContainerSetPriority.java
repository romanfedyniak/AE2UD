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
import appeng.helpers.IPriorityHost;
import appeng.helpers.PriorityAmountTarget;
import appeng.util.Platform;
import net.minecraft.entity.player.InventoryPlayer;


/**
 * The amount screen, opened on a machine's priority. There is no screen of its own for a priority any more:
 * it is one number, and typing one number is what this screen is.
 */
public class ContainerSetPriority extends ContainerSetAmount {

    public ContainerSetPriority(final InventoryPlayer ip, final IPriorityHost host) {
        super(ip, host);

        if (Platform.isServer()) {
            this.setAmountTarget(new PriorityAmountTarget(host));
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.verifyPermissions(SecurityPermissions.BUILD, false);
    }
}
