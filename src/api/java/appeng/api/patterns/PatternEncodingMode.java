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
import java.util.Objects;

import com.google.common.collect.ImmutableList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.stacks.GenericStack;

/**
 * One kind of pattern the pattern terminal can encode: its grids, which recipes from a recipe viewer it takes,
 * and how it turns what is in its grids into a pattern and back. Crafting and processing are the first two;
 * register more with {@link PatternEncodingModes}.
 */
public abstract class PatternEncodingMode {

    private final ResourceLocation id;
    private final List<PatternGrid> grids;

    protected PatternEncodingMode(final ResourceLocation id, final List<PatternGrid> grids) {
        this.id = Objects.requireNonNull(id);
        this.grids = ImmutableList.copyOf(grids);
    }

    public final ResourceLocation getId() {
        return this.id;
    }

    public final List<PatternGrid> getGrids() {
        return this.grids;
    }

    /** @return the grid of that name, or null. */
    public final PatternGrid getGrid(final String name) {
        for (final PatternGrid grid : this.grids) {
            if (grid.getName().equals(name)) {
                return grid;
            }
        }
        return null;
    }

    /** What the mode picker shows for this mode. */
    public abstract ItemStack getIcon();

    public abstract String getTranslationKey();

    /**
     * Whether a recipe from this recipe viewer category is encoded in this mode. A recipe no mode claims goes to
     * processing.
     */
    public boolean claimsRecipeCategory(final String categoryUid) {
        return false;
    }

    /**
     * Lays a recipe from a recipe viewer out over this mode's grids. Runs on the client. The default packs the
     * inputs into the first input grid, items first, and the outputs into the first output grid, leaving no gaps.
     */
    public void placeRecipe(final TransferredRecipe recipe, final RecipePlacement placement) {
        final PatternGrid inputs = this.firstGrid(PatternGrid.Role.INPUT);
        if (inputs != null) {
            int slot = 0;
            for (final List<GenericStack> alternatives : recipe.getItemInputs()) {
                if (!alternatives.isEmpty() && slot < inputs.getSize()) {
                    placement.put(inputs.getName(), slot++, alternatives);
                }
            }
            if (!inputs.isItemsOnly()) {
                for (final GenericStack stack : recipe.getOtherInputs()) {
                    if (slot < inputs.getSize()) {
                        placement.put(inputs.getName(), slot++, stack);
                    }
                }
                for (final GenericStack stack : recipe.getExtraInputs()) {
                    if (slot < inputs.getSize()) {
                        placement.put(inputs.getName(), slot++, stack);
                    }
                }
            }
        }

        final PatternGrid outputs = this.firstGrid(PatternGrid.Role.OUTPUT);
        if (outputs != null) {
            int slot = 0;
            for (final GenericStack stack : recipe.getOutputs()) {
                if (slot < outputs.getSize()) {
                    placement.put(outputs.getName(), slot++, stack);
                }
            }
        }
    }

    /** Runs on the server just before a transferred recipe is written into the grids. */
    public void beforeRecipePlaced(final IPatternEncodingHost host, final RecipePlacement placement) {
    }

    /**
     * Something the player did on this mode's panel, sent from the screen by
     * {@code IPatternTerminalScreen.sendModeAction}. Runs on the server, on the terminal the player has open.
     */
    public void onAction(final IPatternEncodingHost host, final String action) {
    }

    /** Whether this is a pattern of this mode, which the terminal can then load back into the grids. */
    public abstract boolean isPattern(ItemStack stack);

    /**
     * Lays a pattern of this mode out over the grids, replacing what is there.
     *
     * @return false if the pattern could not be read, leaving the grids as they were.
     */
    public abstract boolean load(IPatternEncodingHost host, ItemStack pattern);

    /**
     * Encodes what is in the grids.
     *
     * @return one encoded pattern, or an empty stack when the grids hold nothing this mode can encode.
     */
    public abstract ItemStack encode(IPatternEncodingHost host, EntityPlayer player);

    protected final PatternGrid firstGrid(final PatternGrid.Role role) {
        for (final PatternGrid grid : this.grids) {
            if (grid.getRole() == role) {
                return grid;
            }
        }
        return null;
    }
}
