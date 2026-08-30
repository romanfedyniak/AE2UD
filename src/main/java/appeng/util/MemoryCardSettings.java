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
import appeng.items.misc.ItemEncodedPattern;
import appeng.tile.inventory.AppEngInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;


/**
 * What a memory card carries besides a machine's settings. These are real items rather than numbers, so
 * pasting them costs the player what installing them by hand would have cost.
 */
public final class MemoryCardSettings {

    private MemoryCardSettings() {
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

    private static boolean takeBlankPattern(final IItemHandler playerInv, final IMaterials materials) {
        for (int slot = 0; slot < playerInv.getSlots(); slot++) {
            if (materials.blankPattern().isSameAs(playerInv.getStackInSlot(slot))) {
                return !playerInv.extractItem(slot, 1, false).isEmpty();
            }
        }

        return false;
    }
}
