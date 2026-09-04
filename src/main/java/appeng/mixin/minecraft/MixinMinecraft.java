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

import net.minecraft.client.Minecraft;

import appeng.client.NetworkPickBlock;

/**
 * Lets the pick block key fall through to the network once vanilla has had it.
 * <p>
 * The tail is the only place this can go. Forge has no event for picking a block - it replaced the body of
 * this method with a call to {@code ForgeHooks.onPickBlock} - and the input events that carry the key are
 * fired before that call rather than after, so a listener on one of those would have to take the click away
 * from vanilla instead of following it.
 * <p>
 * Nothing is shadowed: the crosshair and the player are public on {@link Minecraft}, and read from ordinary
 * mod code they need no remapping of their own.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "middleClickMouse", at = @At("TAIL"))
    private void ae2ud$networkPickBlock(final CallbackInfo ci) {
        NetworkPickBlock.onPickBlock();
    }
}
