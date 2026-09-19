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

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.patterns.IPatternEncodingHost;
import appeng.api.stacks.GenericStack;
import appeng.items.misc.ItemEncodedPattern;
import appeng.util.helpers.ItemHandlerUtil;

import static appeng.helpers.ItemStackHelper.stackWriteToNBT;

/** The AE2 encoded pattern item as the two built-in modes write and read it. */
final class EncodedPatterns {

    private EncodedPatterns() {
    }

    static boolean isEncodedPattern(final ItemStack stack, final boolean crafting) {
        return AEApi.instance().definitions().items().encodedPattern().isSameAs(stack)
                && ItemEncodedPattern.isCraftingPattern(stack) == crafting;
    }

    @Nullable
    static ICraftingPatternDetails decode(final ItemStack stack, final World world) {
        return stack.getItem() instanceof ICraftingPatternItem
                ? ((ICraftingPatternItem) stack.getItem()).getPatternForItem(stack, world)
                : null;
    }

    /** Writes every slot, so nothing of what was there before survives. */
    static void decodeInto(final GenericStack[] stacks, final IItemHandler grid) {
        for (int x = 0; x < grid.getSlots(); x++) {
            ItemHandlerUtil.setStackInSlot(grid, x, GenericStack.wrapInItemStack(x < stacks.length ? stacks[x] : null));
        }
    }

    static ItemStack encode(final ItemStack[] in, final ItemStack[] out, final boolean crafting,
            final IPatternEncodingHost host, final EntityPlayer player) {
        final ItemStack pattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1)
                .orElse(ItemStack.EMPTY);
        if (pattern.isEmpty()) {
            return pattern;
        }

        final NBTTagList tagIn = new NBTTagList();
        for (final ItemStack i : in) {
            tagIn.appendTag(tagOf(i));
        }
        final NBTTagList tagOut = new NBTTagList();
        for (final ItemStack i : out) {
            tagOut.appendTag(tagOf(i));
        }

        final NBTTagCompound encoded = new NBTTagCompound();
        encoded.setTag("in", tagIn);
        encoded.setTag("out", tagOut);
        encoded.setBoolean("crafting", crafting);
        encoded.setBoolean("substitute", host.isSubstitution());
        encoded.setBoolean("substitutefluids", host.isFluidSubstitution());
        encoded.setString("author", player.getName());
        pattern.setTagCompound(encoded);
        return pattern;
    }

    private static NBTTagCompound tagOf(final ItemStack i) {
        final NBTTagCompound c = new NBTTagCompound();
        if (!i.isEmpty()) {
            stackWriteToNBT(i, c);
        }
        return c;
    }
}
