/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.render;


import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import appeng.items.misc.WrappedGenericStack;


/**
 * Draws a wrapped key whose render handler draws it itself; {@link DrawnKeyModel} is what sends it here.
 */
@SideOnly(Side.CLIENT)
public class WrappedGenericStackItemRenderer extends TileEntityItemStackRenderer {

    @Override
    public void renderByItem(final ItemStack stack, final float partialTicks) {
        final GenericStack wrapped = stack.getItem() instanceof WrappedGenericStack wrapper ? wrapper.unwrap(stack) : null;
        if (wrapped == null) {
            return;
        }

        final AEKeyRenderHandler handler = AEKeyRendering.get(wrapped.what());
        if (handler == null) {
            return;
        }

        handler.draw(wrapped.what());
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
