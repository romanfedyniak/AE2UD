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
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.upgrades.CardTrait;

class UpgradeRegistryTest {

    private static final CardTrait ALPHA = CardTrait.of(new ResourceLocation("test", "alpha"));
    private static final CardTrait BETA = CardTrait.of(new ResourceLocation("test", "beta"));

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

        registry.registerCard(card, ALPHA, 100);
        registry.registerCard(card, BETA, 5);
        registry.addTraitSupport(ALPHA, host, 4);
        registry.addTraitSupport(BETA, host, 5);

        assertEquals(5, registry.getMaxInstallable(card, host));
        assertEquals(100, registry.getPoints(card, ALPHA));
        assertEquals(5, registry.getPoints(card, BETA));
        assertTrue(registry.isTraitSupported(card, ALPHA, host));
        assertTrue(registry.isTraitSupported(card, BETA, host));
    }

    @Test
    void nonInheritingCardsRequireAnExactAssociation() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack card = stack(new Item(), 0);
        final ItemStack inheritedHost = stack(new Item(), 0);
        final ItemStack explicitHost = stack(new Item(), 0);

        registry.registerCard(card, ALPHA, 1_000, false);
        registry.addTraitSupport(ALPHA, inheritedHost, 4);
        registry.add(card, explicitHost, 2);

        assertEquals(0, registry.getMaxInstallable(card, inheritedHost));
        assertEquals(2, registry.getMaxInstallable(card, explicitHost));
        assertFalse(registry.isTraitSupported(card, ALPHA, inheritedHost));
        assertTrue(registry.isTraitSupported(card, ALPHA, explicitHost));
    }

    @Test
    void aTraitLimitCanBeSetWithoutInheritedCardSupport() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack card = stack(new Item(), 0);
        final ItemStack host = stack(new Item(), 0);

        registry.registerCard(card, ALPHA, 20, false);
        registry.add(card, host, 1);
        registry.setTraitLimit(ALPHA, host, 7);

        assertEquals(1, registry.getMaxInstallable(card, host));
        assertEquals(7, registry.getTraitLimit(ALPHA, host));
        assertTrue(registry.isTraitSupported(card, ALPHA, host));
    }

    @Test
    void countsOnlyTheTraitsTheHostDeclared() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack card = stack(new Item(), 0);
        final ItemStack host = stack(new Item(), 0);

        registry.registerCard(card, ALPHA, 3);
        registry.registerCard(card, BETA, 7);
        registry.addTraitSupport(ALPHA, host, 2);

        final IItemHandler installed = handler(card);
        assertEquals(3, registry.getInstalledPoints(installed, host, ALPHA));
        assertEquals(0, registry.getInstalledPoints(installed, host, BETA));
    }

    @Test
    void installedPointsSaturateAtTheHostLimit() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack card = stack(new Item(), 0);
        final ItemStack capped = stack(new Item(), 0);

        registry.registerCard(card, ALPHA, 4);
        registry.addTraitSupport(ALPHA, capped, 3);
        registry.setTraitLimit(ALPHA, capped, 5);

        assertEquals(5, registry.getInstalledPoints(handler(card, card), capped, ALPHA));
    }

    @Test
    void refusesACardOnlyWhenNothingItBringsHasRoomLeft() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack cappedOnly = stack(new Item(), 0);
        final ItemStack alsoUncapped = stack(new Item(), 0);
        final ItemStack host = stack(new Item(), 0);

        registry.registerCard(cappedOnly, ALPHA, 2);
        registry.registerCard(alsoUncapped, ALPHA, 2);
        registry.registerCard(alsoUncapped, BETA, 1);
        registry.addTraitSupport(ALPHA, host, 4);
        registry.addTraitSupport(BETA, host, 4);
        registry.setTraitLimit(ALPHA, host, 2);

        final IItemHandler full = handler(cappedOnly);
        assertTrue(registry.canInstall(cappedOnly, host, handler()));
        assertFalse(registry.canInstall(cappedOnly, host, full));
        assertTrue(registry.canInstall(alsoUncapped, host, full));
    }

    @Test
    void aCardWithNoTraitsIsJudgedOnItsCountAlone() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack card = stack(new Item(), 0);
        final ItemStack host = stack(new Item(), 0);

        registry.add(card, host, 1);

        assertTrue(registry.canInstall(card, host, handler()));
        assertFalse(registry.canInstall(card, host, handler(card)));
        assertFalse(registry.canInstall(stack(new Item(), 0), host, handler()));
    }

    @Test
    void duplicateRegistrationsAreIdempotentButConflictsFailFast() {
        final UpgradeRegistry registry = new UpgradeRegistry();
        final ItemStack card = stack(new Item(), 0);
        final ItemStack host = stack(new Item(), 0);

        assertDoesNotThrow(() -> registry.registerCard(card, ALPHA, 10));
        assertDoesNotThrow(() -> registry.registerCard(card, ALPHA, 10));
        assertThrows(IllegalArgumentException.class, () -> registry.registerCard(card, ALPHA, 11));

        assertDoesNotThrow(() -> registry.add(card, host, 2));
        assertDoesNotThrow(() -> registry.add(card, host, 2));
        assertThrows(IllegalArgumentException.class, () -> registry.add(card, host, 3));
        assertThrows(IllegalArgumentException.class, () -> registry.registerCard(card, BETA, 0));

        registry.setTraitLimit(ALPHA, host, 5);
        assertThrows(IllegalArgumentException.class, () -> registry.setTraitLimit(ALPHA, host, 6));
        assertEquals(5, registry.getTraitLimit(ALPHA, host));

        assertTrue(registry.isUpgradeCard(card));
        assertFalse(registry.isUpgradeCard(ItemStack.EMPTY));
    }

    private static IItemHandler handler(final ItemStack... cards) {
        final ItemStackHandler installed = new ItemStackHandler(Math.max(cards.length, 1));
        for (int slot = 0; slot < cards.length; slot++) {
            installed.setStackInSlot(slot, cards[slot].copy());
        }
        return installed;
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
