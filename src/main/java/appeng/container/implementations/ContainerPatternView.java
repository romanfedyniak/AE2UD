/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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


import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotInaccessible;
import appeng.tile.inventory.AppEngInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

import java.util.List;


/**
 * The read-only view of one encoded pattern, opened over whatever screen the player was already in.
 * <p>
 * It never reaches a server. Everything shown is decoded from the pattern's own NBT, which the client
 * already has - the stack is the one under the cursor - so there is nothing to ask for and nothing to keep
 * in sync. What the container is for is slots: HEI reads recipes out of {@code getSlotUnderMouse()}, and
 * {@link appeng.client.gui.AEBaseGui} draws amounts for an {@code AppEngSlot} on its own, fluids included.
 * <p>
 * Ingredients sit to the left of an arrow and results to its right, the way a recipe is read everywhere
 * else.
 */
public class ContainerPatternView extends AEBaseContainer {

    private static final int SLOT_SIZE = 18;

    /** Left and right margin, and the height above the header that the title sits in. */
    public static final int MARGIN = 8;
    public static final int HEADER_TOP = 18;
    public static final int ICON_SIZE = 16;
    public static final int ICON_GAP = 4;

    public static final int ARROW_WIDTH = 22;
    public static final int ARROW_HEIGHT = 15;
    /** Air either side of the arrow, so it is not wedged against the two grids. */
    public static final int ARROW_MARGIN = 5;

    /**
     * No row is longer than this. Eight keeps the widest pattern this version can encode - 32 inputs -
     * inside a reasonable window, so nothing has to page or scroll at any size.
     */
    private static final int MAX_COLUMNS = 8;

    private final boolean hasHeader;
    private final int inputSlots;
    private final int inputColumns;
    private final int inputRows;
    private final int outputColumns;
    private final int outputRows;

    public ContainerPatternView(final InventoryPlayer ip, final ICraftingPatternDetails details,
            final List<GenericStack> inputs, final GenericStack[] outputs) {
        super(ip, null, null);

        // A crafting pattern is a recipe shape: its slots have to stay where they were encoded, gaps and
        // all. A processing pattern is a bag of ingredients, so it packs instead.
        final boolean keepShape = details.isCraftable();

        // Only a crafting pattern substitutes, so only it has anything to say about it - which is the same
        // rule the pattern tooltip follows.
        this.hasHeader = keepShape;

        final GenericStack[] laidOut = keepShape ? details.getInputs() : inputs.toArray(new GenericStack[0]);

        this.inputColumns = keepShape ? squareSide(laidOut.length) : columnsFor(laidOut.length);
        this.inputRows = rowsFor(laidOut.length, this.inputColumns);
        this.outputColumns = columnsFor(outputs.length);
        this.outputRows = rowsFor(outputs.length, this.outputColumns);

        final int contentRows = Math.max(this.inputRows, this.outputRows);

        this.inputSlots = laidOut.length;

        this.addGrid(laidOut, this.inputColumns, MARGIN, this.rowTop(contentRows, this.inputRows));
        this.addGrid(outputs, this.outputColumns, this.getOutputLeft(),
                this.rowTop(contentRows, this.outputRows));
    }

    /**
     * The side of the square a shaped recipe was encoded on, taken from the slot count rather than assumed
     * to be three - an addon pattern on a bigger bench lays itself out correctly without anything here
     * knowing about it.
     */
    private static int squareSide(final int slots) {
        final int side = (int) Math.round(Math.sqrt(slots));
        return Math.max(1, side * side == slots ? side : side + 1);
    }

    /**
     * How wide to lay an unshaped group out. Fewest rows first, and among the widths that reach that many
     * rows, the one leaving the fewest empty cells - so ten ingredients read as five and five rather than
     * eight and a stub with the rest of the row standing empty.
     */
    private static int columnsFor(final int count) {
        int best = 1;
        int bestRows = Math.max(1, count);
        int bestWaste = 0;

        for (int columns = 1; columns <= Math.min(MAX_COLUMNS, count); columns++) {
            final int rows = rowsFor(count, columns);
            final int waste = columns * rows - count;

            if (rows < bestRows || (rows == bestRows && waste < bestWaste)) {
                best = columns;
                bestRows = rows;
                bestWaste = waste;
            }
        }

        return best;
    }

    private static int rowsFor(final int count, final int columns) {
        return Math.max(1, (count + columns - 1) / columns);
    }

    /** The shorter of the two grids sits centred against the taller one. */
    private int rowTop(final int contentRows, final int rows) {
        return this.getContentTop() + (contentRows - rows) * SLOT_SIZE / 2;
    }

    private void addGrid(final GenericStack[] stacks, final int columns, final int left, final int top) {
        final AppEngInternalInventory inv = new AppEngInternalInventory(null, Math.max(1, stacks.length));

        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] == null) {
                continue;
            }

            // Wrapped with its amount, not bare: AEBaseGui resolves the slot's stack back into a key and
            // an amount to draw the number, so a bucket in a pattern reads "1000" rather than "1".
            inv.setStackInSlot(i, GenericStack.wrapInItemStack(stacks[i]));
        }

        for (int i = 0; i < stacks.length; i++) {
            this.addSlotToContainer(new SlotInaccessible(inv, i,
                    left + (i % columns) * SLOT_SIZE, top + (i / columns) * SLOT_SIZE));
        }
    }

    /** Where the grids start, once the header icons above them have had their room. */
    public int getContentTop() {
        return HEADER_TOP + (this.hasHeader ? ICON_SIZE + ICON_GAP : 0);
    }

    public int getArrowLeft() {
        return MARGIN + this.inputColumns * SLOT_SIZE + ARROW_MARGIN;
    }

    public int getOutputLeft() {
        return this.getArrowLeft() + ARROW_WIDTH + ARROW_MARGIN;
    }

    /** The arrow reads as pointing from one grid to the other, so it sits level with both. */
    public int getArrowTop() {
        return this.getContentTop()
                + (Math.max(this.inputRows, this.outputRows) * SLOT_SIZE - ARROW_HEIGHT) / 2;
    }

    public int getWidth() {
        return this.getOutputLeft() + this.outputColumns * SLOT_SIZE + MARGIN;
    }

    public int getHeight() {
        return this.getContentTop() + Math.max(this.inputRows, this.outputRows) * SLOT_SIZE + MARGIN;
    }

    /** The inputs are added first, so the slots below this are the ones an ingredient sits in. */
    public int getInputSlots() {
        return this.inputSlots;
    }

    public boolean hasHeader() {
        return this.hasHeader;
    }

    /**
     * Nothing was ever opened on the server, so nothing may be closed there either - the screen this one was
     * summoned over is still the player's real container and has to be left exactly as it was found.
     */
    @Override
    public void onContainerClosed(final EntityPlayer player) {
    }

    @Override
    public boolean canInteractWith(final EntityPlayer player) {
        return true;
    }
}
