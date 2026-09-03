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

/** Any group separated by {@code |} may match - of a key, or of a whole set of them. */
final class OrSearchPredicate<T> implements Predicate<T> {

    private final List<Predicate<T>> groups;

    private OrSearchPredicate(final List<Predicate<T>> groups) {
        this.groups = groups;
    }

    static <T> Predicate<T> of(final List<Predicate<T>> groups) {
        if (groups.isEmpty()) {
            return what -> false;
        }
        if (groups.size() == 1) {
            return groups.get(0);
        }
        return new OrSearchPredicate<>(groups);
    }

    @Override
    public boolean test(final T what) {
        for (final Predicate<T> group : this.groups) {
            if (group.test(what)) {
                return true;
            }
        }
        return false;
    }
}
