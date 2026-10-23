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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.migration.LegacyRewrites;

/**
 * A stack saved by UEL or its addons is rewritten before it is read. Off a world from UEL this is one field read.
 */
@Mixin(ItemStack.class)
public class MixinItemStack {

    @Inject(method = "<init>(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("CTOR_HEAD"))
    private void ae2ud$rewriteLegacy(final NBTTagCompound compound, final CallbackInfo ci) {
        LegacyRewrites.rewriteStack(compound);
    }
}
