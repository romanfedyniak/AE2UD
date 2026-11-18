/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.mixin.minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.item.ItemStack;

import appeng.client.render.crafting.EncodedPatternPreview;

/**
 * The pattern preview for a pattern lying on the ground. The ground renderer resolves the model itself and draws
 * through {@code renderItem(ItemStack, IBakedModel)}, which {@link MixinRenderItem} does not reach, so the stack
 * it read from the entity is swapped for the output: model, tint and glint all come from that.
 */
@Mixin(RenderEntityItem.class)
public class MixinRenderEntityItem {

    @ModifyVariable(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("STORE"), ordinal = 0)
    private ItemStack ae2ud$previewOnGround(final ItemStack stack) {
        final ItemStack output = EncodedPatternPreview.previewFor(stack);
        return output.isEmpty() ? stack : output;
    }
}
