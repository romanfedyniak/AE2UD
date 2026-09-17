/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import appeng.core.features.registries.WirelessTerminalMode;

/**
 * Takes a mode back out of a wireless terminal, and with it whatever real items that mode kept on the terminal.
 */
public final class WirelessTerminalModeRemoval {

    /**
     * Where each of AE2's own modes keeps real items, as opposed to ghosts. A mode not listed here is an
     * addon's, whose data is never touched: nothing says which of it is an item.
     */
    private static final Map<ResourceLocation, String[]> ITEMS = new HashMap<>();

    static {
        ITEMS.put(WirelessTerminalMode.Ids.TERMINAL, new String[0]);
        ITEMS.put(WirelessTerminalMode.Ids.CRAFTING, new String[] { "craftingGrid" });
        ITEMS.put(WirelessTerminalMode.Ids.PATTERN, new String[] { "patterns" });
        ITEMS.put(WirelessTerminalMode.Ids.PATTERN_ACCESS, new String[0]);
        ITEMS.put(WirelessTerminalMode.Ids.INTERFACE_CONFIGURATION, new String[0]);
    }

    private WirelessTerminalModeRemoval() {
    }

    /**
     * A terminal left with no mode written down reads as a plain storage terminal, so the last mode never
     * comes out.
     */
    public static boolean canTakeOut(final ItemStack terminal, final ResourceLocation id) {
        final List<ResourceLocation> unlocked = WirelessTerminalModes.getUnlocked(terminal);
        return unlocked.size() > 1 && unlocked.contains(id);
    }

    /**
     * The terminal without that mode, showing whichever mode it owns first.
     *
     * @param handedOut where the mode's items go, and its data with them; null keeps the data on the terminal
     *                  for when the mode is put back
     */
    public static ItemStack takeOut(final ItemStack terminal, final ResourceLocation id,
            @Nullable final List<ItemStack> handedOut) {
        final ItemStack result = terminal.copy();
        result.setCount(1);

        final List<ResourceLocation> unlocked = WirelessTerminalModes.getUnlocked(result);
        unlocked.remove(id);
        WirelessTerminalModes.setUnlocked(result, unlocked);
        WirelessTerminalModes.clearModeId(result);

        final String[] inventories = ITEMS.get(id);
        if (handedOut != null && inventories != null) {
            final NBTTagCompound data = WirelessTerminalModes.getModeData(result, id);
            for (final String inventory : inventories) {
                final NBTTagList items = data.getCompoundTag(inventory).getTagList("Items",
                        Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < items.tagCount(); i++) {
                    final ItemStack stack = ItemStackHelper.stackFromNBT(items.getCompoundTagAt(i));
                    if (!stack.isEmpty()) {
                        handedOut.add(stack);
                    }
                }
            }
            WirelessTerminalModes.removeModeData(result, id);
        }

        return result;
    }
}
