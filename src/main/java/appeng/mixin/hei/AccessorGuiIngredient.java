/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.mixin.hei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import mezz.jei.gui.ingredients.GuiIngredient;

/**
 * The padding between an ingredient's rect and the icon drawn inside it - 1 for item slots, 0 for fluids.
 * HEI keeps it private and only applies it while drawing, so anything drawing over an icon from outside
 * has to read it to land on the icon rather than on its border.
 */
@Mixin(value = GuiIngredient.class, remap = false)
public interface AccessorGuiIngredient {

    @Accessor("xPadding")
    int getXPadding();

    @Accessor("yPadding")
    int getYPadding();
}
