/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.recipes.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.IForgeRegistryEntry;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTerminalMode;
import appeng.helpers.WirelessTerminalModeRemoval;
import appeng.helpers.WirelessTerminalModes;

/**
 * Takes one mode back out of a wireless terminal: the terminal alone in the grid, showing that mode, gives the
 * mode's terminal part, and the terminal stays in the grid without it.
 *
 * <p>Items the mode kept go to the player who crafted it. Crafted by a machine, there is nobody to give them
 * to, so they stay on the terminal and come back with the mode.</p>
 */
public final class WirelessTerminalModeRemovalRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    private final IWirelessTerminalMode mode;

    public WirelessTerminalModeRemovalRecipe(final IWirelessTerminalMode mode) {
        this.mode = mode;
    }

    @Override
    public boolean matches(final InventoryCrafting inv, final World world) {
        return this.findTerminal(inv) >= 0;
    }

    @Override
    public ItemStack getCraftingResult(final InventoryCrafting inv) {
        return this.findTerminal(inv) >= 0 ? this.getRecipeOutput() : ItemStack.EMPTY;
    }

    /** The slot holding a terminal this recipe can take the mode out of, or -1. */
    private int findTerminal(final InventoryCrafting inv) {
        if (this.mode.getUnlockIngredient().isEmpty()) {
            return -1;
        }

        int found = -1;
        for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
            final ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (found >= 0 || !isTerminal(stack)) {
                return -1;
            }
            found = slot;
        }

        if (found < 0) {
            return -1;
        }

        final ItemStack terminal = inv.getStackInSlot(found);
        final IWirelessTerminalMode active = WirelessTerminalModes.getActiveMode(terminal);
        return active == this.mode && WirelessTerminalModeRemoval.canTakeOut(terminal, this.mode.getId())
                ? found
                : -1;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(final InventoryCrafting inv) {
        final NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        final int slot = this.findTerminal(inv);
        if (slot < 0) {
            return remaining;
        }

        final EntityPlayer player = ForgeHooks.getCraftingPlayer();
        final boolean handOut = player != null && !(player instanceof FakePlayer);
        final List<ItemStack> items = new ArrayList<>();

        remaining.set(slot, WirelessTerminalModeRemoval.takeOut(inv.getStackInSlot(slot), this.mode.getId(),
                handOut ? items : null));

        // The client takes the mode out too, to predict the grid, but only the server hands anything over
        if (handOut && !player.world.isRemote) {
            for (final ItemStack item : items) {
                ItemHandlerHelper.giveItemToPlayer(player, item);
            }
        }

        return remaining;
    }

    private static boolean isTerminal(final ItemStack stack) {
        return AEApi.instance().definitions().items().wirelessTerminal().isSameAs(stack);
    }

    public IWirelessTerminalMode getMode() {
        return this.mode;
    }

    /**
     * A terminal showing this mode beside one more, which is the least a terminal needs for the recipe to
     * take anything out.
     */
    public ItemStack getTerminal() {
        final ItemStack shown = AEApi.instance().definitions().items().wirelessTerminal().maybeStack(1)
                .orElse(ItemStack.EMPTY);
        if (shown.isEmpty()) {
            return shown;
        }

        ResourceLocation other = null;
        for (final IWirelessTerminalMode registered : AEApi.instance().registries().wirelessTerminalModes()
                .getModes()) {
            if (registered != this.mode) {
                other = registered.getId();
                break;
            }
        }

        WirelessTerminalModes.setUnlocked(shown, other == null
                ? Arrays.asList(this.mode.getId())
                : Arrays.asList(other, this.mode.getId()));
        WirelessTerminalModes.setModeId(shown, this.mode.getId());
        return shown;
    }

    @Override
    public boolean canFit(final int width, final int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getRecipeOutput() {
        final ItemStack part = this.mode.getUnlockIngredient().copy();
        part.setCount(1);
        return part;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.from(Ingredient.EMPTY, Ingredient.fromStacks(this.getTerminal()));
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
