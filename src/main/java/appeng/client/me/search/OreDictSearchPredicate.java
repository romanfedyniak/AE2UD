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

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

import appeng.api.stacks.AEKey;

/**
 * {@code $}: the Ore Dictionary names of the key. Empty for every type that does not answer
 * {@link AEKey#getOreDictNames()}, so a fluid never matches this channel unless an addon says it should.
 */
final class OreDictSearchPredicate implements Predicate<AEKey> {

    private final String term;
    private final Function<AEKey, List<String>> oreDictNames;

    OreDictSearchPredicate(final String term, final Function<AEKey, List<String>> oreDictNames) {
        this.term = term;
        this.oreDictNames = oreDictNames;
    }

    @Override
    public boolean test(final AEKey what) {
        for (final String name : this.oreDictNames.apply(what)) {
            if (name.toLowerCase(Locale.ROOT).contains(this.term)) {
                return true;
            }
        }
        return false;
    }
}
