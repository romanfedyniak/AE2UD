/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.upgrades;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

/**
 * A property an upgrade card confers on whatever it is installed in, measured in points.
 *
 * <p>One card may carry several, which is how a single card stands in for two: it is registered with each
 * trait it confers, and every host that declared support for one of them reads it back through
 * {@link IUpgradeInventory#getInstalledPoints}. A trait a host never declared is ignored, so a card does
 * not smuggle in an effect by fitting through some other trait.</p>
 *
 * <p>Traits are interned by id, so two lookups of the same id give the same object and an addon need not
 * reach for someone else's constant to name a trait it wants to share.</p>
 */
public final class CardTrait {

    private static final Map<ResourceLocation, CardTrait> TRAITS = new ConcurrentHashMap<>();

    private final ResourceLocation id;
    private volatile String tooltipKey;
    private volatile boolean counted;

    private CardTrait(final ResourceLocation id) {
        this.id = id;
    }

    /**
     * A trait a host either has or has not, where a second card carrying it adds nothing to the first -
     * overflow destruction, sticky, fuzzy matching. Says nothing of itself in a card's tooltip either,
     * because the list of hosts underneath already tells the whole story.
     */
    public static CardTrait of(final ResourceLocation id) {
        return of(id, null);
    }

    /**
     * A trait where a second card adds to the first: speed, capacity, energy, pattern expansion. Hosts read
     * it as a total rather than as a yes, and how much of it they take is worth putting in a config.
     */
    public static CardTrait counted(final ResourceLocation id) {
        return counted(id, null);
    }

    /**
     * @param tooltipKey a translation key taking the point total, printed on every card carrying the trait
     */
    public static CardTrait counted(final ResourceLocation id, @Nullable final String tooltipKey) {
        final CardTrait trait = of(id, tooltipKey);
        trait.counted = true;
        return trait;
    }

    /**
     * @param tooltipKey a translation key taking the point total, printed on every card carrying the trait
     */
    public static CardTrait of(final ResourceLocation id, @Nullable final String tooltipKey) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }

        final CardTrait trait = TRAITS.computeIfAbsent(id, CardTrait::new);

        // Whoever names the trait first labels it, so the constant and a later bare lookup agree whichever
        // of the two the class loader reaches first.
        if (tooltipKey != null) {
            synchronized (trait) {
                if (trait.tooltipKey == null) {
                    trait.tooltipKey = tooltipKey;
                } else if (!trait.tooltipKey.equals(tooltipKey)) {
                    throw new IllegalArgumentException("Conflicting tooltip for card trait " + id);
                }
            }
        }

        return trait;
    }

    public ResourceLocation getId() {
        return this.id;
    }

    @Nullable
    public String getTooltipKey() {
        return this.tooltipKey;
    }

    /**
     * Whether a second card carrying this trait adds to the first. Sticky once set, so a bare
     * {@link #of(ResourceLocation)} elsewhere does not quietly undo the declaration.
     */
    public boolean isCounted() {
        return this.counted;
    }

    @Override
    public String toString() {
        return this.id.toString();
    }
}
