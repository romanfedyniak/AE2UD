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
    public static final CardTrait SPEED = CardTrait.counted(id("speed"),
            "gui.tooltips.appliedenergistics2.SpeedPoints");

    /**
     * How much of a filter a host offers. Capped per host through
     * {@link IUpgradeRegistry#setTraitLimit}, because the rows have to exist in a screen.
     */
    public static final CardTrait CAPACITY = CardTrait.counted(id("capacity"),
            "gui.tooltips.appliedenergistics2.CapacityPoints");

    /** Matches a filter loosely, by damage or by ore dictionary. */
    public static final CardTrait FUZZY = CardTrait.of(id("fuzzy"));

    /** Turns a filter from a whitelist into a blacklist. */
    public static final CardTrait INVERTER = CardTrait.of(id("inverter"));

    /** Keeps what a filter names where it already is, rather than letting it move on. */
    public static final CardTrait STICKY = CardTrait.of(id("sticky"));

    /** Divides a cell evenly between the types it is partitioned to. */
    public static final CardTrait EQUAL_DISTRIBUTION = CardTrait.of(id("equal_distribution"));

    /** Destroys what will not fit rather than refusing it. */
    public static final CardTrait VOID = CardTrait.of(id("void"));

    /** Lets a host ask the network to craft what it is short of. */
    public static final CardTrait CRAFTING = CardTrait.of(id("crafting"));

    /** Lets a pattern provider settle a job itself instead of pushing it at a machine. */
    public static final CardTrait FAKE_CRAFTING = CardTrait.of(id("fake_crafting"));

    /** Gives a host a redstone mode. */
    public static final CardTrait REDSTONE = CardTrait.of(id("redstone"));

    /** A larger battery on a powered tool. What one point is worth is the tool's own business. */
    public static final CardTrait ENERGY = CardTrait.counted(id("energy"));

    /** A row of nine more pattern slots each, capped per host. */
    public static final CardTrait PATTERN_EXPANSION = CardTrait.counted(id("pattern_expansion"));

    private CardTraits() {
    }

    private static ResourceLocation id(final String path) {
        return new ResourceLocation("appliedenergistics2", path);
    }
}
