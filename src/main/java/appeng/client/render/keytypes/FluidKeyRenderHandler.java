/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.render.keytypes;


import appeng.api.client.AEKeyModelContext;
import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;


public class FluidKeyRenderHandler implements AEKeyRenderHandler {

    @Nullable
    @Override
    public IBakedModel getModel(final AEKey what, final AEKeyModelContext context) {
        if (!(what instanceof AEFluidKey fluidKey)) {
            return null;
        }

        final FluidStack stack = fluidKey.toStack(Fluid.BUCKET_VOLUME);
        return context.getSpriteModel(stack.getFluid().getStill(stack));
    }

    @Override
    public int getTint(final AEKey what) {
        // Still textures are greyscale and carry their colour here, as they do for a fluid in any other slot.
        return what instanceof AEFluidKey fluidKey ? fluidKey.getFluid().getColor() : AEKeyRendering.NO_TINT;
    }
}
