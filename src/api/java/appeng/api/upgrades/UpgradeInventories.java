/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.upgrades;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;

/**
 * Factory methods for upgrade inventories used by addon machines and items.
 */
public final class UpgradeInventories {

    private UpgradeInventories() {
    }

    public static IUpgradeInventory forMachine(final ItemStack upgradableObject, final int slots,
            final IUpgradeInventoryListener listener) {
        return AEApi.instance().registries().upgrades()
                .createMachineInventory(upgradableObject, slots, listener);
    }

    public static IUpgradeInventory forItem(final ItemStack upgradableItem, final int slots,
            final IUpgradeInventoryListener listener) {
        return AEApi.instance().registries().upgrades()
                .createItemInventory(upgradableItem, slots, listener);
    }
}
