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

/** Every term in one group has to match - of a key, or of a whole set of them. */
final class AndSearchPredicate<T> implements Predicate<T> {

    private final List<Predicate<T>> terms;

    private AndSearchPredicate(final List<Predicate<T>> terms) {
        this.terms = terms;
    }

    static <T> Predicate<T> of(final List<Predicate<T>> terms) {
        if (terms.isEmpty()) {
            return what -> true;
        }
        if (terms.size() == 1) {
            return terms.get(0);
        }
        return new AndSearchPredicate<>(terms);
    }

    @Override
    public boolean test(final T what) {
        for (final Predicate<T> term : this.terms) {
            if (!term.test(what)) {
                return false;
            }
        }
        return true;
    }
}
