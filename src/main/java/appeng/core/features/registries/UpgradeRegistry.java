/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.core.features.registries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeInventoryListener;
import appeng.api.upgrades.IUpgradeRegistry;
import appeng.items.contents.ItemUpgradeInventory;
import appeng.parts.automation.StackUpgradeInventory;

public final class UpgradeRegistry implements IUpgradeRegistry {

    private final Map<StackKey, CardTraits> cards = new LinkedHashMap<>();
    private final Map<StackKey, Map<StackKey, Integer>> associations = new LinkedHashMap<>();
    private final Map<StackKey, Integer> speedSupport = new LinkedHashMap<>();
    private final Map<StackKey, CapacitySupport> capacitySupport = new LinkedHashMap<>();
    private final Map<StackKey, Integer> capacityLimits = new LinkedHashMap<>();

    @Override
    public synchronized void add(final ItemStack upgradeCard, final ItemStack upgradableObject,
            final int maxSupported) {
        requirePositive(maxSupported, "maxSupported");
        final StackKey card = StackKey.of(upgradeCard, "upgradeCard");
        final StackKey host = StackKey.of(upgradableObject, "upgradableObject");
        this.cards.computeIfAbsent(card, ignored -> new CardTraits());
        putConsistent(this.associations.computeIfAbsent(card, ignored -> new LinkedHashMap<>()), host,
                maxSupported, "upgrade association");
    }

    @Override
    public synchronized void registerSpeedCard(final ItemStack upgradeCard, final int speedPoints,
            final boolean inheritStandardSupport) {
        requirePositive(speedPoints, "speedPoints");
        final StackKey card = StackKey.of(upgradeCard, "upgradeCard");
        final CardTraits traits = this.cards.computeIfAbsent(card, ignored -> new CardTraits());
        traits.setSpeed(speedPoints, inheritStandardSupport);
    }

    @Override
    public synchronized void addSpeedCardSupport(final ItemStack upgradableObject, final int maxSupported) {
        requirePositive(maxSupported, "maxSupported");
        putConsistent(this.speedSupport, StackKey.of(upgradableObject, "upgradableObject"), maxSupported,
                "speed-card support");
    }

    @Override
    public synchronized void registerCapacityCard(final ItemStack upgradeCard, final int capacityPoints,
            final boolean inheritStandardSupport) {
        requirePositive(capacityPoints, "capacityPoints");
        final StackKey card = StackKey.of(upgradeCard, "upgradeCard");
        final CardTraits traits = this.cards.computeIfAbsent(card, ignored -> new CardTraits());
        traits.setCapacity(capacityPoints, inheritStandardSupport);
    }

    @Override
    public synchronized void addCapacityCardSupport(final ItemStack upgradableObject, final int maxSupported,
            final int maxCapacityPoints) {
        requirePositive(maxSupported, "maxSupported");
        requirePositive(maxCapacityPoints, "maxCapacityPoints");
        final StackKey host = StackKey.of(upgradableObject, "upgradableObject");
        final CapacitySupport support = new CapacitySupport(maxSupported, maxCapacityPoints);
        final CapacitySupport previousSupport = this.capacitySupport.get(host);
        if (previousSupport != null && !previousSupport.equals(support)) {
            throw new IllegalArgumentException("Conflicting capacity-card support for " + upgradableObject);
        }
        requireConsistent(this.capacityLimits, host, maxCapacityPoints, "capacity limit");
        this.capacitySupport.putIfAbsent(host, support);
        this.capacityLimits.putIfAbsent(host, maxCapacityPoints);
    }

    @Override
    public synchronized void setCapacityLimit(final ItemStack upgradableObject, final int maxCapacityPoints) {
        requirePositive(maxCapacityPoints, "maxCapacityPoints");
        putConsistent(this.capacityLimits, StackKey.of(upgradableObject, "upgradableObject"), maxCapacityPoints,
                "capacity limit");
    }

    @Override
    public synchronized int getMaxInstallable(final ItemStack upgradeCard, final ItemStack upgradableObject) {
        if (upgradeCard.isEmpty() || upgradableObject.isEmpty()) {
            return 0;
        }

        final StackKey card = StackKey.of(upgradeCard);
        final StackKey host = StackKey.of(upgradableObject);
        int result = getOrZero(this.associations.get(card), host);
        final CardTraits traits = this.cards.get(card);
        if (traits != null) {
            if (traits.inheritSpeedSupport) {
                result = Math.max(result, this.speedSupport.getOrDefault(host, 0));
            }
            if (traits.inheritCapacitySupport) {
                final CapacitySupport support = this.capacitySupport.get(host);
                if (support != null) {
                    result = Math.max(result, support.maxCards);
                }
            }
        }
        return result;
    }

    @Override
    public synchronized int getSpeedPoints(final ItemStack upgradeCard) {
        final CardTraits traits = getTraits(upgradeCard);
        return traits == null ? 0 : traits.speedPoints;
    }

    @Override
    public synchronized boolean isSpeedCardSupported(final ItemStack upgradeCard,
            final ItemStack upgradableObject) {
        if (upgradeCard.isEmpty() || upgradableObject.isEmpty()) {
            return false;
        }
        final StackKey card = StackKey.of(upgradeCard);
        final StackKey host = StackKey.of(upgradableObject);
        final CardTraits traits = this.cards.get(card);
        return traits != null && traits.speedPoints > 0
                && (getOrZero(this.associations.get(card), host) > 0
                        || traits.inheritSpeedSupport && this.speedSupport.containsKey(host));
    }

    @Override
    public synchronized int getCapacityPoints(final ItemStack upgradeCard) {
        final CardTraits traits = getTraits(upgradeCard);
        return traits == null ? 0 : traits.capacityPoints;
    }

    @Override
    public synchronized boolean isCapacityCardSupported(final ItemStack upgradeCard,
            final ItemStack upgradableObject) {
        if (upgradeCard.isEmpty() || upgradableObject.isEmpty()) {
            return false;
        }
        final StackKey card = StackKey.of(upgradeCard);
        final StackKey host = StackKey.of(upgradableObject);
        final CardTraits traits = this.cards.get(card);
        return traits != null && traits.capacityPoints > 0 && this.capacityLimits.containsKey(host)
                && (getOrZero(this.associations.get(card), host) > 0
                        || traits.inheritCapacitySupport && this.capacitySupport.containsKey(host));
    }

    @Override
    public synchronized int getCapacityLimit(final ItemStack upgradableObject) {
        if (upgradableObject.isEmpty()) {
            return 0;
        }
        return this.capacityLimits.getOrDefault(StackKey.of(upgradableObject), 0);
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

        final CardTraits traits = this.cards.get(card);
        if (traits != null && traits.inheritSpeedSupport) {
            this.speedSupport.forEach((host, max) -> mergeMaximum(result, host.toStack(), max));
        }
        if (traits != null && traits.inheritCapacitySupport) {
            this.capacitySupport.forEach((host, support) -> mergeMaximum(result, host.toStack(), support.maxCards));
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

    private CardTraits getTraits(final ItemStack stack) {
        return stack.isEmpty() ? null : this.cards.get(StackKey.of(stack));
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

    private static final class CardTraits {
        private int speedPoints;
        private int capacityPoints;
        private boolean inheritSpeedSupport;
        private boolean inheritCapacitySupport;

        private void setSpeed(final int points, final boolean inherit) {
            if (this.speedPoints != 0 && (this.speedPoints != points || this.inheritSpeedSupport != inherit)) {
                throw new IllegalArgumentException("Conflicting speed-card registration");
            }
            this.speedPoints = points;
            this.inheritSpeedSupport = inherit;
        }

        private void setCapacity(final int points, final boolean inherit) {
            if (this.capacityPoints != 0
                    && (this.capacityPoints != points || this.inheritCapacitySupport != inherit)) {
                throw new IllegalArgumentException("Conflicting capacity-card registration");
            }
            this.capacityPoints = points;
            this.inheritCapacitySupport = inherit;
        }
    }

    private static final class CapacitySupport {
        private final int maxCards;
        private final int maxPoints;

        private CapacitySupport(final int maxCards, final int maxPoints) {
            this.maxCards = maxCards;
            this.maxPoints = maxPoints;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CapacitySupport)) {
                return false;
            }
            final CapacitySupport other = (CapacitySupport) obj;
            return this.maxCards == other.maxCards && this.maxPoints == other.maxPoints;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.maxCards, this.maxPoints);
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
            return 31 * System.identityHashCode(this.item) + this.metadata;
        }
    }
}
