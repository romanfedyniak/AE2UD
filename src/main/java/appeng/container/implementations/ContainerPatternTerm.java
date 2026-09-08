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

package appeng.container.implementations;


import appeng.api.storage.ITerminalHost;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotFakeProcessingGrid;
import appeng.container.slot.SlotPatternOutputs;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.util.Platform;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.items.IItemHandler;

import static appeng.helpers.PatternHelper.CRAFTING_GRID_DIMENSION;
import static appeng.helpers.PatternHelper.PROCESSING_INPUT_LIMIT;
import static appeng.helpers.PatternHelper.PROCESSING_OUTPUT_LIMIT;


public class ContainerPatternTerm extends ContainerPatternEncoder {


    public ContainerPatternTerm(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable, false);

        this.craftingSlots = new SlotFakeCraftingMatrix[CRAFTING_GRID_DIMENSION * CRAFTING_GRID_DIMENSION];
        this.processingSlots = new SlotFakeProcessingGrid[PROCESSING_INPUT_LIMIT];
        this.outputSlots = new OptionalSlotFake[PROCESSING_OUTPUT_LIMIT];

        final IItemHandler patternInv = this.getPart().getInventoryByName("pattern");
        final IItemHandler output = this.getPart().getInventoryByName("output");

        this.crafting = this.getPart().getInventoryByName("crafting");
        this.processing = this.getPart().getInventoryByName("processing");

        for (int y = 0; y < CRAFTING_GRID_DIMENSION; y++) {
            for (int x = 0; x < CRAFTING_GRID_DIMENSION; x++) {
                final int idx = x + y * CRAFTING_GRID_DIMENSION;
                this.addSlotToContainer(this.craftingSlots[idx] = new SlotFakeCraftingMatrix(this.crafting, idx, 18 + x * 18, -76 + y * 18));
            }
        }

        this.addSlotToContainer(this.craftSlot = new SlotPatternTerm(ip.player, this.getActionSource(), this
                .getPowerSource(), monitorable, this.crafting, patternInv, this.cOut, 110, -76 + 18, this, 2, this));
        this.craftSlot.setIIcon(-1);

        // The processing grids go in at the origin: updateSlotVisibility owns their positions from here
        // on, and moves them whenever the page or the orientation changes.
        for (int i = 0; i < PROCESSING_INPUT_LIMIT; i++) {
            this.addSlotToContainer(this.processingSlots[i] = new SlotFakeProcessingGrid(this.processing, i, 0, 0));
        }

        for (int i = 0; i < PROCESSING_OUTPUT_LIMIT; i++) {
            this.addSlotToContainer(this.outputSlots[i] = new SlotPatternOutputs(output, this, i, 0, 0, 0, 0, 1));
            this.outputSlots[i].setRenderDisabled(false);
            this.outputSlots[i].setIIcon(-1);
        }

        this.addSlotToContainer(
                this.patternSlotIN = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN, patternInv, 0, 147, -72 - 9, this
                        .getInventoryPlayer()));
        this.addSlotToContainer(
                this.patternSlotOUT = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN, patternInv, 1, 147, -72 + 34, this
                        .getInventoryPlayer()));

        this.patternSlotOUT.setStackLimit(1);

        this.bindPlayerInventory(ip, 0, 0);
        this.updateSlotVisibility();
    }


    @Override
    public boolean isSlotEnabled(final int idx) {
        if (idx == 1) {
            return Platform.isServer() ? !this.getPart().isCraftingRecipe() : !this.isCraftingMode();
        } else if (idx == 2) {
            return Platform.isServer() ? this.getPart().isCraftingRecipe() : this.isCraftingMode();
        } else {
            return false;
        }
    }

}
