/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.upgrades;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.definitions.IItemDefinition;

/**
 * Registers upgrade cards and the machines, parts and items that accept them.
 *
 * <p>A card is described by the {@link CardTrait}s it confers and by the hosts those traits work in. A
 * host that declares support for a trait takes it from any card carrying it, so one card can stand in for
 * several, and an addon's card works in AE2's machines without AE2 knowing about it.</p>
 *
 * <p>Cards and hosts are matched by item and metadata. NBT is deliberately ignored.</p>
 */
public interface IUpgradeRegistry {

    /**
     * Registers an exact card-to-host association: this card works in this host, whatever its traits.
     */
    void add(ItemStack upgradeCard, ItemStack upgradableObject, int maxSupported);

    default void add(final ItemStack upgradeCard, final IItemDefinition upgradableObject, final int maxSupported) {
        upgradableObject.maybeStack(1).ifPresent(stack -> this.add(upgradeCard, stack, maxSupported));
    }

    /**
     * Registers what a card confers. A card may be registered with as many traits as it carries.
     *
     * @param inheritSupport if true, the card works in every host registered through
     *                       {@link #addTraitSupport}; if false, only where {@link #add} named it
     */
    void registerCard(ItemStack upgradeCard, CardTrait trait, int points, boolean inheritSupport);

    default void registerCard(final ItemStack upgradeCard, final CardTrait trait, final int points) {
        this.registerCard(upgradeCard, trait, points, true);
    }

    /**
     * Registers a host as taking this trait from any card that carries it.
     */
    void addTraitSupport(CardTrait trait, ItemStack upgradableObject, int maxSupported);

    default void addTraitSupport(final CardTrait trait, final IItemDefinition upgradableObject,
            final int maxSupported) {
        upgradableObject.maybeStack(1).ifPresent(stack -> this.addTraitSupport(trait, stack, maxSupported));
    }

    /**
     * Caps how many points of a trait a host can end up with, however many points its cards carry.
     * Without one the host takes whatever is installed.
     */
    void setTraitLimit(CardTrait trait, ItemStack upgradableObject, int maxPoints);

    default void setTraitLimit(final CardTrait trait, final IItemDefinition upgradableObject,
            final int maxPoints) {
        upgradableObject.maybeStack(1).ifPresent(stack -> this.setTraitLimit(trait, stack, maxPoints));
    }

    int getMaxInstallable(ItemStack upgradeCard, ItemStack upgradableObject);

    /**
     * The points of a trait one card carries, regardless of where it is installed.
     */
    int getPoints(ItemStack upgradeCard, CardTrait trait);

    /**
     * Everything a card confers, in the order it was registered.
     */
    Map<CardTrait, Integer> getTraits(ItemStack upgradeCard);

    /**
     * Returns whether this card's trait works in this host.
     */
    boolean isTraitSupported(ItemStack upgradeCard, CardTrait trait, ItemStack upgradableObject);

    /**
     * @return the host's cap on this trait, or 0 when it has none
     */
    int getTraitLimit(CardTrait trait, ItemStack upgradableObject);

    /**
     * The points a host ends up with, counting only cards whose trait it declared, capped by
     * {@link #setTraitLimit}.
     *
     * <p>Takes a plain handler rather than an {@link IUpgradeInventory} so that a machine or cell holding
     * its cards in an inventory of its own still gets the same answer.</p>
     */
    int getInstalledPoints(IItemHandler installed, ItemStack upgradableObject, CardTrait trait);

    /**
     * Whether one more of this card would do the host any good: it is not already at its own count, and at
     * least one trait it brings here still has room. A card the host takes for no trait at all - one named
     * by {@link #add} alone - is judged on the count only.
     */
    boolean canInstall(ItemStack upgradeCard, ItemStack upgradableObject, IItemHandler installed);

    boolean isUpgradeCard(ItemStack stack);

    /**
     * Returns the effective host associations for a card, including the hosts it inherits per trait.
     */
    Map<ItemStack, Integer> getSupportedObjects(ItemStack upgradeCard);

    IUpgradeInventory createMachineInventory(ItemStack upgradableObject, int slots,
            IUpgradeInventoryListener listener);

    IUpgradeInventory createItemInventory(ItemStack upgradableItem, int slots,
            IUpgradeInventoryListener listener);
}
