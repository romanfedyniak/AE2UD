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


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.config.SecurityPermissions;
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
 * The whole selection travels as one string - {@code namespace:type=1,namespace:other=0} - rather than as
 * a bitmask over registry ids. Two reasons: an id is a position in a registry, so a client that disagreed
 * about the order would toggle the wrong type, and the client has no way to know *which* types this
 * particular host allows, only which exist. Both facts have to come from the server.
 *
 * @see appeng.client.gui.implementations.GuiKeyTypeSelection
 */
public class ContainerKeyTypeSelection extends AEBaseContainer {

    private static final String SEPARATOR = ",";
    private static final String ASSIGN = "=";

    private final KeyTypeSelectionHost host;

    @GuiSync(0)
    public String selection = "";

    public ContainerKeyTypeSelection(final InventoryPlayer ip, final KeyTypeSelectionHost te) {
        super(ip, (TileEntity) (te instanceof TileEntity ? te : null), (IPart) (te instanceof IPart ? te : null));
        this.host = te;
    }

    public ISubMenuHost getSubMenuHost() {
        return (ISubMenuHost) this.host;
    }

    /**
     * What the screen draws: every type this host allows, in registration order, mapped to whether it is on.
     */
    public Map<AEKeyType, Boolean> getSelection() {
        final Map<AEKeyType, Boolean> out = new LinkedHashMap<>();

        for (final String entry : this.selection.split(SEPARATOR)) {
            final int split = entry.indexOf(ASSIGN);
            if (split <= 0) {
                continue;
            }

            final AEKeyType type = AEKeyTypes.get(new ResourceLocation(entry.substring(0, split)));
            if (type != null) {
                out.put(type, "1".equals(entry.substring(split + 1)));
            }
        }

        return out;
    }

    public void toggle(final ResourceLocation id) {
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
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            final List<String> entries = new ArrayList<>();
            for (final Map.Entry<AEKeyType, Boolean> entry : this.host.getKeyTypeSelection().enabled().entrySet()) {
                entries.add(entry.getKey().getRegistryName() + ASSIGN + (entry.getValue() ? "1" : "0"));
            }
            this.selection = String.join(SEPARATOR, entries);
        }

        super.detectAndSendChanges();
    }
}
