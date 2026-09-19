/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.integrations.hei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import appeng.api.stacks.GenericStack;

/**
 * Where an addon registers its {@link ExtraInputProvider}s, by the uid of the recipe category they speak for,
 * and where moving a recipe into a processing pattern asks them.
 * <p>
 * Register during mod initialisation, before any screen opens.
 */
public final class ExtraInputProviders {

    private static volatile Map<String, List<ExtraInputProvider>> providers = ImmutableMap.of();

    private ExtraInputProviders() {
    }

    /**
     * Several providers may speak for one category; what they give is put into the pattern in the order they
     * were registered.
     */
    public static synchronized void register(final String categoryUid, final ExtraInputProvider provider) {
        final Map<String, List<ExtraInputProvider>> next = new HashMap<>(providers);
        next.put(categoryUid, ImmutableList.<ExtraInputProvider>builder()
                .addAll(next.getOrDefault(categoryUid, Collections.emptyList())).add(provider).build());
        providers = ImmutableMap.copyOf(next);
    }

    /**
     * @return what every provider for that category adds to the recipe; empty if none is registered.
     */
    public static List<GenericStack> getExtraInputs(final String categoryUid, final List<GenericStack> inputs,
            final List<GenericStack> outputs) {
        final List<ExtraInputProvider> forCategory = providers.get(categoryUid);
        if (forCategory == null) {
            return Collections.emptyList();
        }
        final List<GenericStack> extra = new ArrayList<>();
        for (final ExtraInputProvider provider : forCategory) {
            extra.addAll(provider.getExtraInputs(inputs, outputs));
        }
        return extra;
    }
}
