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

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.client.render.crafting.EncodedPatternPreview;

/**
 * Draws an encoded pattern as its output while shift is held, in any container screen there is.
 * <p>
 * AE's own screens already do this for their pattern slots, by handing the renderer a different stack
 * ({@code AEBaseGui.drawSlot} through {@code SlotRestrictedInput.getDisplayStack}). This is the same trick a
 * level further down, in the method those screens override, so that a vanilla inventory reads alike.
 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainer extends GuiScreen {

    /** What the slot being drawn previews as, carried from the stack swap to the amount that labels it. */
    @Unique
    @Nullable
    private ItemStack ae2ud$previewedOutput;

    @Redirect(method = "drawSlot", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/inventory/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack ae2ud$previewPattern(final Slot slot) {
        final ItemStack stack = slot.getStack();
        final ItemStack output = EncodedPatternPreview.previewFor(stack);

        if (output.isEmpty()) {
            this.ae2ud$previewedOutput = null;
            return stack;
        }

        this.ae2ud$previewedOutput = output;

        // A single item: the amount belongs to AE's own digits below, and vanilla writes its count in the
        // same corner.
        final ItemStack icon = output.copy();
        icon.setCount(1);
        return icon;
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void ae2ud$drawPreviewedAmount(final Slot slot, final CallbackInfo ci) {
        if (this.ae2ud$previewedOutput != null) {
            EncodedPatternPreview.drawAmount(this.fontRenderer, this.ae2ud$previewedOutput, slot.xPos, slot.yPos);
            this.ae2ud$previewedOutput = null;
        }
    }
}
