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
import java.util.Collection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;

import net.minecraft.util.text.TextFormatting;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.AEKey;
import appeng.client.me.search.SearchTokenizer.Term;
import appeng.core.AEClientConfig;
import appeng.util.Platform;

/**
 * The terminal's search field, compiled. The string is parsed once when it changes, into a predicate over
 * {@link AEKey}; what a key answers - its name, its tooltip, its Ore Dictionary names - is worked out once
 * per key and kept, and so is whether it matched.
 * <p>
 * That caching is the point. The filter runs again on every update the network sends, and the old code
 * recompiled a {@link java.util.regex.Pattern} and rebuilt a full tooltip for every row every time.
 */
public final class RepoSearch {

    private String searchString = "";
    private String compiledFrom = "";
    private boolean searchTooltips;
    private Predicate<AEKey> search = what -> true;
    private Predicate<Collection<AEKey>> searchGroup = keys -> true;
    private boolean positiveTerms;

    private final Object2BooleanMap<AEKey> matches = new Object2BooleanOpenHashMap<>();
    private final Map<AEKey, String> names = new HashMap<>();
    private final Map<AEKey, String> tooltips = new HashMap<>();
    private final Map<AEKey, List<String>> oreDictNames = new HashMap<>();

    public RepoSearch() {
        this.searchTooltips = searchesTooltips();
    }

    public String getSearchString() {
        return this.searchString;
    }

    public void setSearchString(@Nullable final String searchString) {
        this.searchString = searchString == null ? "" : searchString;
    }

    /**
     * Recompiles the predicate if the query or the tooltip setting has moved since the last call, and says
     * whether it did - the view has to be rebuilt exactly then.
     */
    public boolean refresh() {
        final boolean tooltipsNow = searchesTooltips();

        if (tooltipsNow == this.searchTooltips && this.searchString.equals(this.compiledFrom)) {
            return false;
        }

        this.searchTooltips = tooltipsNow;
        this.compiledFrom = this.searchString;
        this.compile(this.searchString);
        this.matches.clear();
        return true;
    }

    /**
     * A name written as one term of this grammar: quoted, so that the spaces in it stay part of the name
     * and none of the grammar's own characters are read as grammar.
     */
    public static String termFor(final AEKey what) {
        return quote(TextFormatting.getTextWithoutFormattingCodes(
                what.getDisplayName().getUnformattedText()));
    }

    public static String quote(final String name) {
        return '"' + name.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    public boolean matches(final AEKey what) {
        if (this.matches.containsKey(what)) {
            return this.matches.getBoolean(what);
        }

        final boolean result = this.search.test(what);
        this.matches.put(what, result);
        return result;
    }

    /**
     * Whether a set of keys answers the query - a pattern's ingredients, an interface's configured items.
     * A positive term needs one key in the set to satisfy it; an excluded term needs none to. That is what
     * {@code -iron} says about a whole pattern, and it cannot be said one key at a time: asked of a single
     * ingredient it only means "this one is not iron", which every other ingredient answers yes to.
     */
    public boolean matchesAny(final Collection<AEKey> keys) {
        return this.searchGroup.test(keys);
    }

    /**
     * Whether the query names anything to find, rather than only what to leave out. A screen that marks
     * what it found asks this first: {@code -iron} is answered by everything that is not iron, so
     * marking each of those would light the whole screen and point at nothing.
     */
    public boolean hasPositiveTerms() {
        return this.positiveTerms;
    }

    /** Both readings of the query, built from one pass over its terms. */
    private void compile(final String query) {
        final List<List<Term>> groups = SearchTokenizer.tokenize(query);
        this.positiveTerms = false;
        final List<Predicate<AEKey>> keyGroups = new ArrayList<>(groups.size());
        final List<Predicate<Collection<AEKey>>> setGroups = new ArrayList<>(groups.size());

        for (final List<Term> group : groups) {
            final List<Predicate<AEKey>> keyTerms = new ArrayList<>(group.size());
            final List<Predicate<Collection<AEKey>>> setTerms = new ArrayList<>(group.size());

            for (final Term term : group) {
                final Predicate<AEKey> predicate = compileTerm(term.text);
                if (predicate == null) {
                    continue;
                }
                this.positiveTerms |= !term.excluded;
                keyTerms.add(term.excluded ? predicate.negate() : predicate);
                final Predicate<Collection<AEKey>> any = anyOf(predicate);
                setTerms.add(term.excluded ? any.negate() : any);
            }

            keyGroups.add(AndSearchPredicate.of(keyTerms));
            setGroups.add(AndSearchPredicate.of(setTerms));
        }

        this.search = OrSearchPredicate.of(keyGroups);
        this.searchGroup = OrSearchPredicate.of(setGroups);
    }

    private static Predicate<Collection<AEKey>> anyOf(final Predicate<AEKey> term) {
        return keys -> {
            for (final AEKey key : keys) {
                if (term.test(key)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** Null for a term that says nothing yet, such as a lone prefix being typed. */
    @Nullable
    private Predicate<AEKey> compileTerm(final String raw) {
        if (raw.length() > 2 && raw.charAt(0) == '/' && raw.charAt(raw.length() - 1) == '/') {
            return RegexSearchPredicate.of(raw.substring(1, raw.length() - 1), this::nameOf,
                    this.searchTooltips ? this::tooltipOf : null);
        }

        final String term = raw.substring(1).toLowerCase(Locale.ROOT);
        switch (raw.charAt(0)) {
            case '@':
                return term.isEmpty() ? null : new ModSearchPredicate(term);
            case '#':
                return term.isEmpty() ? null : new TooltipsSearchPredicate(term, this::tooltipOf);
            case '$':
                return term.isEmpty() ? null : new OreDictSearchPredicate(term, this::oreDictNamesOf);
            case '&':
            case '*':
                return term.isEmpty() ? null : new ItemIdSearchPredicate(term);
            default:
                break;
        }

        final String plain = raw.toLowerCase(Locale.ROOT);
        final Predicate<AEKey> byName = new NameSearchPredicate(plain, this::nameOf);
        if (!this.searchTooltips) {
            return byName;
        }

        // The setting says a bare term also looks inside tooltips, which is an OR with the name and not a
        // channel of its own - '#' stays the way to search tooltips alone.
        return OrSearchPredicate.of(Arrays.asList(byName, new TooltipsSearchPredicate(plain, this::tooltipOf)));
    }

    private String nameOf(final AEKey what) {
        return this.names.computeIfAbsent(what, key -> plain(key.getDisplayName().getUnformattedText()));
    }

    private List<String> oreDictNamesOf(final AEKey what) {
        return this.oreDictNames.computeIfAbsent(what, AEKey::getOreDictNames);
    }

    private String tooltipOf(final AEKey what) {
        return this.tooltips.computeIfAbsent(what, RepoSearch::buildTooltip);
    }

    private static String buildTooltip(final AEKey what) {
        final List<String> lines = Platform.getTooltip(what);
        if (lines.isEmpty()) {
            return "";
        }

        final String modName = Platform.getModName(what.getModId());
        final StringBuilder text = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            final String line = plain(lines.get(i));

            // Packs commonly hang the mod's name off the end of every tooltip through ItemTooltipEvent.
            // Left in, a bare term equal to that name would match the mod's whole catalogue, which is
            // what '@' is for.
            if (i > 0 && i == lines.size() - 1 && line.equals(modName.toLowerCase(Locale.ROOT))) {
                continue;
            }

            text.append(line).append('\n');
        }

        return text.toString();
    }

    private static String plain(final String text) {
        return TextFormatting.getTextWithoutFormattingCodes(text).toLowerCase(Locale.ROOT);
    }

    private static boolean searchesTooltips() {
        return AEClientConfig.instance().getConfigManager().getSetting(Settings.SEARCH_TOOLTIPS) != YesNo.NO;
    }
}
