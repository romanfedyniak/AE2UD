/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.me.search;

import java.util.Locale;
import java.util.function.Predicate;

import appeng.api.stacks.AEKey;
import appeng.util.Platform;

/**
 * {@code @}: the mod's id and the mod's name, with a spaceless form of each - the four strings HEI indexes,
 * so that a query that finds a mod there finds it here. It is why {@code @ae2} works on a mod whose id is
 * {@code appliedenergistics2}: the name is not.
 */
final class ModSearchPredicate implements Predicate<AEKey> {

    private final String term;

    ModSearchPredicate(final String term) {
        this.term = term;
    }

    @Override
    public boolean test(final AEKey what) {
        final String modId = what.getModId();
        if (modId == null) {
            return false;
        }
        return this.matches(modId) || this.matches(Platform.getModName(modId));
    }

    private boolean matches(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains(this.term) || lower.replace(" ", "").contains(this.term);
    }
}
