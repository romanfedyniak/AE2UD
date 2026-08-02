/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.upgrades;

import java.util.Map;

import net.minecraft.item.ItemStack;

import appeng.api.definitions.IItemDefinition;

/**
 * Registers upgrade cards and the machines, parts and items that accept them.
 *
 * <p>Cards and hosts are matched by item and metadata. NBT is deliberately ignored.</p>
 */
public interface IUpgradeRegistry {

    /**
     * Registers an exact card-to-host association.
     */
    void add(ItemStack upgradeCard, ItemStack upgradableObject, int maxSupported);

    default void add(final ItemStack upgradeCard, final IItemDefinition upgradableObject, final int maxSupported) {
        upgradableObject.maybeStack(1).ifPresent(stack -> this.add(upgradeCard, stack, maxSupported));
    }

    /**
     * Registers a card that contributes speed points and inherits the standard speed-compatible hosts.
     */
    default void registerSpeedCard(final ItemStack upgradeCard, final int speedPoints) {
        this.registerSpeedCard(upgradeCard, speedPoints, true);
    }

    /**
     * Registers a card that contributes speed points.
     *
     * @param inheritStandardSupport if true, the card works in every host registered through
     *                               {@link #addSpeedCardSupport(ItemStack, int)}
     */
    void registerSpeedCard(ItemStack upgradeCard, int speedPoints, boolean inheritStandardSupport);

    /**
     * Registers a host as supporting speed cards that inherit standard compatibility.
     */
    void addSpeedCardSupport(ItemStack upgradableObject, int maxSupported);

    default void addSpeedCardSupport(final IItemDefinition upgradableObject, final int maxSupported) {
        upgradableObject.maybeStack(1).ifPresent(stack -> this.addSpeedCardSupport(stack, maxSupported));
    }

    /**
     * Registers a card that contributes capacity points and inherits the standard capacity-compatible hosts.
     */
    default void registerCapacityCard(final ItemStack upgradeCard, final int capacityPoints) {
        this.registerCapacityCard(upgradeCard, capacityPoints, true);
    }

    /**
     * Registers a card that contributes capacity points.
     */
    void registerCapacityCard(ItemStack upgradeCard, int capacityPoints, boolean inheritStandardSupport);

    /**
     * Registers a host as supporting capacity cards. Capacity points are capped at {@code maxCapacityPoints}.
     */
    void addCapacityCardSupport(ItemStack upgradableObject, int maxSupported, int maxCapacityPoints);

    default void addCapacityCardSupport(final IItemDefinition upgradableObject, final int maxSupported,
            final int maxCapacityPoints) {
        upgradableObject.maybeStack(1)
                .ifPresent(stack -> this.addCapacityCardSupport(stack, maxSupported, maxCapacityPoints));
    }

    /**
     * Sets the capacity-point limit without making the host inherit standard capacity-card support.
     */
    void setCapacityLimit(ItemStack upgradableObject, int maxCapacityPoints);

    default void setCapacityLimit(final IItemDefinition upgradableObject, final int maxCapacityPoints) {
        upgradableObject.maybeStack(1).ifPresent(stack -> this.setCapacityLimit(stack, maxCapacityPoints));
    }

    int getMaxInstallable(ItemStack upgradeCard, ItemStack upgradableObject);

    int getSpeedPoints(ItemStack upgradeCard);

    /**
     * Returns whether this card contributes speed points to this host.
     */
    boolean isSpeedCardSupported(ItemStack upgradeCard, ItemStack upgradableObject);

    int getCapacityPoints(ItemStack upgradeCard);

    /**
     * Returns whether this card contributes capacity points to this host.
     */
    boolean isCapacityCardSupported(ItemStack upgradeCard, ItemStack upgradableObject);

    int getCapacityLimit(ItemStack upgradableObject);

    boolean isUpgradeCard(ItemStack stack);

    /**
     * Returns the effective host associations for a card, including inherited speed/capacity support.
     */
    Map<ItemStack, Integer> getSupportedObjects(ItemStack upgradeCard);

    IUpgradeInventory createMachineInventory(ItemStack upgradableObject, int slots,
            IUpgradeInventoryListener listener);

    IUpgradeInventory createItemInventory(ItemStack upgradableItem, int slots,
            IUpgradeInventoryListener listener);
}
