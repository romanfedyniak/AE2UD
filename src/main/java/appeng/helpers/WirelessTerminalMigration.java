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

import java.util.Collections;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import appeng.api.AEApi;
import appeng.items.tools.powered.ToolWirelessTerminal;
import appeng.util.Platform;

/**
 * Turns the wireless crafting, pattern and interface terminals of before into the one terminal with that mode
 * unlocked.
 *
 * <p>The old items are still registered, so nothing in a world is lost and no save complains about a missing
 * item. They are simply unreachable - no recipe, no creative tab - and each one becomes the real terminal the
 * moment a player carries it.</p>
 */
public final class WirelessTerminalMigration {

    private WirelessTerminalMigration() {
    }

    /**
     * The terminal this old one becomes, carrying its charge, its link to the network and everything stored on
     * it, or an empty stack if there is nothing to convert into.
     */
    public static ItemStack convert(final ItemStack legacy) {
        if (!(legacy.getItem() instanceof ToolWirelessTerminal)) {
            return ItemStack.EMPTY;
        }

        final ToolWirelessTerminal old = (ToolWirelessTerminal) legacy.getItem();
        final ResourceLocation mode = old.getLegacyMode();
        if (mode == null) {
            return ItemStack.EMPTY;
        }

        final Optional<Item> target = AEApi.instance().definitions().items().wirelessTerminal().maybeItem();
        if (!target.isPresent()) {
            return ItemStack.EMPTY;
        }

        final ItemStack converted = new ItemStack(target.get());
        final NBTTagCompound tag = legacy.getTagCompound();
        if (tag != null) {
            converted.setTagCompound(tag.copy());
        }

        // What the old screen kept on the item belongs to its mode now, or the crafting grid of one mode would
        // be the crafting grid of the next.
        final NBTTagCompound modeData = new NBTTagCompound();
        final NBTTagCompound convertedTag = Platform.openNbtData(converted);
        for (final String key : old.getLegacyModeKeys()) {
            if (convertedTag.hasKey(key)) {
                modeData.setTag(key, convertedTag.getTag(key));
                convertedTag.removeTag(key);
            }
        }

        WirelessTerminalModes.setModeData(converted, mode, modeData);
        WirelessTerminalModes.setUnlocked(converted, Collections.singletonList(mode));
        WirelessTerminalModes.setModeId(converted, mode);

        return converted;
    }

    /**
     * Swaps the old terminal for its replacement wherever the player is holding it.
     *
     * @return true once the stack has been taken out of the player's hands, after which the caller must not go
     *         on using it.
     */
    public static boolean convertInPlace(final ItemStack legacy, @Nullable final EntityPlayer player) {
        if (player == null) {
            return false;
        }

        final ItemStack converted = convert(legacy);
        if (converted.isEmpty()) {
            return false;
        }

        return replace(player.inventory.mainInventory, legacy, converted)
                || replace(player.inventory.offHandInventory, legacy, converted)
                || replace(player.inventory.armorInventory, legacy, converted);
    }

    /**
     * Found by identity rather than by contents: two terminals in one inventory are the same item with the
     * same tag right up until one of them is charged, and swapping the wrong one would move somebody's link.
     */
    private static boolean replace(final NonNullList<ItemStack> inventory, final ItemStack legacy,
            final ItemStack converted) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.get(slot) == legacy) {
                inventory.set(slot, converted);
                return true;
            }
        }

        return false;
    }
}
