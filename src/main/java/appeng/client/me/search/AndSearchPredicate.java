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
import java.util.function.Predicate;

import appeng.api.stacks.AEKey;

/** Every term in one group has to match. */
final class AndSearchPredicate implements Predicate<AEKey> {

    private final List<Predicate<AEKey>> terms;

    private AndSearchPredicate(final List<Predicate<AEKey>> terms) {
        this.terms = terms;
    }

    static Predicate<AEKey> of(final List<Predicate<AEKey>> terms) {
        if (terms.isEmpty()) {
            return what -> true;
        }
        if (terms.size() == 1) {
            return terms.get(0);
        }
        return new AndSearchPredicate(terms);
    }

    @Override
    public boolean test(final AEKey what) {
        for (final Predicate<AEKey> term : this.terms) {
            if (!term.test(what)) {
                return false;
            }
        }
        return true;
    }
}
