/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.integration.modules.jei;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.api.integrations.hei.IngredientConverter;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

/**
 * Items, which the viewer already speaks natively. Registered like any other type so that the code that
 * reads a slot has one path rather than a special case at the front of it.
 */
public final class ItemIngredientConverter implements IngredientConverter<ItemStack> {

    @Override
    public Class<ItemStack> getIngredientClass() {
        return ItemStack.class;
    }

    @Nullable
    @Override
    public ItemStack getIngredientFromStack(final GenericStack stack) {
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return null;
        }
        return itemKey.toStack((int) Math.max(1, Math.min(stack.amount(), Integer.MAX_VALUE)));
    }

    @Nullable
    @Override
    public GenericStack getStackFromIngredient(final ItemStack ingredient) {
        // Resolves rather than reads: a slot's stack may be the placeholder a non-item key is wrapped in,
        // and then it stands for that key, not for the placeholder item.
        return GenericStack.resolveItemStack(ingredient);
    }
}
