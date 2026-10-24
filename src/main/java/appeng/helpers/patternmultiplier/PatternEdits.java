/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's pattern multi-tool (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.patternmultiplier;

import javax.annotation.Nullable;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.container.ContainerNull;
import appeng.helpers.ItemStackHelper;
import appeng.items.misc.ItemEncodedPattern;
import appeng.util.helpers.ItemHandlerUtil;

/**
 * What the pattern multi-tool does to an encoded pattern: scale every amount in it, swap one ingredient for
 * another, or wipe it back to a blank pattern.
 */
public final class PatternEdits {

    private PatternEdits() {
    }

    public enum Scale {
        MULTIPLY_BY_TWO, MULTIPLY_BY_THREE, INCREASE_BY_ONE, DIVIDE_BY_TWO, DIVIDE_BY_THREE, DECREASE_BY_ONE
    }

    /**
     * What replacing would do to one pattern.
     */
    public enum Replacement {
        /** The pattern does not use what is searched for. */
        NONE,
        /** It would change. */
        OK,
        /** It uses it, but the crafting recipe it would become does not exist; it is left alone. */
        INVALID
    }

    /**
     * A button's action, by name, on the first {@code slots} slots of {@code patterns}: {@code Clear},
     * {@code Replace}, or a {@link Scale}. Each changed pattern is set back as a copy, so that an interface
     * holding it hears it changed and reads it again.
     */
    public static void applyAll(final IItemHandler patterns, final int slots, final String action,
            final ItemStack find, final ItemStack replacement, final World world) {
        final Scale scale = scaleOf(action);
        for (int slot = 0; slot < Math.min(slots, patterns.getSlots()); slot++) {
            final ItemStack pattern = patterns.getStackInSlot(slot);
            if (!isPattern(pattern)) {
                continue;
            }
            final ItemStack edited = pattern.copy();
            final ItemStack result;
            if ("Clear".equals(action)) {
                result = clear(edited);
            } else if ("Replace".equals(action)) {
                result = replace(edited, find, replacement, world, true) == Replacement.OK ? edited : null;
            } else {
                result = scale != null && scale(edited, scale) ? edited : null;
            }
            if (result != null) {
                ItemHandlerUtil.setStackInSlot(patterns, slot, result);
            }
        }
    }

    @Nullable
    private static Scale scaleOf(final String action) {
        for (final Scale scale : Scale.values()) {
            if (scale.name().equals(action)) {
                return scale;
            }
        }
        return null;
    }

    public static boolean isPattern(final ItemStack stack) {
        return stack.getItem() instanceof ItemEncodedPattern && stack.hasTagCompound();
    }

    /**
     * Every amount in the pattern, inputs and outputs, scaled alike. A division that does not come out even for
     * every amount, and a step that would take an amount below one or past what it holds, leaves the pattern as it
     * was. A crafting pattern becomes a processing one, since a recipe makes only what it makes.
     *
     * @return whether the pattern changed
     */
    public static boolean scale(final ItemStack pattern, final Scale scale) {
        if (!isPattern(pattern)) {
            return false;
        }
        final NBTTagCompound tag = pattern.getTagCompound();
        final NBTTagList in = tag.getTagList("in", Constants.NBT.TAG_COMPOUND);
        final NBTTagList out = tag.getTagList("out", Constants.NBT.TAG_COMPOUND);
        final NBTTagList newIn = new NBTTagList();
        final NBTTagList newOut = new NBTTagList();
        if (!scaleAll(in, newIn, scale) || !scaleAll(out, newOut, scale)) {
            return false;
        }
        tag.setTag("in", newIn);
        tag.setTag("out", newOut);
        tag.setBoolean("crafting", false);
        tag.setBoolean("substitute", false);
        tag.setBoolean("substitutefluids", false);
        return true;
    }

    private static boolean scaleAll(final NBTTagList from, final NBTTagList to, final Scale scale) {
        for (int i = 0; i < from.tagCount(); i++) {
            final NBTTagCompound entry = from.getCompoundTagAt(i);
            final ItemStack stack = ItemStackHelper.stackFromNBT(entry);
            if (stack.isEmpty()) {
                to.appendTag(entry.copy());
                continue;
            }
            final GenericStack wrapped = GenericStack.unwrapItemStack(stack);
            final long amount = wrapped != null ? wrapped.amount() : stack.getCount();
            final long limit = wrapped != null ? Long.MAX_VALUE : Integer.MAX_VALUE;
            final long scaled = scaled(amount, scale, limit);
            if (scaled < 1) {
                return false;
            }
            final ItemStack result;
            if (wrapped != null) {
                result = GenericStack.wrapInItemStack(wrapped.what(), scaled);
            } else {
                result = stack.copy();
                result.setCount((int) scaled);
            }
            to.appendTag(ItemStackHelper.stackToNBT(result));
        }
        return true;
    }

    /**
     * @return the new amount, or 0 when this amount cannot take the step
     */
    private static long scaled(final long amount, final Scale scale, final long limit) {
        switch (scale) {
            case MULTIPLY_BY_TWO:
                return amount <= limit / 2 ? amount * 2 : 0;
            case MULTIPLY_BY_THREE:
                return amount <= limit / 3 ? amount * 3 : 0;
            case INCREASE_BY_ONE:
                return amount < limit ? amount + 1 : 0;
            case DIVIDE_BY_TWO:
                return amount % 2 == 0 ? amount / 2 : 0;
            case DIVIDE_BY_THREE:
                return amount % 3 == 0 ? amount / 3 : 0;
            case DECREASE_BY_ONE:
                return amount - 1;
            default:
                return 0;
        }
    }

    /**
     * The pattern back to as many blank patterns.
     */
    public static ItemStack clear(final ItemStack pattern) {
        if (!isPattern(pattern)) {
            return pattern;
        }
        return AEApi.instance().definitions().materials().blankPattern().maybeStack(pattern.getCount())
                .orElse(pattern);
    }

    /**
     * Every ingredient and result that is {@code find} becomes {@code replacement}, in the same amount; with no
     * replacement it is taken out. An item is matched by item and damage, its tag aside; a fluid also by a
     * container that holds it.
     */
    public static Replacement replace(final ItemStack pattern, final ItemStack find, final ItemStack replacement,
            final World world, final boolean apply) {
        if (!isPattern(pattern) || find.isEmpty()) {
            return Replacement.NONE;
        }
        final NBTTagCompound tag = pattern.getTagCompound();
        final boolean crafting = tag.getBoolean("crafting");
        final AEKey findKey = keyOf(find);
        final AEKey replacementKey = replacement.isEmpty() ? null : keyOf(replacement);

        final NBTTagList newIn = new NBTTagList();
        final NBTTagList newOut = new NBTTagList();
        final boolean changedIn = replaceAll(tag.getTagList("in", Constants.NBT.TAG_COMPOUND), newIn, find, findKey,
                replacement, replacementKey, crafting);
        final boolean changedOut = !crafting && replaceAll(tag.getTagList("out", Constants.NBT.TAG_COMPOUND), newOut,
                find, findKey, replacement, replacementKey, false);
        if (!changedIn && !changedOut) {
            return Replacement.NONE;
        }
        if (crafting && !isRecipe(newIn, world)) {
            return Replacement.INVALID;
        }
        if (apply) {
            tag.setTag("in", newIn);
            if (!crafting) {
                tag.setTag("out", newOut);
            }
        }
        return Replacement.OK;
    }

    private static boolean replaceAll(final NBTTagList from, final NBTTagList to, final ItemStack find,
            @Nullable final AEKey findKey, final ItemStack replacement, @Nullable final AEKey replacementKey,
            final boolean keepPlaces) {
        boolean changed = false;
        for (int i = 0; i < from.tagCount(); i++) {
            final NBTTagCompound entry = from.getCompoundTagAt(i);
            final ItemStack stack = ItemStackHelper.stackFromNBT(entry);
            final GenericStack wrapped = GenericStack.unwrapItemStack(stack);
            final boolean matches = !stack.isEmpty() && (wrapped != null
                    ? wrapped.what().equals(findKey)
                    : stack.isItemEqual(find));
            if (!matches) {
                to.appendTag(entry.copy());
                continue;
            }
            changed = true;
            final long amount = wrapped != null ? wrapped.amount() : stack.getCount();
            final ItemStack result = replaced(replacement, replacementKey, amount);
            if (result.isEmpty()) {
                // A crafting grid keeps its nine places, or the recipe would shift.
                if (keepPlaces) {
                    to.appendTag(new NBTTagCompound());
                }
                continue;
            }
            to.appendTag(ItemStackHelper.stackToNBT(result));
        }
        return changed;
    }

    private static ItemStack replaced(final ItemStack replacement, @Nullable final AEKey replacementKey,
            final long amount) {
        if (replacement.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (GenericStack.isWrapped(replacement) || replacementKey instanceof AEFluidKey) {
            return replacementKey == null ? ItemStack.EMPTY : GenericStack.wrapInItemStack(replacementKey, amount);
        }
        final ItemStack result = replacement.copy();
        result.setCount((int) Math.min(amount, Integer.MAX_VALUE));
        return result;
    }

    /**
     * The key a wrapped stack stands for, or the fluid a container holds; null for a plain item.
     */
    @Nullable
    private static AEKey keyOf(final ItemStack stack) {
        final GenericStack wrapped = GenericStack.unwrapItemStack(stack);
        if (wrapped != null) {
            return wrapped.what();
        }
        final FluidStack fluid = FluidUtil.getFluidContained(stack);
        return fluid == null ? null : AEFluidKey.of(fluid);
    }

    private static boolean isRecipe(final NBTTagList in, final World world) {
        if (in.tagCount() != 9) {
            return false;
        }
        final InventoryCrafting grid = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int i = 0; i < 9; i++) {
            grid.setInventorySlotContents(i, ItemStackHelper.stackFromNBT(in.getCompoundTagAt(i)));
        }
        return CraftingManager.findMatchingRecipe(grid, world) != null;
    }
}
