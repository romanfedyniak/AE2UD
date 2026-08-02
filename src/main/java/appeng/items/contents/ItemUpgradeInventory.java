/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.items.contents;

import net.minecraft.item.ItemStack;

import appeng.api.upgrades.IUpgradeInventoryListener;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.util.Platform;

public final class ItemUpgradeInventory extends StackUpgradeInventory {

    private final ItemStack item;

    public ItemUpgradeInventory(final ItemStack item, final int slots, final IUpgradeInventoryListener listener) {
        super(item, null, slots, listener);
        this.item = item;
        this.readFromNBT(Platform.openNbtData(item), "upgrades");
    }

    @Override
    protected void onContentsChanged(final int slot) {
        this.writeToNBT(Platform.openNbtData(this.item), "upgrades");
        super.onContentsChanged(slot);
    }
}
