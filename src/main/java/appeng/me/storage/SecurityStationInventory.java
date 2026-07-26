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

package appeng.me.storage;


import com.mojang.authlib.GameProfile;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.implementations.items.IBiometricCard;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.GridAccessException;
import appeng.tile.misc.TileSecurityStation;


/**
 * Holds the biometric cards registered with a security station.
 * <p/>
 * Replaces the old {@code IMEInventoryHandler<IAEItemStack>} implementation. It no longer posts changes to
 * listeners itself: that bookkeeping belonged to {@code IMEMonitor}, which is gone. The network's storage service
 * now periodically diffs its own cached amounts, so a plain {@link MEStorage} mounted through
 * {@code IStorageProvider} is all that's needed.
 * <p/>
 * There is no reference implementation for this class: AE2-original dropped the security station entirely, so
 * this is a straight port of the pre-migration logic onto the new key/storage types.
 */
public class SecurityStationInventory implements MEStorage {

    private final KeyCounter storedItems = new KeyCounter();
    private final TileSecurityStation securityTile;

    public SecurityStationInventory(final TileSecurityStation ts) {
        this.securityTile = ts;
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource src) {
        if (!(what instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }

        if (!this.hasPermission(src)) {
            return 0;
        }

        if (!AEApi.instance().definitions().items().biometricCard().isSameAs(itemKey.toStack())) {
            return 0;
        }

        if (!this.canAccept(itemKey)) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            this.storedItems.add(itemKey, 1);
            this.securityTile.inventoryChanged();
        }

        return 1;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource src) {
        if (!(what instanceof AEItemKey) || amount <= 0 || !this.hasPermission(src)) {
            return 0;
        }

        if (this.storedItems.get(what) <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            this.storedItems.remove(what);
            this.securityTile.inventoryChanged();
        }

        return 1;
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        out.addAll(this.storedItems);
    }

    @Override
    public ITextComponent getDescription() {
        return new TextComponentString("Security Station");
    }

    public KeyCounter getStoredItems() {
        return this.storedItems;
    }

    private boolean hasPermission(final IActionSource src) {
        if (src.player().isPresent()) {
            try {
                return this.securityTile.getProxy().getSecurity().hasPermission(src.player().get(), SecurityPermissions.SECURITY);
            } catch (final GridAccessException e) {
                // :P
            }
        }
        return false;
    }

    private boolean canAccept(final AEItemKey input) {
        if (!(input.getItem() instanceof IBiometricCard tbc)) {
            return false;
        }

        final GameProfile newUser = tbc.getProfile(input.toStack());
        final int playerId = AEApi.instance().registries().players().getID(newUser);
        if (this.securityTile.getOwner() == playerId) {
            return false;
        }

        for (final Object2LongMap.Entry<AEKey> entry : this.storedItems) {
            if (!(entry.getKey() instanceof AEItemKey existing)) {
                continue;
            }
            if (!(existing.getItem() instanceof IBiometricCard existingCard)) {
                continue;
            }

            final GameProfile thisUser = existingCard.getProfile(existing.toStack());
            if (thisUser != null && thisUser.equals(newUser)) {
                return false;
            }
        }

        return true;
    }
}
