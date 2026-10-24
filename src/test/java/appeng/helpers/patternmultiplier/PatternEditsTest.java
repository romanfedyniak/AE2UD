/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.patternmultiplier;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.ItemStackHelper;
import appeng.items.misc.ItemEncodedPattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public final class PatternEditsTest {

    private static ItemEncodedPattern patternItem;

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.register();
        patternItem = new ItemEncodedPattern();
        // Only plain items here: nothing is a wrapped key.
        GenericStack.setWrapper(new GenericStack.Wrapper() {
            @Override
            public ItemStack wrap(final AEKey what, final long amount) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isWrapped(final ItemStack stack) {
                return false;
            }

            @Override
            public GenericStack unwrap(final ItemStack stack) {
                return null;
            }
        });
    }

    @Test
    public void multiplyingScalesInputsAndOutputs() {
        final ItemStack pattern = processing(new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.GOLD_INGOT, 1));

        assertThat(PatternEdits.scale(pattern, PatternEdits.Scale.MULTIPLY_BY_TWO), is(true));
        assertThat(in(pattern, 0).getCount(), is(6));
        assertThat(out(pattern, 0).getCount(), is(2));
    }

    @Test
    public void amountsPastAByteAreKept() {
        final ItemStack pattern = processing(new ItemStack(Items.IRON_INGOT, 100), new ItemStack(Items.GOLD_INGOT, 1));

        assertThat(PatternEdits.scale(pattern, PatternEdits.Scale.MULTIPLY_BY_THREE), is(true));
        assertThat(in(pattern, 0).getCount(), is(300));
    }

    @Test
    public void unevenDivisionLeavesThePatternAlone() {
        final ItemStack pattern = processing(new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.GOLD_INGOT, 3));

        assertThat(PatternEdits.scale(pattern, PatternEdits.Scale.DIVIDE_BY_TWO), is(false));
        assertThat(in(pattern, 0).getCount(), is(4));
        assertThat(out(pattern, 0).getCount(), is(3));
    }

    @Test
    public void nothingGoesBelowOne() {
        final ItemStack pattern = processing(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.GOLD_INGOT, 1));

        assertThat(PatternEdits.scale(pattern, PatternEdits.Scale.DECREASE_BY_ONE), is(false));
        assertThat(in(pattern, 0).getCount(), is(2));
    }

    @Test
    public void replacingKeepsTheAmount() {
        final ItemStack pattern = processing(new ItemStack(Items.IRON_INGOT, 5), new ItemStack(Items.GOLD_INGOT, 1));

        assertThat(PatternEdits.replace(pattern, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.APPLE), null,
                true), is(PatternEdits.Replacement.OK));
        assertThat(in(pattern, 0).getItem(), is(Items.APPLE));
        assertThat(in(pattern, 0).getCount(), is(5));
        assertThat(PatternEdits.replace(pattern, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.APPLE), null,
                false), is(PatternEdits.Replacement.NONE));
    }

    private static ItemStack processing(final ItemStack in, final ItemStack out) {
        final NBTTagCompound tag = new NBTTagCompound();
        final NBTTagList inputs = new NBTTagList();
        inputs.appendTag(ItemStackHelper.stackToNBT(in));
        final NBTTagList outputs = new NBTTagList();
        outputs.appendTag(ItemStackHelper.stackToNBT(out));
        tag.setTag("in", inputs);
        tag.setTag("out", outputs);
        tag.setBoolean("crafting", false);
        final ItemStack pattern = new ItemStack(patternItem);
        pattern.setTagCompound(tag);
        return pattern;
    }

    private static ItemStack in(final ItemStack pattern, final int index) {
        return ItemStackHelper.stackFromNBT(pattern.getTagCompound().getTagList("in", 10).getCompoundTagAt(index));
    }

    private static ItemStack out(final ItemStack pattern, final int index) {
        return ItemStackHelper.stackFromNBT(pattern.getTagCompound().getTagList("out", 10).getCompoundTagAt(index));
    }
}
