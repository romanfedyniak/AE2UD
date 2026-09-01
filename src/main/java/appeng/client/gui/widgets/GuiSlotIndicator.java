/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui.widgets;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;

import appeng.client.gui.AEBaseGui;
import appeng.container.slot.SlotRestrictedInput;
import appeng.util.ItemToggle;

/**
 * The lamp in the corner of a slot holding something that can be switched off, so its state is read off the
 * screen rather than out of a tooltip. Red for off, green for on, and nothing else: it is not clicked, and
 * the item under it is still switched by right clicking the slot.
 *
 * <p>Drawn from the foreground, over both the slot and the item in it, in the corner the stack size never
 * uses.</p>
 */
public final class GuiSlotIndicator {

    private static final int SHEET = 16;
    private static final int SIZE = 5;
    private static final int LIT = 8;

    /** Flush into the slot's top right corner, opposite the stack size. */
    private static final int OFFSET_X = 16 - SIZE;
    private static final int OFFSET_Y = 0;

    private GuiSlotIndicator() {
    }

    public static void draw(final AEBaseGui gui) {
        boolean bound = false;

        for (final Slot slot : gui.inventorySlots.inventorySlots) {
            if (!(slot instanceof SlotRestrictedInput) || !slot.isEnabled()
                    || !ItemToggle.isSwitchable(slot.getStack())) {
                continue;
            }

            if (!bound) {
                // An item has been drawn under this, which leaves lighting on and the sprite's own corners
                // filled in black if blending is not asked for again.
                GlStateManager.disableLighting();
                GlStateManager.disableDepth();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                AEBaseGui.enableSpriteBlending();
                gui.bindTexture("guis/indicator.png");
                bound = true;
            }

            Gui.drawModalRectWithCustomSizedTexture(slot.xPos + OFFSET_X, slot.yPos + OFFSET_Y,
                    ItemToggle.isEnabled(slot.getStack()) ? LIT : 0, 0, SIZE, SIZE, SHEET, SHEET);
        }
    }
}
