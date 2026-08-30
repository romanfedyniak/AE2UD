/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.util;


import appeng.api.AEApi;
import appeng.api.definitions.IMaterials;
import appeng.core.localization.PlayerMessages;
import appeng.items.contents.NetworkToolViewer;
import appeng.items.misc.ItemEncodedPattern;
import appeng.items.tools.ToolNetworkTool;
import appeng.tile.inventory.AppEngInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import java.util.ArrayList;
import java.util.List;


/**
 * What a memory card carries besides a machine's settings. These are real items rather than numbers, so
 * pasting them costs the player what installing them by hand would have cost.
 */
public final class MemoryCardSettings {

    private MemoryCardSettings() {
    }

    public static void exportUpgrades(final IItemHandler upgrades, final NBTTagCompound output) {
        if (upgrades instanceof AppEngInternalInventory) {
            ((AppEngInternalInventory) upgrades).writeToNBT(output, "upgrades");
        }
    }

    /**
     * Leaves the machine holding exactly the cards the copy names: what is missing is drawn from the player
     * and from a network tool they carry, and what the copy does not ask for is handed back the same way.
     */
    public static void importUpgrades(final IItemHandler upgrades, final NBTTagCompound input, final EntityPlayer player) {
        // A card written before the machine could carry these says nothing about them, which is not the
        // same as saying the machine should hold none.
        if (!Platform.isServer() || !(upgrades instanceof AppEngInternalInventory) || !input.hasKey("upgrades")) {
            return;
        }

        final AppEngInternalInventory target = (AppEngInternalInventory) upgrades;
        final AppEngInternalInventory desired = new AppEngInternalInventory(null, target.getSlots());
        desired.readFromNBT(input, "upgrades");

        if (player.capabilities.isCreativeMode) {
            for (int slot = 0; slot < target.getSlots(); slot++) {
                target.setStackInSlot(slot, desired.getStackInSlot(slot).copy());
            }

            return;
        }

        final List<IItemHandler> sources = new ArrayList<>(2);
        sources.add(new PlayerMainInvWrapper(player.inventory));

        final IItemHandler networkTool = findNetworkTool(player);
        if (networkTool != null) {
            sources.add(networkTool);
        }

        final List<ItemStack> wanted = new ArrayList<>();
        for (final ItemStack card : desired) {
            if (!card.isEmpty()) {
                wanted.add(card);
            }
        }

        for (int slot = 0; slot < target.getSlots(); slot++) {
            final ItemStack installed = target.getStackInSlot(slot);

            if (!installed.isEmpty() && !claim(wanted, installed)) {
                giveBack(target.extractItem(slot, installed.getCount(), false), sources, player);
            }
        }

        int missing = 0;

        for (final ItemStack card : wanted) {
            final ItemStack taken = takeCard(sources, card);

            if (taken.isEmpty()) {
                missing++;
            } else {
                giveBack(insert(target, taken), sources, player);
            }
        }

        if (missing > 0) {
            player.sendMessage(PlayerMessages.MissingUpgradesToInstall.get());
        }
    }

    /** How many cards or patterns a copy carries, for the memory card's own tooltip. */
    public static int countStored(final NBTTagCompound data, final String name) {
        if (!data.hasKey(name)) {
            return 0;
        }

        final ItemStackHandler stored = new ItemStackHandler();
        stored.deserializeNBT(data.getCompoundTag(name));

        int count = 0;
        for (int slot = 0; slot < stored.getSlots(); slot++) {
            count += stored.getStackInSlot(slot).getCount();
        }

        return count;
    }

    public static void exportPatterns(final IItemHandler patterns, final NBTTagCompound output) {
        if (patterns instanceof AppEngInternalInventory) {
            ((AppEngInternalInventory) patterns).writeToNBT(output, "patterns");
        }
    }

    /**
     * Re-encodes the recorded patterns from the player's own blank ones, handing back a blank for every
     * pattern it clears out first.
     *
     * @param usableSlots how many of the target's pattern slots it may write to. An interface earns more of
     *                    them with every expansion card, so the cards have to be restored before this runs.
     */
    public static void importPatterns(final IItemHandler patterns, final int usableSlots, final NBTTagCompound input, final EntityPlayer player) {
        if (!Platform.isServer() || !(patterns instanceof AppEngInternalInventory) || !input.hasKey("patterns")) {
            return;
        }

        final AppEngInternalInventory target = (AppEngInternalInventory) patterns;
        final AppEngInternalInventory desired = new AppEngInternalInventory(null, target.getSlots());
        desired.readFromNBT(input, "patterns");

        final boolean creative = player.capabilities.isCreativeMode;
        final IMaterials materials = AEApi.instance().definitions().materials();
        final PlayerMainInvWrapper playerInv = new PlayerMainInvWrapper(player.inventory);

        for (int slot = 0; slot < target.getSlots(); slot++) {
            final ItemStack current = target.getStackInSlot(slot);

            if (current.getItem() instanceof ItemEncodedPattern) {
                if (!creative) {
                    final ItemStack blank = materials.blankPattern().maybeStack(current.getCount()).get();
                    if (!player.addItemStackToInventory(blank)) {
                        player.dropItem(blank, true);
                    }
                }

                target.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }

        int missing = 0;

        for (int slot = 0; slot < Math.min(usableSlots, desired.getSlots()); slot++) {
            final ItemStack pattern = desired.getStackInSlot(slot);

            if (!(pattern.getItem() instanceof ItemEncodedPattern)) {
                continue;
            }

            // A recipe that no longer decodes is the source's junk, not a shortage of the player's.
            if (((ItemEncodedPattern) pattern.getItem()).getPatternForItem(pattern, player.world) == null) {
                continue;
            }

            if (creative || takeBlankPattern(playerInv, materials)) {
                target.setStackInSlot(slot, pattern);
            } else {
                missing++;
            }
        }

        if (missing > 0) {
            player.sendMessage(PlayerMessages.MissingPatternsToEncode.get());
        }
    }

    /** Ticks one card of this kind off what the copy still wants, if it wants one at all. */
    private static boolean claim(final List<ItemStack> wanted, final ItemStack card) {
        for (int i = 0; i < wanted.size(); i++) {
            if (isSameCard(wanted.get(i), card)) {
                wanted.remove(i);
                return true;
            }
        }

        return false;
    }

    private static ItemStack takeCard(final List<IItemHandler> sources, final ItemStack card) {
        for (final IItemHandler source : sources) {
            for (int slot = 0; slot < source.getSlots(); slot++) {
                if (isSameCard(source.getStackInSlot(slot), card)) {
                    final ItemStack taken = source.extractItem(slot, card.getCount(), false);

                    if (!taken.isEmpty()) {
                        return taken;
                    }
                }
            }
        }

        return ItemStack.EMPTY;
    }

    /** The player's own pockets first, then the network tool, and the floor when neither will have it. */
    private static void giveBack(ItemStack card, final List<IItemHandler> sources, final EntityPlayer player) {
        for (final IItemHandler source : sources) {
            if (card.isEmpty()) {
                return;
            }

            card = insert(source, card);
        }

        if (!card.isEmpty()) {
            player.dropItem(card, false);
        }
    }

    private static ItemStack insert(final IItemHandler inv, ItemStack card) {
        for (int slot = 0; slot < inv.getSlots() && !card.isEmpty(); slot++) {
            card = inv.insertItem(slot, card, false);
        }

        return card;
    }

    private static boolean isSameCard(final ItemStack a, final ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static IItemHandler findNetworkTool(final EntityPlayer player) {
        for (final ItemStack stack : player.inventory.mainInventory) {
            if (!stack.isEmpty() && stack.getItem() instanceof ToolNetworkTool) {
                return new NetworkToolViewer(stack, null).getInternalInventory();
            }
        }

        return null;
    }

    private static boolean takeBlankPattern(final IItemHandler playerInv, final IMaterials materials) {
        for (int slot = 0; slot < playerInv.getSlots(); slot++) {
            if (materials.blankPattern().isSameAs(playerInv.getStackInSlot(slot))) {
                return !playerInv.extractItem(slot, 1, false).isEmpty();
            }
        }

        return false;
    }
}
