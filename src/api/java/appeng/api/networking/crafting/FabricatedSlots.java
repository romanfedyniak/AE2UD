/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.networking.crafting;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;

/**
 * Which squares of a recipe a network can fill in for itself.
 * <p>
 * A square holding a full bucket, where the recipe hands the empty one back, is a square the network never
 * has to keep buckets of water in: it can take the water it has, fill an empty bucket and put that in. The
 * same holds for any container the {@link ContainerItemStrategies} know how to empty.
 * <p>
 * Here rather than beside the pattern that uses it, because an addon encoding patterns on a bench of its
 * own has to decide the same thing about its own recipes, and the rule must be the one rule.
 */
public final class FabricatedSlots {

    private FabricatedSlots() {
    }

    /**
     * Whether what a machine is holding in that slot is a container the network conjured rather than an
     * item it ever had. A crafting cpu draws the contents out of storage and writes the container straight
     * into the table it hands the machine, so nothing in the network answers for it.
     * <p>
     * Such a stack must never leave the machine as an item: not dropped when the machine is broken, not
     * taken out of its window, not pushed anywhere. It is destroyed instead, exactly as the contents were
     * conjured, or a bucket is made out of the water that paid for it. A machine holding a plan a cpu
     * pushed asks this about every slot it is about to give up.
     *
     * @param details     the plan the machine is running, or null for none
     * @param cpuSupplied whether a crafting cpu pushed that plan, rather than a player feeding the machine
     *                    a pattern of their own
     */
    public static boolean isConjured(@Nullable final ICraftingPatternDetails details, final boolean cpuSupplied,
            final int slot) {
        return cpuSupplied && details != null && details.getPatternInputs().get(slot).isFabricated();
    }

    /**
     * @param grid   the recipe as it was laid out, which is not modified
     * @param recipe the recipe that grid makes, or null when it makes nothing
     *
     * @return one entry per square of {@code grid}: what the network would put into that square, or null
     *         for a square it has to hold the ingredient for
     */
    public static GenericStack[] find(final InventoryCrafting grid, final IRecipe recipe) {
        final GenericStack[] found = new GenericStack[grid.getSizeInventory()];

        if (recipe == null) {
            return found;
        }

        // On a throwaway copy: an implementation of getRemainingItems is free to empty the stacks it was
        // handed, and this grid is the reference frame every isValidItemForSlot test is built from. One mod
        // recipe draining it leaves the pattern rejecting its own ingredients.
        final NonNullList<ItemStack> remaining = recipe.getRemainingItems(copyOf(grid));

        for (int x = 0; x < found.length && x < remaining.size(); x++) {
            final ItemStack encoded = grid.getStackInSlot(x);

            if (encoded.isEmpty()) {
                continue;
            }

            final GenericStack contained = ContainerItemStrategies.getContainedStack(encoded);

            if (contained == null || contained.amount() <= 0) {
                continue;
            }

            final ItemStack emptied = emptyContainerOf(encoded);
            final ItemStack leftBehind = remaining.get(x);

            if (!emptied.isEmpty() && ItemStack.areItemsEqual(emptied, leftBehind)
                    && ItemStack.areItemStackTagsEqual(emptied, leftBehind)) {
                found[x] = contained;
            }
        }

        return found;
    }

    /**
     * @return what is left of the container once everything in it is taken out, or empty if it cannot be
     *         fully emptied.
     */
    private static ItemStack emptyContainerOf(final ItemStack container) {
        final ContainerItemStrategy.Context context = ContainerItemStrategies.openContext(container, null);

        if (context == null) {
            return ItemStack.EMPTY;
        }

        final GenericStack content = context.getExtractableContent();

        if (content == null || content.amount() <= 0
                || context.extract(content.what(), content.amount(), Actionable.MODULATE) != content.amount()) {
            return ItemStack.EMPTY;
        }

        return context.getContainer();
    }

    private static InventoryCrafting copyOf(final InventoryCrafting grid) {
        final InventoryCrafting copy = new InventoryCrafting(NO_CONTAINER, grid.getWidth(), grid.getHeight());

        for (int x = 0; x < grid.getSizeInventory(); x++) {
            copy.setInventorySlotContents(x, grid.getStackInSlot(x).copy());
        }

        return copy;
    }

    /** InventoryCrafting insists on a container to notify; nothing here is ever on a screen. */
    private static final Container NO_CONTAINER = new Container() {
        @Override
        public boolean canInteractWith(final EntityPlayer player) {
            return false;
        }
    };
}
