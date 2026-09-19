/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.encoding;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.items.IItemHandler;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.patterns.IPatternEncodingHost;
import appeng.api.patterns.PatternEncodingMode;
import appeng.api.patterns.PatternEncodingModes;
import appeng.api.patterns.PatternGrid;
import appeng.api.patterns.RecipePlacement;
import appeng.api.patterns.TransferredRecipe;
import appeng.api.stacks.GenericStack;
import appeng.container.ContainerNull;
import appeng.core.localization.GuiText;

import static appeng.helpers.PatternHelper.CRAFTING_GRID_DIMENSION;

/** The three-by-three crafting pattern, matched against the vanilla recipe list. */
public final class CraftingEncodingMode extends PatternEncodingMode {

    public static final String GRID = "crafting";

    private static final String CATEGORY = "minecraft.crafting";

    public CraftingEncodingMode() {
        super(PatternEncodingModes.CRAFTING, Collections.singletonList(
                PatternGrid.itemsOnly(GRID, CRAFTING_GRID_DIMENSION * CRAFTING_GRID_DIMENSION, PatternGrid.Role.INPUT)));
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Blocks.CRAFTING_TABLE);
    }

    @Override
    public String getTranslationKey() {
        return GuiText.CraftingPattern.getUnlocalized();
    }

    @Override
    public boolean claimsRecipeCategory(final String categoryUid) {
        return CATEGORY.equals(categoryUid);
    }

    /** A shape: an empty square stays empty and keeps its place. */
    @Override
    public void placeRecipe(final TransferredRecipe recipe, final RecipePlacement placement) {
        final List<List<GenericStack>> inputs = recipe.getItemInputs();
        for (int slot = 0; slot < inputs.size() && slot < CRAFTING_GRID_DIMENSION * CRAFTING_GRID_DIMENSION; slot++) {
            placement.put(GRID, slot, inputs.get(slot));
        }
    }

    @Override
    public boolean isPattern(final ItemStack stack) {
        return EncodedPatterns.isEncodedPattern(stack, true);
    }

    @Override
    public boolean load(final IPatternEncodingHost host, final ItemStack pattern) {
        final ICraftingPatternDetails details = EncodedPatterns.decode(pattern, host.getEncodingWorld());
        if (details == null) {
            return false;
        }
        host.setSubstitution(details.canSubstitute());
        host.setFluidSubstitution(details.canSubstituteFluids());
        EncodedPatterns.decodeInto(details.getInputs(), host.getEncodingGrid(this, GRID));
        return true;
    }

    @Override
    public ItemStack encode(final IPatternEncodingHost host, final EntityPlayer player) {
        final IItemHandler grid = host.getEncodingGrid(this, GRID);
        final ItemStack[] in = new ItemStack[grid.getSlots()];
        boolean any = false;
        for (int x = 0; x < in.length; x++) {
            in[x] = grid.getStackInSlot(x);
            any |= !in[x].isEmpty();
        }
        if (!any) {
            return ItemStack.EMPTY;
        }

        final ItemStack out = result(grid, host);
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return EncodedPatterns.encode(in, new ItemStack[] { out }, true, host, player);
    }

    private static ItemStack result(final IItemHandler grid, final IPatternEncodingHost host) {
        final InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), CRAFTING_GRID_DIMENSION, CRAFTING_GRID_DIMENSION);
        for (int x = 0; x < ic.getSizeInventory(); x++) {
            ic.setInventorySlotContents(x, grid.getStackInSlot(x));
        }
        final IRecipe recipe = CraftingManager.findMatchingRecipe(ic, host.getEncodingWorld());
        return recipe == null ? ItemStack.EMPTY : recipe.getCraftingResult(ic);
    }
}
