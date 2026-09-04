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

import java.util.List;

import mezz.jei.render.IngredientRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import appeng.integration.modules.jei.JeiIngredientActions;

/**
 * Says what the network shortcuts would do, under the ingredient they would act on.
 * <p>
 * {@code ItemTooltipEvent} cannot carry this. It is fired by {@code ItemStack.getTooltip}, so a fluid in
 * HEI's list - which can be ordered from the network like anything else - never reaches it, and the lines
 * were missing on exactly the ingredients the craft shortcut is most interesting for. Forge's
 * {@code RenderTooltipEvent} arrives late enough to see every ingredient but hands out its lines through
 * {@code Collections.unmodifiableList}, on purpose.
 * <p>
 * This is the one call that paints a list ingredient's tooltip, whatever type it is.
 */
@Mixin(value = IngredientRenderer.class, remap = false)
public class MixinIngredientRenderer {

    @ModifyArg(method = "drawTooltip(Lnet/minecraft/client/Minecraft;IILjava/util/List;)V",
            at = @At(value = "INVOKE",
                    target = "Lmezz/jei/gui/TooltipRenderer;drawHoveringText(Lnet/minecraft/item/ItemStack;"
                            + "Lnet/minecraft/client/Minecraft;Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V"),
            index = 2)
    private List<String> ae2ud$networkShortcutHints(final List<String> tooltip) {
        return JeiIngredientActions.withHints(tooltip);
    }
}
