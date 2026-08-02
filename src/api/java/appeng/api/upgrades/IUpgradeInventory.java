/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.upgrades;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

/**
 * An inventory that accepts upgrade cards registered for its host item.
 */
public interface IUpgradeInventory extends IItemHandler {

    /**
     * The item representation of the machine, part or item being upgraded.
     */
    ItemStack getUpgradableItem();

    /**
     * Returns the number of physically installed cards matching {@code upgradeCard}.
     */
    int getInstalledUpgrades(ItemStack upgradeCard);

    /**
     * Returns the maximum number of matching cards that can be installed.
     */
    int getMaxInstalled(ItemStack upgradeCard);

    /**
     * Returns the saturated sum of speed points supplied by installed cards.
     */
    int getInstalledSpeedPoints();

    /**
     * Returns the sum of capacity points, capped to the host's registered capacity limit.
     */
    int getInstalledCapacityPoints();

    void readFromNBT(NBTTagCompound data, String name);

    void writeToNBT(NBTTagCompound data, String name);
}
