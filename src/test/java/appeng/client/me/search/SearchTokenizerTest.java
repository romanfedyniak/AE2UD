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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class SearchTokenizerTest {

    @Test
    public void aRegexTermReachesTheRegexAsTyped() {
        assertEquals("/\\d+/", SearchTokenizer.tokenize("/\\d+/").get(0).get(0).text);
        assertEquals("/\\bore\\b/", SearchTokenizer.tokenize("/\\bore\\b/").get(0).get(0).text);
    }

    @Test
    public void aBarOrASpaceInsideARegexDoesNotSplitIt() {
        final List<List<SearchTokenizer.Term>> groups = SearchTokenizer.tokenize("/iron|gold/ -/a b/ ingot");
        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).size());
        assertEquals("/iron|gold/", groups.get(0).get(0).text);
        assertEquals("/a b/", groups.get(0).get(1).text);
        assertTrue(groups.get(0).get(1).excluded);
        assertEquals("ingot", groups.get(0).get(2).text);
    }

    @Test
    public void aQuotedOrEscapedFirstCharacterIsNoPrefix() {
        final SearchTokenizer.Term quoted = SearchTokenizer.tokenize("\"*Shiny*\"").get(0).get(0);
        assertEquals("*Shiny*", quoted.text);
        assertTrue(quoted.literal);

        final SearchTokenizer.Term escaped = SearchTokenizer.tokenize("\\@foo").get(0).get(0);
        assertEquals("@foo", escaped.text);
        assertTrue(escaped.literal);

        final SearchTokenizer.Term slashes = SearchTokenizer.tokenize("\"/x/\"").get(0).get(0);
        assertEquals("/x/", slashes.text);
        assertTrue(slashes.literal);

        assertFalse(SearchTokenizer.tokenize("@foo").get(0).get(0).literal);
        assertFalse(SearchTokenizer.tokenize("i\"ron\"").get(0).get(0).literal);
    }

    @Test
    public void aRegexThatOverflowsTheStackMatchesNothing() {
        final StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 200_000; i++) {
            longText.append('a');
        }
        final String text = longText.toString();
        final RegexSearchPredicate regex = RegexSearchPredicate.of("(.|x)*z", what -> text, what -> text);
        assertFalse(regex.test(null));
    }
}
