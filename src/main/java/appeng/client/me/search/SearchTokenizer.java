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

import java.util.ArrayList;
import java.util.List;

/**
 * Splits what the player typed into groups joined by {@code |}, each group a list of terms joined by AND.
 * <p>
 * The grammar is HadEnoughItems', deliberately: the terminal can mirror its search string straight into
 * HEI's own field, and two panes side by side that read the same string differently would be worse than
 * any grammar we could invent.
 */
final class SearchTokenizer {

    private SearchTokenizer() {
    }

    static final class Term {
        final String text;
        final boolean excluded;

        Term(final String text, final boolean excluded) {
            this.text = text;
            this.excluded = excluded;
        }
    }

    static List<List<Term>> tokenize(final String query) {
        final List<List<Term>> groups = new ArrayList<>();
        List<Term> group = new ArrayList<>();
        final StringBuilder term = new StringBuilder();

        boolean quoted = false;
        boolean excluded = false;
        boolean escaped = false;

        for (int i = 0; i < query.length(); i++) {
            final char c = query.charAt(i);

            if (escaped) {
                term.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == '|') {
                excluded = end(group, term, excluded);
                groups.add(group);
                group = new ArrayList<>();
            } else if (!quoted && Character.isWhitespace(c)) {
                excluded = end(group, term, excluded);
            } else if (!quoted && term.length() == 0 && (c == '-' || c == '!')) {
                excluded = true;
            } else {
                term.append(c);
            }
        }

        end(group, term, excluded);
        groups.add(group);

        return groups;
    }

    private static boolean end(final List<Term> group, final StringBuilder term, final boolean excluded) {
        if (term.length() > 0) {
            group.add(new Term(term.toString(), excluded));
            term.setLength(0);
        }
        return false;
    }
}
