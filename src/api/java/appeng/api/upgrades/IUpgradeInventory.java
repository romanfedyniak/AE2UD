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
     * Returns the points of a trait the installed cards supply, capped by the host's limit for it.
     */
    int getInstalledPoints(CardTrait trait);

    /**
     * Whether any installed card carries this trait at all.
     */
    default boolean isInstalled(final CardTrait trait) {
        return this.getInstalledPoints(trait) > 0;
    }

    /**
     * Whether one more of this card would do the host any good. The single place the rule lives; the
     * inventory's own filter asks nothing else.
     */
    boolean canInstall(ItemStack upgradeCard);

    void readFromNBT(NBTTagCompound data, String name);

    void writeToNBT(NBTTagCompound data, String name);
}
