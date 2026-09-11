/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.features.registries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.util.ResourceLocation;

class ChannelTierTest {

    private static ChannelTier tier(final int capacity) {
        return new ChannelTier(new ResourceLocation("test", "tier"), capacity);
    }

    @Test
    void theServersNumberReplacesTheLocalOneUntilRestored() {
        final ChannelTier tier = tier(32);

        assertTrue(tier.override(256));
        assertEquals(256, tier.getCapacity());

        assertTrue(tier.restore());
        assertEquals(32, tier.getCapacity());
    }

    @Test
    void anOverrideThatChangesNothingSaysSo() {
        final ChannelTier tier = tier(32);

        assertFalse(tier.override(32));
        assertFalse(tier.restore());
    }

    @Test
    void restoringTwiceLeavesTheLocalNumber() {
        final ChannelTier tier = tier(8);
        tier.override(0);

        assertTrue(tier.restore());
        assertFalse(tier.restore());
        assertEquals(8, tier.getCapacity());
    }
}
