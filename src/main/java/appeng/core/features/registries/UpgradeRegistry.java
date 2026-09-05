/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.core.features.registries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.google.common.math.IntMath;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.upgrades.CardTrait;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeInventoryListener;
import appeng.api.upgrades.IUpgradeRegistry;
import appeng.items.contents.ItemUpgradeInventory;
import appeng.parts.automation.StackUpgradeInventory;

public final class UpgradeRegistry implements IUpgradeRegistry {

    private final Map<StackKey, CardRegistration> cards = new LinkedHashMap<>();
    private final Map<StackKey, Map<StackKey, Integer>> associations = new LinkedHashMap<>();
    private final Map<CardTrait, Map<StackKey, Integer>> traitSupport = new LinkedHashMap<>();
    private final Map<CardTrait, Map<StackKey, Integer>> traitLimits = new LinkedHashMap<>();

    @Override
    public synchronized void add(final ItemStack upgradeCard, final ItemStack upgradableObject,
            final int maxSupported) {
        requirePositive(maxSupported, "maxSupported");
        final StackKey card = StackKey.of(upgradeCard, "upgradeCard");
        final StackKey host = StackKey.of(upgradableObject, "upgradableObject");
        this.cards.computeIfAbsent(card, ignored -> new CardRegistration());
        putConsistent(this.associations.computeIfAbsent(card, ignored -> new LinkedHashMap<>()), host,
                maxSupported, "upgrade association");
    }

    @Override
    public synchronized void registerCard(final ItemStack upgradeCard, final CardTrait trait,
            final int points, final boolean inheritSupport) {
        requirePositive(points, "points");
        Objects.requireNonNull(trait, "trait");
        final StackKey card = StackKey.of(upgradeCard, "upgradeCard");
        this.cards.computeIfAbsent(card, ignored -> new CardRegistration())
                .set(trait, points, inheritSupport);
    }

    @Override
    public synchronized void addTraitSupport(final CardTrait trait, final ItemStack upgradableObject,
            final int maxSupported) {
        requirePositive(maxSupported, "maxSupported");
        Objects.requireNonNull(trait, "trait");
        putConsistent(this.traitSupport.computeIfAbsent(trait, ignored -> new LinkedHashMap<>()),
                StackKey.of(upgradableObject, "upgradableObject"), maxSupported, "trait support");
    }

    @Override
    public synchronized void setTraitLimit(final CardTrait trait, final ItemStack upgradableObject,
            final int maxPoints) {
        requirePositive(maxPoints, "maxPoints");
        Objects.requireNonNull(trait, "trait");
        putConsistent(this.traitLimits.computeIfAbsent(trait, ignored -> new LinkedHashMap<>()),
                StackKey.of(upgradableObject, "upgradableObject"), maxPoints, "trait limit");
    }

    @Override
    public synchronized int getMaxInstallable(final ItemStack upgradeCard, final ItemStack upgradableObject) {
        if (upgradeCard.isEmpty() || upgradableObject.isEmpty()) {
            return 0;
        }

        final StackKey card = StackKey.of(upgradeCard);
        final StackKey host = StackKey.of(upgradableObject);
        int result = getOrZero(this.associations.get(card), host);

        final CardRegistration registration = this.cards.get(card);
        if (registration != null) {
            for (final Map.Entry<CardTrait, TraitPoints> entry : registration.traits.entrySet()) {
                if (entry.getValue().inheritSupport) {
                    result = Math.max(result, getOrZero(this.traitSupport.get(entry.getKey()), host));
                }
            }
        }

        return result;
    }

    @Override
    public synchronized int getPoints(final ItemStack upgradeCard, final CardTrait trait) {
        final CardRegistration registration = this.getRegistration(upgradeCard);
        if (registration == null) {
            return 0;
        }

        final TraitPoints points = registration.traits.get(trait);
        return points == null ? 0 : points.points;
    }

    @Override
    public synchronized Map<CardTrait, Integer> getTraits(final ItemStack upgradeCard) {
        final Map<CardTrait, Integer> result = new LinkedHashMap<>();
        final CardRegistration registration = this.getRegistration(upgradeCard);
        if (registration != null) {
            registration.traits.forEach((trait, points) -> result.put(trait, points.points));
        }
        return result;
    }

    @Override
    public synchronized boolean isTraitSupported(final ItemStack upgradeCard, final CardTrait trait,
            final ItemStack upgradableObject) {
        if (upgradableObject.isEmpty()) {
            return false;
        }

        final CardRegistration registration = this.getRegistration(upgradeCard);
        if (registration == null) {
            return false;
        }

        final TraitPoints points = registration.traits.get(trait);
        if (points == null) {
            return false;
        }

        final StackKey host = StackKey.of(upgradableObject);
        return getOrZero(this.associations.get(StackKey.of(upgradeCard)), host) > 0
                || points.inheritSupport && getOrZero(this.traitSupport.get(trait), host) > 0;
    }

    @Override
    public synchronized int getTraitLimit(final CardTrait trait, final ItemStack upgradableObject) {
        if (upgradableObject.isEmpty()) {
            return 0;
        }

        return getOrZero(this.traitLimits.get(trait), StackKey.of(upgradableObject));
    }

    @Override
    public synchronized int getInstalledPoints(final IItemHandler installed,
            final ItemStack upgradableObject, final CardTrait trait) {
        return this.getInstalledPoints(installed, upgradableObject, trait, -1);
    }

    @Override
    public synchronized int getInstalledPoints(final IItemHandler installed,
            final ItemStack upgradableObject, final CardTrait trait, final int ignoredSlot) {
        int points = 0;

        for (int slot = 0; installed != null && slot < installed.getSlots(); slot++) {
            if (slot == ignoredSlot) {
                continue;
            }

            final ItemStack card = installed.getStackInSlot(slot);
            if (card.isEmpty() || !this.isTraitSupported(card, trait, upgradableObject)) {
                continue;
            }

            points = IntMath.saturatedAdd(points,
                    IntMath.saturatedMultiply(this.getPoints(card, trait), card.getCount()));
        }

        final int limit = this.getTraitLimit(trait, upgradableObject);
        return limit > 0 ? Math.min(points, limit) : points;
    }

    @Override
    public synchronized boolean canInstall(final ItemStack upgradeCard, final ItemStack upgradableObject,
            final IItemHandler installed) {
        if (upgradeCard.isEmpty()) {
            return false;
        }

        if (countInstalled(installed, upgradeCard) >= this.getMaxInstallable(upgradeCard, upgradableObject)) {
            return false;
        }

        final CardRegistration registration = this.getRegistration(upgradeCard);
        if (registration == null) {
            return true;
        }

        boolean confersAnything = false;
        for (final CardTrait trait : registration.traits.keySet()) {
            if (!this.isTraitSupported(upgradeCard, trait, upgradableObject)) {
                continue;
            }

            confersAnything = true;
            final int limit = this.getTraitLimit(trait, upgradableObject);
            if (limit <= 0 || this.getInstalledPoints(installed, upgradableObject, trait) < limit) {
                return true;
            }
        }

        // Nothing it brings works here, so nothing it brings can be full either: the plain association is
        // what let it in, and that was already counted above.
        return !confersAnything;
    }

    @Override
    public synchronized boolean isUpgradeCard(final ItemStack stack) {
        return !stack.isEmpty() && this.cards.containsKey(StackKey.of(stack));
    }

    @Override
    public synchronized Map<ItemStack, Integer> getSupportedObjects(final ItemStack upgradeCard) {
        final Map<ItemStack, Integer> result = new LinkedHashMap<>();
        if (upgradeCard.isEmpty()) {
            return result;
        }

        final StackKey card = StackKey.of(upgradeCard);
        final Map<StackKey, Integer> exact = this.associations.get(card);
        if (exact != null) {
            exact.forEach((host, max) -> mergeMaximum(result, host.toStack(), max));
        }

        final CardRegistration registration = this.cards.get(card);
        if (registration != null) {
            for (final Map.Entry<CardTrait, TraitPoints> entry : registration.traits.entrySet()) {
                if (!entry.getValue().inheritSupport) {
                    continue;
                }

                final Map<StackKey, Integer> support = this.traitSupport.get(entry.getKey());
                if (support != null) {
                    support.forEach((host, max) -> mergeMaximum(result, host.toStack(), max));
                }
            }
        }

        return result;
    }

    @Override
    public IUpgradeInventory createMachineInventory(final ItemStack upgradableObject, final int slots,
            final IUpgradeInventoryListener listener) {
        return new StackUpgradeInventory(upgradableObject, null, slots, listener);
    }

    @Override
    public IUpgradeInventory createItemInventory(final ItemStack upgradableItem, final int slots,
            final IUpgradeInventoryListener listener) {
        return new ItemUpgradeInventory(upgradableItem, slots, listener);
    }

    private CardRegistration getRegistration(final ItemStack stack) {
        return stack.isEmpty() ? null : this.cards.get(StackKey.of(stack));
    }

    private static int countInstalled(final IItemHandler installed, final ItemStack upgradeCard) {
        int count = 0;

        for (int slot = 0; installed != null && slot < installed.getSlots(); slot++) {
            final ItemStack card = installed.getStackInSlot(slot);
            if (!card.isEmpty() && ItemStack.areItemsEqual(card, upgradeCard)) {
                count = IntMath.saturatedAdd(count, card.getCount());
            }
        }

        return count;
    }

    private static int getOrZero(final Map<StackKey, Integer> values, final StackKey key) {
        return values == null ? 0 : values.getOrDefault(key, 0);
    }

    private static <K> void putConsistent(final Map<K, Integer> values, final K key, final int value,
            final String description) {
        requireConsistent(values, key, value, description);
        values.putIfAbsent(key, value);
    }

    private static <K> void requireConsistent(final Map<K, Integer> values, final K key, final int value,
            final String description) {
        final Integer previous = values.get(key);
        if (previous != null && previous != value) {
            throw new IllegalArgumentException("Conflicting " + description + ": " + previous + " != " + value);
        }
    }

    private static void mergeMaximum(final Map<ItemStack, Integer> values, final ItemStack stack, final int value) {
        for (final Map.Entry<ItemStack, Integer> entry : values.entrySet()) {
            if (ItemStack.areItemsEqual(entry.getKey(), stack)) {
                entry.setValue(Math.max(entry.getValue(), value));
                return;
            }
        }
        values.put(stack, value);
    }

    private static void requirePositive(final int value, final String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static final class CardRegistration {
        private final Map<CardTrait, TraitPoints> traits = new LinkedHashMap<>();

        private void set(final CardTrait trait, final int points, final boolean inheritSupport) {
            final TraitPoints previous = this.traits.get(trait);
            if (previous != null && (previous.points != points || previous.inheritSupport != inheritSupport)) {
                throw new IllegalArgumentException("Conflicting registration of card trait " + trait);
            }
            this.traits.put(trait, new TraitPoints(points, inheritSupport));
        }
    }

    private static final class TraitPoints {
        private final int points;
        private final boolean inheritSupport;

        private TraitPoints(final int points, final boolean inheritSupport) {
            this.points = points;
            this.inheritSupport = inheritSupport;
        }
    }

    private static final class StackKey {
        private final Item item;
        private final int metadata;

        private StackKey(final Item item, final int metadata) {
            this.item = item;
            this.metadata = metadata;
        }

        private static StackKey of(final ItemStack stack) {
            return new StackKey(stack.getItem(), stack.getMetadata());
        }

        private static StackKey of(final ItemStack stack, final String name) {
            if (stack == null || stack.isEmpty()) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
            return of(stack);
        }

        private ItemStack toStack() {
            return new ItemStack(this.item, 1, this.metadata);
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StackKey)) {
                return false;
            }
            final StackKey other = (StackKey) obj;
            return this.item == other.item && this.metadata == other.metadata;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.item, this.metadata);
        }
    }
}
