/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's storage exposer (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.exposer;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.behaviors.ExposedStorage;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

/**
 * Every item the network holds is a slot of its own.
 */
public final class ItemExposerHandler implements IItemHandler {

    private final ExposedStorage storage;

    public ItemExposerHandler(final ExposedStorage storage) {
        this.storage = storage;
    }

    @Override
    public int getSlots() {
        return this.storage.size();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(final int slot) {
        final AEKey key = this.storage.getKey(slot);
        if (!(key instanceof AEItemKey)) {
            return ItemStack.EMPTY;
        }
        return ((AEItemKey) key).toStack((int) Math.min(this.storage.getAmount(key), Integer.MAX_VALUE));
    }

    @Nonnull
    @Override
    public ItemStack insertItem(final int slot, @Nonnull final ItemStack stack, final boolean simulate) {
        return stack;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        final AEKey key = this.storage.getKey(slot);
        if (!(key instanceof AEItemKey) || amount <= 0) {
            return ItemStack.EMPTY;
        }

        final AEItemKey item = (AEItemKey) key;
        final long taken = this.storage.extract(item, Math.min(amount, item.getMaxStackSize()),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE);
        return taken > 0 ? item.toStack((int) taken) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(final int slot) {
        return Integer.MAX_VALUE;
    }
}
