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

import net.minecraftforge.fluids.FluidStack;

import appeng.api.integrations.hei.IngredientConverter;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;

/**
 * Fluids, the mod's own second key type.
 */
public final class FluidIngredientConverter implements IngredientConverter<FluidStack> {

    @Override
    public Class<FluidStack> getIngredientClass() {
        return FluidStack.class;
    }

    @Nullable
    @Override
    public FluidStack getIngredientFromStack(final GenericStack stack) {
        if (!(stack.what() instanceof AEFluidKey fluidKey)) {
            return null;
        }
        // At least one millibucket: a fluid stack of nothing is one the viewer throws away.
        return fluidKey.toStack((int) Math.max(1, Math.min(stack.amount(), Integer.MAX_VALUE)));
    }

    @Nullable
    @Override
    public GenericStack getStackFromIngredient(final FluidStack ingredient) {
        if (ingredient.amount <= 0) {
            return null;
        }
        return new GenericStack(AEFluidKey.of(ingredient), ingredient.amount);
    }
}
