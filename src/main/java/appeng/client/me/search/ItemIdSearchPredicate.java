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

/** {@code &} or {@code *}: the registry name, such as {@code minecraft:stone}. */
final class ItemIdSearchPredicate implements Predicate<AEKey> {

    private final String term;

    ItemIdSearchPredicate(final String term) {
        this.term = term;
    }

    @Override
    public boolean test(final AEKey what) {
        return what.getId() != null && what.getId().toString().toLowerCase(Locale.ROOT).contains(this.term);
    }
}
