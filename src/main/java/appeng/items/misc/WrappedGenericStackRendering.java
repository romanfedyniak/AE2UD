/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.items.misc;


import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.bootstrap.IItemRendering;
import appeng.bootstrap.ItemRenderingCustomizer;
import appeng.client.render.WrappedGenericStackModel;


/**
 * The client-side half of {@link WrappedGenericStack}, mirroring {@code FluidDummyItemRendering}.
 * <p>
 * Without it the item has no model, and Forge logs a missing-model error for
 * {@code appliedenergistics2:wrapped_generic_stack#inventory} at every startup while every wrapped key renders
 * as the purple-and-black checker.
 */
public class WrappedGenericStackRendering extends ItemRenderingCustomizer {
    @Override
    public void customize(IItemRendering rendering) {
        rendering.builtInModel("models/item/wrapped_generic_stack", new WrappedGenericStackModel());
        rendering.color((s, i) -> {
            // Fluid still textures are greyscale and carry their colour here, as they do for FluidDummyItem.
            // Everything else is drawn with a model that already knows its own colours, so it must not be
            // tinted: white is the identity multiplier.
            final GenericStack wrapped = s.getItem() instanceof WrappedGenericStack wrapper ? wrapper.unwrap(s) : null;
            if (wrapped != null && wrapped.what() instanceof AEFluidKey fluidKey) {
                return fluidKey.getFluid().getColor();
            }
            return 0xFFFFFFFF;
        });
    }
}
