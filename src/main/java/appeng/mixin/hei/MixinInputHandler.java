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

import mezz.jei.input.InputHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.GuiScreen;

import appeng.integration.modules.jei.JeiIngredientActions;

/**
 * Gives the two network shortcuts the click before HEI decides what to do with it.
 * <p>
 * A Forge listener cannot do this. HEI takes {@code GuiScreenEvent.MouseInputEvent.Pre} at
 * {@code EventPriority.HIGHEST} <em>and</em> with {@code receiveCanceled = true}, so it acts on a click
 * whatever anyone else did with the event first. What it does with a middle click is not nothing either:
 * a screen that registered a ghost ingredient handler - which every terminal in this mod does - starts a
 * ghost drag on it, and a screen without one starts a drag of HEI's own whenever control is held.
 * <p>
 * This is the one method every click in an open screen passes through, and answering true is how HEI's own
 * handlers say a click was theirs.
 */
@Mixin(value = InputHandler.class, remap = false)
public class MixinInputHandler {

    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true)
    private void ae2ud$networkShortcuts(final GuiScreen guiScreen, final int mouseButton, final int mouseX,
            final int mouseY, final CallbackInfoReturnable<Boolean> cir) {
        if (JeiIngredientActions.act(mouseButton - 100)) {
            cir.setReturnValue(true);
        }
    }
}
