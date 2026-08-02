package appeng.core.features.registries;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

class UpgradeRegistryTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void matchesCardsAndHostsByItemAndMetadataButNotNbt() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final Item cardItem = new Item();
        final Item hostItem = new Item();
        final ItemStack registeredCard = stack(cardItem, 2);
        registeredCard.setTagCompound(tag("registered"));
        final ItemStack registeredHost = stack(hostItem, 4);

        registry.add(registeredCard, registeredHost, 3);

        final ItemStack queriedCard = stack(cardItem, 2);
        queriedCard.setTagCompound(tag("different"));
        assertEquals(3, registry.getMaxInstallable(queriedCard, stack(hostItem, 4)));
        assertEquals(0, registry.getMaxInstallable(stack(cardItem, 3), stack(hostItem, 4)));
        assertEquals(0, registry.getMaxInstallable(queriedCard, stack(hostItem, 5)));
    }

    @Test
    void inheritedTraitsCombineTheirPhysicalCardLimits() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack card = stack(new Item(), 0);
        final ItemStack host = stack(new Item(), 0);

        registry.registerSpeedCard(card, 100);
        registry.registerCapacityCard(card, 5);
        registry.addSpeedCardSupport(host, 4);
        registry.addCapacityCardSupport(host, 5, 5);

        assertEquals(5, registry.getMaxInstallable(card, host));
        assertEquals(100, registry.getSpeedPoints(card));
        assertEquals(5, registry.getCapacityPoints(card));
        assertEquals(5, registry.getCapacityLimit(host));
        assertTrue(registry.isSpeedCardSupported(card, host));
        assertTrue(registry.isCapacityCardSupported(card, host));
    }

    @Test
    void nonInheritingSpeedCardsRequireAnExactAssociation() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack card = stack(new Item(), 0);
        final ItemStack inheritedHost = stack(new Item(), 0);
        final ItemStack explicitHost = stack(new Item(), 0);

        registry.registerSpeedCard(card, 1_000, false);
        registry.addSpeedCardSupport(inheritedHost, 4);
        registry.add(card, explicitHost, 2);

        assertEquals(0, registry.getMaxInstallable(card, inheritedHost));
        assertEquals(2, registry.getMaxInstallable(card, explicitHost));
        assertFalse(registry.isSpeedCardSupported(card, inheritedHost));
        assertTrue(registry.isSpeedCardSupported(card, explicitHost));
    }

    @Test
    void capacityLimitCanBeRegisteredWithoutInheritedCardSupport() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack card = stack(new Item(), 0);
        final ItemStack host = stack(new Item(), 0);

        registry.registerCapacityCard(card, 20, false);
        registry.add(card, host, 1);
        registry.setCapacityLimit(host, 7);

        assertEquals(1, registry.getMaxInstallable(card, host));
        assertEquals(7, registry.getCapacityLimit(host));
        assertTrue(registry.isCapacityCardSupported(card, host));
    }

    @Test
    void conflictingCapacitySupportDoesNotPartiallyRegisterTheHost() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack card = stack(new Item(), 0);
        final ItemStack host = stack(new Item(), 0);

        registry.registerCapacityCard(card, 1);
        registry.setCapacityLimit(host, 5);

        assertThrows(IllegalArgumentException.class, () -> registry.addCapacityCardSupport(host, 4, 6));
        assertEquals(0, registry.getMaxInstallable(card, host));
        assertFalse(registry.isCapacityCardSupported(card, host));
        assertEquals(5, registry.getCapacityLimit(host));
    }

    @Test
    void duplicateRegistrationsAreIdempotentButConflictsFailFast() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack card = stack(new Item(), 0);
        final ItemStack host = stack(new Item(), 0);

        assertDoesNotThrow(() -> registry.registerSpeedCard(card, 10));
        assertDoesNotThrow(() -> registry.registerSpeedCard(card, 10));
        assertThrows(IllegalArgumentException.class, () -> registry.registerSpeedCard(card, 11));

        assertDoesNotThrow(() -> registry.add(card, host, 2));
        assertDoesNotThrow(() -> registry.add(card, host, 2));
        assertThrows(IllegalArgumentException.class, () -> registry.add(card, host, 3));
        assertThrows(IllegalArgumentException.class, () -> registry.registerCapacityCard(card, 0));

        assertTrue(registry.isUpgradeCard(card));
        assertFalse(registry.isUpgradeCard(ItemStack.EMPTY));
    }

    private static ItemStack stack(final Item item, final int metadata) {
        return new ItemStack(item, 1, metadata);
    }

    private static NBTTagCompound tag(final String value) {
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setString("test", value);
        return tag;
    }
}
