/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's pattern multi-tool (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.patternmultiplier;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javax.annotation.Nullable;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.container.slot.OptionalSlotRestrictedInput;
import appeng.container.slot.SlotRestrictedInput;

/**
 * The pattern multi-tool a player carries, beside a screen that holds patterns: its rows stand as columns to the
 * left of the window, and patterns move between the two by hand.
 */
public final class PatternMultiplierPanel {

    public static final int WIDTH = 86;
    public static final int HEIGHT = 198;
    /** Where the panel stands, from the window's left edge: past the column of buttons there. */
    public static final int LEFT = -18 - 2 - WIDTH;
    public static final int SLOTS_INSET = 8;

    private PatternMultiplierPanel() {
    }

    /**
     * Adds the carried tool's pattern slots, the tool's own slot locked, or nothing when none is carried.
     *
     * @return the tool, or null
     */
    @Nullable
    public static PatternMultiplierHost attach(final InventoryPlayer inventory, final int top,
            final Consumer<Slot> addSlot, final IntConsumer lockSlot) {
        final ItemStack tool = PatternMultipliers.carriedBy(inventory.player);
        if (tool.isEmpty()) {
            return null;
        }
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (inventory.getStackInSlot(i) == tool) {
                lockSlot.accept(i);
            }
        }
        final PatternMultiplierHost host = new PatternMultiplierHost(tool, null);
        for (int row = 0; row < PatternMultiplierHost.ROWS; row++) {
            for (int column = 0; column < PatternMultiplierHost.COLUMNS; column++) {
                // The tool's rows of nine are the panel's columns.
                final int x = LEFT + SLOTS_INSET + row * 18;
                // A pixel below the inset, where the texture's wells are.
                final int y = top + SLOTS_INSET + 1 + column * 18;
                final Slot slot = new PanelSlot(host, row * PatternMultiplierHost.COLUMNS + column, x, y, row,
                        inventory).setPlayerSide();
                addSlot.accept(slot);
            }
        }
        return host;
    }

    /**
     * A slot of the panel. Screens that place their slots by the window's height leave these where they are.
     */
    public static final class PanelSlot extends OptionalSlotRestrictedInput {
        private boolean hidden;

        private PanelSlot(final PatternMultiplierHost host, final int index, final int x, final int y,
                final int row, final InventoryPlayer inventory) {
            super(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN, host.getPatterns(),
                    group -> group < host.getRows(), index, x, y, row, inventory);
        }

        /**
         * Put out of the way on the screen while another panel opens over the same place.
         */
        public void setHidden(final boolean hidden) {
            this.hidden = hidden;
        }

        @Override
        public boolean isSlotEnabled() {
            return !this.hidden && super.isSlotEnabled();
        }
    }
}
