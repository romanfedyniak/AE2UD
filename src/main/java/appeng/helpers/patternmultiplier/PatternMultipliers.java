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

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

import appeng.items.tools.ToolPatternMultiplier;

/**
 * Finding the pattern multi-tool a player carries.
 */
public final class PatternMultipliers {

    private PatternMultipliers() {
    }

    /**
     * The first one in a bauble slot, else the first in the inventory; empty when there is none.
     */
    public static ItemStack carriedBy(final EntityPlayer player) {
        if (Loader.isModLoaded("baubles")) {
            final ItemStack worn = firstInBaubles(player);
            if (!worn.isEmpty()) {
                return worn;
            }
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            final ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.getItem() instanceof ToolPatternMultiplier) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Whether this very stack is in one of the player's bauble slots.
     */
    public static boolean inBaubles(final EntityPlayer player, final ItemStack stack) {
        return Loader.isModLoaded("baubles") && isWorn(player, stack);
    }

    private static boolean isWorn(final EntityPlayer player, final ItemStack stack) {
        final IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < baubles.getSlots(); i++) {
            if (baubles.getStackInSlot(i) == stack) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack firstInBaubles(final EntityPlayer player) {
        final IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < baubles.getSlots(); i++) {
            final ItemStack stack = baubles.getStackInSlot(i);
            if (stack.getItem() instanceof ToolPatternMultiplier) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
