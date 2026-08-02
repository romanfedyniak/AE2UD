/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.upgrades;

/**
 * Notified after the contents of an upgrade inventory change.
 */
@FunctionalInterface
public interface IUpgradeInventoryListener {

    void onUpgradesChanged(IUpgradeInventory inventory);
}
