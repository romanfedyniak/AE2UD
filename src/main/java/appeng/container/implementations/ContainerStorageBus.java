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


import appeng.api.config.*;
import appeng.api.stacks.AEKey;
import appeng.container.guisync.GuiSync;
import appeng.me.storage.MEInventoryHandler;
import appeng.parts.misc.PartStorageBus;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.iterators.NullIterator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.Iterator;


public class ContainerStorageBus extends ContainerUpgradeable {

    private final PartStorageBus storageBus;

    @GuiSync(3)
    public AccessRestriction rwMode = AccessRestriction.READ_WRITE;

    @GuiSync(4)
    public StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;

    @GuiSync(7)
    public YesNo stickyMode = YesNo.NO;

    public ContainerStorageBus(final InventoryPlayer ip, final PartStorageBus te) {
        super(ip, te);
        this.storageBus = te;
    }

    @Override
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void setupConfig() {
        this.setupExpandableConfig(2, 9, 5);
        this.setupUpgrades();
    }

    @Override
    public int availableUpgrades() {
        return 5;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            this.setFuzzyMode((FuzzyMode) this.getUpgradeable().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setReadWriteMode((AccessRestriction) this.getUpgradeable().getConfigManager().getSetting(Settings.ACCESS));
            this.setStorageFilter((StorageFilter) this.getUpgradeable().getConfigManager().getSetting(Settings.STORAGE_FILTER));
            this.setStickyMode((YesNo) this.getUpgradeable().getConfigManager().getSetting(Settings.STICKY_MODE));
        }

        this.standardDetectAndSendChanges();
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        final int upgrades = this.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);

        return upgrades > idx;
    }

    public void partition() {
        final IItemHandler inv = this.getUpgradeable().getInventoryByName("config");

        final MEInventoryHandler cellInv = this.storageBus.getInternalHandler();

        Iterator<AEKey> i = new NullIterator<>();
        if (cellInv != null) {
            i = cellInv.getAvailableStacks().keySet().iterator();
        }

        for (int x = 0; x < inv.getSlots(); x++) {
            if (i.hasNext() && this.isSlotEnabled((x / 9) - 2)) {
                final ItemStack g = i.next().wrapForDisplayOrFilter();
                ItemHandlerUtil.setStackInSlot(inv, x, g);
            } else {
                ItemHandlerUtil.setStackInSlot(inv, x, ItemStack.EMPTY);
            }
        }

        this.detectAndSendChanges();
    }

    public AccessRestriction getReadWriteMode() {
        return this.rwMode;
    }

    private void setReadWriteMode(final AccessRestriction rwMode) {
        this.rwMode = rwMode;
    }

    public StorageFilter getStorageFilter() {
        return this.storageFilter;
    }

    private void setStorageFilter(final StorageFilter storageFilter) {
        this.storageFilter = storageFilter;
    }

    public YesNo getStickyMode() {
        return this.stickyMode;
    }

    private void setStickyMode(final YesNo stickyMode) {
        this.stickyMode = stickyMode;
    }
}
