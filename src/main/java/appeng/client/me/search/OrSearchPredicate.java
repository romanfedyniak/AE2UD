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

/** Any group separated by {@code |} may match. */
final class OrSearchPredicate implements Predicate<AEKey> {

    private final List<Predicate<AEKey>> groups;

    private OrSearchPredicate(final List<Predicate<AEKey>> groups) {
        this.groups = groups;
    }

    static Predicate<AEKey> of(final List<Predicate<AEKey>> groups) {
        if (groups.isEmpty()) {
            return what -> false;
        }
        if (groups.size() == 1) {
            return groups.get(0);
        }
        return new OrSearchPredicate(groups);
    }

    @Override
    public boolean test(final AEKey what) {
        for (final Predicate<AEKey> group : this.groups) {
            if (group.test(what)) {
                return true;
            }
        }
        return false;
    }
}
