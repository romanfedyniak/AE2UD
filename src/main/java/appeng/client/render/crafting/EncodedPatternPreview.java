/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.client.render.crafting;


import appeng.api.stacks.GenericStack;
import appeng.client.render.StackSizeRenderer;
import appeng.items.misc.ItemEncodedPattern;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * An encoded pattern draws as the thing it makes while shift is held.
 * <p>
 * What gets swapped is the <em>stack</em>, never the model. Handing the renderer another item's model while
 * leaving the pattern in the stack works only for things drawn out of quads: anything with a renderer of its
 * own - a vanilla shield, a GregTech machine - looks its contents up in the stack it is given, finds a
 * pattern there and draws nothing at all.
 */
@SideOnly(Side.CLIENT)
public final class EncodedPatternPreview {

    private static final StackSizeRenderer STACK_SIZE_RENDERER = new StackSizeRenderer();

    private EncodedPatternPreview() {
    }

    /**
     * @return what the pattern makes and how much of it, or an empty stack if this is not a pattern being
     *         previewed. The output is taken by position: a pattern whose first output is a fluid previews
     *         as that fluid, the same as the interface's own slots show it.
     */
    public static ItemStack previewFor(final ItemStack stack) {
        if (stack.isEmpty() || !GuiScreen.isShiftKeyDown() || !(stack.getItem() instanceof ItemEncodedPattern)) {
            return ItemStack.EMPTY;
        }

        return ((ItemEncodedPattern) stack.getItem()).getOutput(stack);
    }

    /**
     * The amount in AE's own digits, as every AE slot writes it - the slot is drawn holding a single item so
     * that vanilla leaves this corner alone.
     */
    public static void drawAmount(final FontRenderer fontRenderer, final ItemStack output, final int x, final int y) {
        // getOutput wraps the pattern's output key, so a fluid arrives here as a placeholder and has to be
        // resolved rather than read (CONTRACT.md §9.1d).
        STACK_SIZE_RENDERER.renderStackSize(fontRenderer, GenericStack.resolveItemStack(output), x, y);
    }
}
