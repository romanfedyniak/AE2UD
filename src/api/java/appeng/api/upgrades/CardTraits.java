/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.upgrades;

import net.minecraft.util.ResourceLocation;

/**
 * The card traits AE2 registers itself.
 */
public final class CardTraits {

    /** How often a machine acts. Uncapped: a host takes as many points as the cards in it carry. */
    public static final CardTrait SPEED = CardTrait.of(id("speed"),
            "gui.tooltips.appliedenergistics2.SpeedPoints");

    /**
     * How much of a filter a host offers. Capped per host through
     * {@link IUpgradeRegistry#setTraitLimit}, because the rows have to exist in a screen.
     */
    public static final CardTrait CAPACITY = CardTrait.of(id("capacity"),
            "gui.tooltips.appliedenergistics2.CapacityPoints");

    private CardTraits() {
    }

    private static ResourceLocation id(final String path) {
        return new ResourceLocation("appliedenergistics2", path);
    }
}
