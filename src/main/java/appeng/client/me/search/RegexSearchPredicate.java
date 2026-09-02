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
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import appeng.api.stacks.AEKey;

/**
 * A term written between slashes, matched as a regular expression against the name and, while the
 * tooltip channel is on, against the tooltip. It is the one piece of the old search that the HEI grammar
 * has no room for - there the whole search string was compiled as a regex and run over tooltip lines -
 * so it survives here in a form that says out loud what it is.
 */
final class RegexSearchPredicate implements Predicate<AEKey> {

    private final Pattern pattern;
    private final Function<AEKey, String> names;
    @Nullable
    private final Function<AEKey, String> tooltips;

    private RegexSearchPredicate(final Pattern pattern, final Function<AEKey, String> names,
            @Nullable final Function<AEKey, String> tooltips) {
        this.pattern = pattern;
        this.names = names;
        this.tooltips = tooltips;
    }

    /** Null for an expression that does not compile, so a half-typed one filters nothing out. */
    @Nullable
    static RegexSearchPredicate of(final String expression, final Function<AEKey, String> names,
            @Nullable final Function<AEKey, String> tooltips) {
        try {
            return new RegexSearchPredicate(Pattern.compile(expression, Pattern.CASE_INSENSITIVE), names, tooltips);
        } catch (final Exception malformed) {
            return null;
        }
    }

    @Override
    public boolean test(final AEKey what) {
        if (this.pattern.matcher(this.names.apply(what)).find()) {
            return true;
        }
        return this.tooltips != null && this.pattern.matcher(this.tooltips.apply(what)).find();
    }
}
