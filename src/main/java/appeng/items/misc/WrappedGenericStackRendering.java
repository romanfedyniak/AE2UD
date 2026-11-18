/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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


import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import appeng.bootstrap.IItemRendering;
import appeng.bootstrap.ItemRenderingCustomizer;
import appeng.client.render.WrappedGenericStackModel;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;


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
        rendering.color((s, i) -> tintOf(s));
    }

    // Asked once per quad of the icon: the stack's key is read from its tag once per stack drawn, not per quad.
    private static ItemStack lastStack;
    private static NBTTagCompound lastTag;
    private static int lastTint;

    private static int tintOf(final ItemStack s) {
        final NBTTagCompound tag = s.getTagCompound();
        if (s == lastStack && tag == lastTag) {
            return lastTint;
        }

        final GenericStack wrapped = s.getItem() instanceof WrappedGenericStack wrapper ? wrapper.unwrap(s) : null;
        final int tint = wrapped == null ? AEKeyRendering.NO_TINT : AEKeyRendering.getTint(wrapped.what());
        lastStack = s;
        lastTag = tag;
        lastTint = tint;
        return tint;
    }
}
