/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.patterns;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * The modes of the pattern terminal, in the order the mode picker lists them.
 * <p>
 * Register during pre-initialisation or initialisation, before any terminal opens: every terminal makes room
 * for every mode's grids when it is built.
 */
public final class PatternEncodingModes {

    public static final ResourceLocation CRAFTING = new ResourceLocation("appliedenergistics2", "crafting");
    public static final ResourceLocation PROCESSING = new ResourceLocation("appliedenergistics2", "processing");

    private static volatile List<PatternEncodingMode> modes = ImmutableList.of();

    private PatternEncodingModes() {
    }

    public static synchronized void register(final PatternEncodingMode mode) {
        if (get(mode.getId()) != null) {
            throw new IllegalArgumentException("Pattern encoding mode registered twice: " + mode.getId());
        }
        modes = ImmutableList.<PatternEncodingMode>builder().addAll(modes).add(mode).build();
    }

    public static List<PatternEncodingMode> getAll() {
        return modes;
    }

    @Nullable
    public static PatternEncodingMode get(final ResourceLocation id) {
        for (final PatternEncodingMode mode : modes) {
            if (mode.getId().equals(id)) {
                return mode;
            }
        }
        return null;
    }

    /** The mode of that id, or crafting when no such mode is registered, as when its addon was removed. */
    public static PatternEncodingMode getOrDefault(@Nullable final ResourceLocation id) {
        final PatternEncodingMode mode = id == null ? null : get(id);
        return mode != null ? mode : get(CRAFTING);
    }

    /** The mode a recipe from this recipe viewer category is encoded in; processing when no mode claims it. */
    public static PatternEncodingMode forRecipeCategory(final String categoryUid) {
        for (final PatternEncodingMode mode : modes) {
            if (mode.claimsRecipeCategory(categoryUid)) {
                return mode;
            }
        }
        return get(PROCESSING);
    }

    /** @return the mode that encoded this pattern, or null for anything the terminal cannot load back. */
    @Nullable
    public static PatternEncodingMode forPattern(final ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (final PatternEncodingMode mode : modes) {
            if (mode.isPattern(stack)) {
                return mode;
            }
        }
        return null;
    }
}
