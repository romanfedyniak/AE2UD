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

import appeng.api.migration.LegacyRewrites;
import appeng.api.migration.LegacyStacks;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.PatternHelper;

/**
 * UEL's cells, encoded patterns and crafting CPUs.
 */
final class UelStorage {

    private static final String AE = "appliedenergistics2:";

    private static final String[] CELLS = { "storage_cell_1k", "storage_cell_4k", "storage_cell_16k",
            "storage_cell_64k", "fluid_storage_cell_1k", "fluid_storage_cell_4k", "fluid_storage_cell_16k",
            "fluid_storage_cell_64k", "portable_cell", "matter_cannon", "color_applicator" };

    private static final String[] CPU_TILES = { "crafting_unit", "crafting_storage", "crafting_monitor" };

    private UelStorage() {
    }

    static void register() {
        for (final String cell : CELLS) {
            LegacyStacks.addCell(AE + cell);
        }
        LegacyRewrites.addStackRewriter(AE + "encoded_pattern", UelStorage::pattern);
        for (final String tile : CPU_TILES) {
            LegacyRewrites.addTileRewriter(AE + tile, saved -> {
                cancelJob(saved);
                return saved;
            });
        }
    }

    /**
     * Another mod's terminal wrote patterns 81 inputs long, while AE2UD lays a pattern out on a grid of 32. The
     * empty end is cut off; a pattern that really uses more stays as it is, unusable, and is logged.
     */
    private static void pattern(final NBTTagCompound stack) {
        final NBTTagCompound tag = stack.getCompoundTag("tag");
        final int limit = tag.getBoolean("crafting") ? 9 : PatternHelper.PROCESSING_INPUT_LIMIT;
        for (final String key : new String[] { "in", "out" }) {
            final NBTTagList list = tag.getTagList(key, Constants.NBT.TAG_COMPOUND);
            if (list.tagCount() <= limit) {
                continue;
            }
            int last = list.tagCount();
            while (last > limit && isEmpty(list.getCompoundTagAt(last - 1))) {
                last--;
            }
            if (last > limit) {
                LegacyRewrites.log().note("pattern with " + last + " " + key + "puts, more than " + limit
                        + "; it cannot be used until it is encoded again: " + stack);
                return;
            }
            while (list.tagCount() > last) {
                list.removeTag(list.tagCount() - 1);
            }
        }
    }

    private static boolean isEmpty(final NBTTagCompound stack) {
        return stack.isEmpty() || stack.getInteger("Count") <= 0 || "minecraft:air".equals(stack.getString("id"));
    }

    /**
     * Jobs that were running are cancelled: what the CPU held is all that survives, and a finished CPU hands it
     * back to the network on its own.
     */
    private static void cancelJob(final NBTTagCompound tile) {
        // AE2UD always saves it, UEL never did.
        if (!tile.getBoolean("core") || tile.hasKey("craftPriority")) {
            return;
        }
        final String where = "crafting CPU" + UelInterfaces.at(tile);

        final NBTTagList inventory = tile.getTagList("inventory", Constants.NBT.TAG_COMPOUND);
        final NBTTagList rewritten = new NBTTagList();
        for (int i = 0; i < inventory.tagCount(); i++) {
            final NBTTagCompound saved = inventory.getCompoundTagAt(i);
            if (saved.hasKey(AEKey.TYPE_FIELD)) {
                rewritten.appendTag(saved);
                continue;
            }
            final GenericStack read = LegacyStacks.read(saved);
            if (read == null || read.amount() <= 0) {
                if (read == null) {
                    LegacyRewrites.log().lost(where + ": " + saved);
                }
                continue;
            }
            final NBTTagCompound entry = new NBTTagCompound();
            GenericStack.writeTag(entry, read);
            rewritten.appendTag(entry);
        }
        tile.setTag("inventory", rewritten);

        final boolean running = !tile.getBoolean("isComplete")
                || tile.getTagList("tasks", Constants.NBT.TAG_COMPOUND).tagCount() > 0;
        if (running) {
            LegacyRewrites.log().note(where + ": its job was cancelled; " + rewritten.tagCount()
                    + " kinds of things it held go back to the network");
        }
        for (final String key : new String[] { "finalOutput", "tasks", "waitingFor", "link", "waiting",
                "requestingPlayerUUIDMost", "requestingPlayerUUIDLeast" }) {
            tile.removeTag(key);
        }
        tile.setBoolean("isComplete", true);
    }
}
