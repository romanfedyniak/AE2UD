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

package appeng.container.implementations;


import java.util.Map;

import appeng.api.config.SecurityPermissions;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.helpers.ISubMenuHost;
import appeng.util.Platform;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;


/**
 * Backs the screen that turns a host's key types on and off.
 * <p>
 * The whole selection travels as one string, encoded by {@link KeyTypeSelection#encode}, because the client
 * has no way to know which types this particular host allows - only which exist.
 *
 * @see appeng.client.gui.implementations.GuiKeyTypeSelection
 */
public class ContainerKeyTypeSelection extends AEBaseContainer {

    private final KeyTypeSelectionHost host;

    @GuiSync(0)
    public String selection = "";

    public ContainerKeyTypeSelection(final InventoryPlayer ip, final KeyTypeSelectionHost te) {
        super(ip, (TileEntity) (te instanceof TileEntity ? te : null), (IPart) (te instanceof IPart ? te : null),
                (IGuiItemObject) (te instanceof IGuiItemObject ? te : null));
        this.host = te;
    }

    public ISubMenuHost getSubMenuHost() {
        return (ISubMenuHost) this.host;
    }

    /**
     * What the screen draws: every type this host allows, in registration order, mapped to whether it is on.
     */
    public Map<AEKeyType, Boolean> getSelection() {
        return KeyTypeSelection.decode(this.selection);
    }

    public void toggle(final ResourceLocation id) {
        if (this.host.requiresBuildPermissionForKeyTypeSelection()
                && Platform.isServer()
                && !this.hasAccess(SecurityPermissions.BUILD, false)) {
            return;
        }

        final AEKeyType type = AEKeyTypes.get(id);
        if (type == null) {
            return;
        }

        final KeyTypeSelection current = this.host.getKeyTypeSelection();
        if (!current.enabled().containsKey(type)) {
            return;
        }

        current.setEnabled(type, !current.isEnabled(type));
        this.detectAndSendChanges();
    }

    @Override
    public void detectAndSendChanges() {
        if (this.host.requiresBuildPermissionForKeyTypeSelection()) {
            this.verifyPermissions(SecurityPermissions.BUILD, false);
        }

        if (Platform.isServer()) {
            this.selection = KeyTypeSelection.encode(this.host.getKeyTypeSelection().enabled());
        }

        super.detectAndSendChanges();
    }
}
