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
import net.minecraftforge.common.util.Constants;

import appeng.api.migration.LegacyRewrites;
import appeng.api.migration.LegacyStacks;

/**
 * Settings UEL kept on items and on the cell workbench: memory cards, interfaces taken with a wrench, the
 * wireless pattern terminal's grid.
 */
final class UelSettings {

    private static final String AE = "appliedenergistics2:";

    /**
     * Names a memory card remembers its source by, for the parts and blocks that were merged.
     */
    private static final String[][] RENAMED_SOURCES = {
            { "fluid_storage_bus", "storage_bus" },
            { "fluid_import_bus", "import_bus" },
            { "fluid_export_bus", "export_bus" },
            { "fluid_level_emitter", "level_emitter" },
            { "identity_annihilation_plane", "annihilation_plane" },
            { "fluid_annihilation_plane", "annihilation_plane" },
            { "fluid_formation_plane", "formation_plane" },
            { "expanded_processing_pattern_terminal", "pattern_terminal" },
            { "fluid_interface", "interface" },
            { "fluid_terminal", "terminal" },
            { "fluid_interface_configuration_terminal", "interface_configuration_terminal" },
    };

    private UelSettings() {
    }

    static void register() {
        LegacyRewrites.addTileRewriter(AE + "cell_workbench", tile -> {
            LegacyStacks.rewriteInventory(tile.getCompoundTag("config"), "cell workbench" + UelInterfaces.at(tile));
            return tile;
        });
        LegacyRewrites.addStackRewriter(AE + "memory_card", UelSettings::memoryCard);
        LegacyRewrites.addStackRewriter(AE + "interface", stack -> {
            if (stack.getCompoundTag("tag").hasKey("config")) {
                UelInterfaces.rewriteItemInterface(stack.getCompoundTag("tag"), "interface item");
            }
        });
        LegacyRewrites.addStackRewriter(AE + "fluid_interface", stack -> {
            if (stack.getCompoundTag("tag").hasKey("config")) {
                UelInterfaces.rewriteFluidInterface(stack.getCompoundTag("tag"), "fluid interface item");
            }
        });
        LegacyRewrites.addStackRewriter(AE + "wireless_pattern_terminal", UelSettings::wirelessPatternTerminal);
    }

    private static void memoryCard(final NBTTagCompound stack) {
        final NBTTagCompound tag = stack.getCompoundTag("tag");
        if (!tag.hasKey("Data", Constants.NBT.TAG_COMPOUND)) {
            return;
        }
        final NBTTagCompound data = tag.getCompoundTag("Data");
        if (data.hasKey("config", Constants.NBT.TAG_COMPOUND)) {
            LegacyStacks.rewriteInventory(data.getCompoundTag("config"), "memory card");
        }

        final String source = tag.getString("Config");
        for (final String[] renamed : RENAMED_SOURCES) {
            if (source.endsWith("." + renamed[0])) {
                tag.setString("Config", source.substring(0, source.length() - renamed[0].length()) + renamed[1]);
                break;
            }
        }
    }

    /**
     * Before modes, the terminal kept its processing recipe in the crafting grid. Without a word about it, UEL
     * opened the terminal processing.
     */
    private static void wirelessPatternTerminal(final NBTTagCompound stack) {
        final NBTTagCompound tag = stack.getCompoundTag("tag");
        if (!tag.getBoolean("isCraftingMode")) {
            UelGrids.moveToProcessing(tag, "craftingGrid", "processing", 3);
        }
        tag.removeTag("isCraftingMode");
    }
}
