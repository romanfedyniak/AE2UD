/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.migration.uel;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.helpers.PatternHelper;

final class UelGrids {

    private UelGrids() {
    }

    /**
     * UEL's pattern terminal kept a processing recipe in its crafting grid. It moves to AE2UD's processing grid,
     * row by row, so that it is laid out as the player left it.
     */
    static void moveToProcessing(final NBTTagCompound holder, final String from, final String to, final int width) {
        if (!holder.hasKey(from, Constants.NBT.TAG_COMPOUND) || holder.hasKey(to)) {
            return;
        }
        final NBTTagList items = holder.getCompoundTag(from).getTagList("Items", Constants.NBT.TAG_COMPOUND);
        final NBTTagList moved = new NBTTagList();
        for (int i = 0; i < items.tagCount(); i++) {
            final NBTTagCompound item = items.getCompoundTagAt(i).copy();
            final int slot = item.getInteger("Slot");
            item.setInteger("Slot", slot / width * PatternHelper.PROCESSING_INPUT_WIDTH + slot % width);
            moved.appendTag(item);
        }
        final NBTTagCompound grid = new NBTTagCompound();
        grid.setTag("Items", moved);
        grid.setInteger("Size", PatternHelper.PROCESSING_INPUT_LIMIT);
        holder.setTag(to, grid);
        holder.removeTag(from);
    }

    /**
     * How many of an inventory's {@code {Items, Size}} stacks are this item and meta.
     */
    static int count(final NBTTagCompound inventory, final String id, final int meta) {
        final NBTTagList items = inventory.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        int count = 0;
        for (int i = 0; i < items.tagCount(); i++) {
            final NBTTagCompound item = items.getCompoundTagAt(i);
            if (id.equals(item.getString("id")) && item.getShort("Damage") == meta) {
                count += Math.max(1, item.getInteger("Count"));
            }
        }
        return count;
    }
}
