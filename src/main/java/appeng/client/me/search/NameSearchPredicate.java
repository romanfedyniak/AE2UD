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

import java.util.function.Function;
import java.util.function.Predicate;

import appeng.api.stacks.AEKey;

/** A term with no prefix: the name the player reads on the row. */
final class NameSearchPredicate implements Predicate<AEKey> {

    private final String term;
    private final Function<AEKey, String> names;

    NameSearchPredicate(final String term, final Function<AEKey, String> names) {
        this.term = term;
        this.names = names;
    }

    @Override
    public boolean test(final AEKey what) {
        return this.names.apply(what).contains(this.term);
    }
}
