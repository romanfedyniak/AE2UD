/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.upgrades;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;

/**
 * Access to the standard AE2 upgrade-card stacks.
 */
public final class UpgradeCards {

    private UpgradeCards() {
    }

    public static ItemStack capacity() {
        return stack(AEApi.instance().definitions().materials().cardCapacity());
    }

    public static ItemStack redstone() {
        return stack(AEApi.instance().definitions().materials().cardRedstone());
    }

    public static ItemStack crafting() {
        return stack(AEApi.instance().definitions().materials().cardCrafting());
    }

    public static ItemStack magnet() {
        return stack(AEApi.instance().definitions().materials().cardMagnet());
    }

    public static ItemStack sticky() {
        return stack(AEApi.instance().definitions().materials().cardSticky());
    }

    public static ItemStack fuzzy() {
        return stack(AEApi.instance().definitions().materials().cardFuzzy());
    }

    public static ItemStack speed() {
        return stack(AEApi.instance().definitions().materials().cardSpeed());
    }

    public static ItemStack inverter() {
        return stack(AEApi.instance().definitions().materials().cardInverter());
    }

    public static ItemStack patternExpansion() {
        return stack(AEApi.instance().definitions().materials().cardPatternExpansion());
    }

    public static ItemStack quantumLink() {
        return stack(AEApi.instance().definitions().materials().cardQuantumLink());
    }

    private static ItemStack stack(final IItemDefinition definition) {
        return definition.maybeStack(1).orElse(ItemStack.EMPTY);
    }
}
