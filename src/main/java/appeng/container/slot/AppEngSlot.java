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

package appeng.container.slot;


import appeng.api.stacks.GenericStack;
import appeng.container.AEBaseContainer;
import appeng.util.helpers.ItemHandlerUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;


public class AppEngSlot extends Slot {
    private static final IInventory emptyInventory = new InventoryBasic("[Null]", true, 0);
    private final IItemHandler itemHandler;
    private final int index;

    private int defX;
    private int defY;
    private boolean hidden = false;
    private boolean isDraggable = true;
    private boolean isPlayerSide = false;
    private AEBaseContainer myContainer = null;
    private int IIcon = -1;
    private hasCalculatedValidness isValid;
    private boolean isDisplay = false;
    private boolean returnAsSingleStack;
    /** A stand-in drawn in place of the slot's stack, and its copy at a count of one. */
    private ItemStack singleFrom = ItemStack.EMPTY;
    private ItemStack single = ItemStack.EMPTY;
    /** What the drawn stack stands for, and the stack it was worked out from. */
    @Nullable
    private GenericStack resolved;
    private Item resolvedItem;
    private int resolvedDamage;
    private int resolvedCount;
    @Nullable
    private NBTTagCompound resolvedTag;
    /** Where the slot was built, which {@link #restoreHome()} puts it back to. */
    private final int homeX;
    private final int homeY;
    private Supplier<String> emptyTooltip;
    private IntSupplier stackLimitCap;

    public AppEngSlot(final IItemHandler inv, final int idx, final int x, final int y) {
        super(emptyInventory, idx, x, y);
        this.itemHandler = inv;
        this.index = idx;

        this.defX = x;
        this.defY = y;
        this.homeX = x;
        this.homeY = y;
        this.setIsValid(hasCalculatedValidness.NotAvailable);
    }

    public Slot setNotDraggable() {
        this.setDraggable(false);
        return this;
    }

    public Slot setPlayerSide() {
        this.isPlayerSide = true;
        return this;
    }

    /**
     * What to say about this slot while nothing is in it - which side it can be automated from, say. A slot
     * that holds something says nothing, because the item's own tooltip is there instead.
     */
    public String getTooltip() {
        return this.emptyTooltip == null ? null : this.emptyTooltip.get();
    }

    public AppEngSlot setEmptyTooltip(final Supplier<String> emptyTooltip) {
        this.emptyTooltip = emptyTooltip;
        return this;
    }

    public void clearStack() {
        ItemHandlerUtil.setStackInSlot(this.itemHandler, this.index, ItemStack.EMPTY);
    }

    @Override
    public boolean isItemValid(@Nonnull final ItemStack par1ItemStack) {
        if (this.isSlotEnabled()) {
            return this.itemHandler.isItemValid(this.index, par1ItemStack);
        }
        return false;
    }

    @Override
    @Nonnull
    public ItemStack getStack() {
        if (!this.isSlotEnabled()) {
            return ItemStack.EMPTY;
        }

        if (this.itemHandler.getSlots() <= this.getSlotIndex()) {
            return ItemStack.EMPTY;
        }


        if (this.isDisplay()) {
            this.setDisplay(false);
            if (this.returnAsSingleStack()) {
                setReturnAsSingleStack(false);
                return this.singleOf(this.getDisplayStack());
            }
            return this.getDisplayStack();
        }

        return this.itemHandler.getStackInSlot(this.index);
    }

    /**
     * The display stack at a count of one, for drawing, which asks every frame. A stack of one is drawn as it is.
     * A stand-in the slot draws in place of its stack (a pattern's output) does not change while it is shown, so
     * its copy is kept; the slot's own stack is copied, as it may have changed in place.
     */
    private ItemStack singleOf(final ItemStack display) {
        if (display.getCount() == 1) {
            return display;
        }
        if (display == this.singleFrom) {
            return this.single;
        }
        final ItemStack copy = display.copy();
        copy.setCount(1);
        if (display != this.itemHandler.getStackInSlot(this.index)) {
            this.singleFrom = display;
            this.single = copy;
        }
        return copy;
    }

    /**
     * What the stack drawn in this slot stands for, for its amount and its craftable mark. Drawing asks every
     * frame; the key (a copy of the tag, the capabilities written out) is built again only when the stack changed.
     */
    @Nullable
    public GenericStack resolveForDisplay(final ItemStack stack) {
        // Compared by what the stack holds, not by which stack it is: a filter inventory hands out a new
        // placeholder on every read.
        if (stack.getItem() != this.resolvedItem || stack.getMetadata() != this.resolvedDamage
                || stack.getCount() != this.resolvedCount
                || !Objects.equals(stack.getTagCompound(), this.resolvedTag)) {
            this.resolved = GenericStack.resolveItemStack(stack);
            this.resolvedItem = stack.getItem();
            this.resolvedDamage = stack.getMetadata();
            this.resolvedCount = stack.getCount();
            this.resolvedTag = stack.getTagCompound() == null ? null : stack.getTagCompound().copy();
        }
        return this.resolved;
    }

    private boolean returnAsSingleStack() {
        return this.returnAsSingleStack;
    }

    public void setReturnAsSingleStack(boolean returnAsSingleStack) {
        this.returnAsSingleStack = returnAsSingleStack;
    }

    /**
     * Note there is no {@link #isSlotEnabled()} test here. This is also the client's sync entry point - a
     * disabled slot has to accept what the server says it holds, or it keeps showing what it held before it
     * was switched off. Refusing a *click* on a disabled slot is the caller's job: for a real slot
     * {@link #isItemValid} and {@link #canTakeStack} already answer false, and filter slots are gated in
     * {@code AEBaseContainer.doAction}.
     */
    @Override
    public void putStack(final ItemStack stack) {
        ItemHandlerUtil.setStackInSlot(this.itemHandler, this.index, stack);

        if (this.getContainer() != null) {
            this.getContainer().onSlotChange(this);
        }
    }

    public IItemHandler getItemHandler() {
        return this.itemHandler;
    }

    @Override
    public void onSlotChanged() {
        this.setIsValid(hasCalculatedValidness.NotAvailable);
        if (this.isSlotEnabled() && this.itemHandler != null) {
            ItemHandlerUtil.setStackInSlot(this.itemHandler, this.index, this.getStack().copy());

            if (this.getContainer() != null) {
                this.getContainer().onSlotChange(this);
            }
        }
        super.onSlotChanged();
    }

    @Override
    public int getSlotStackLimit() {
        final int limit = this.itemHandler.getSlotLimit(this.index);
        return this.stackLimitCap == null ? limit : Math.min(limit, this.stackLimitCap.getAsInt());
    }

    /**
     * A second ceiling on the slot, for one whose real limit the client cannot see. The inventory behind a
     * slot is the tile's own, and a tile does not send its settings to the client - so where a machine
     * decides how much a slot holds, the client reads whatever the inventory was built with and predicts a
     * click wrongly. Both are consulted, and the lower wins, which is right on either side.
     */
    public AppEngSlot setStackLimitCap(final IntSupplier stackLimitCap) {
        this.stackLimitCap = stackLimitCap;
        return this;
    }

    @Override
    public int getItemStackLimit(@Nonnull ItemStack stack) {
        return Math.min(this.getSlotStackLimit(), stack.getMaxStackSize());
    }

    @Override
    public boolean canTakeStack(final EntityPlayer par1EntityPlayer) {
        if (this.isSlotEnabled()) {
            var draggedStack = par1EntityPlayer.inventory.getItemStack();
            ItemStack slotStack = this.getStack();


            if (!draggedStack.isEmpty()) {
                if (draggedStack.isItemEqual(slotStack)) {
                    if (draggedStack.getCount() >= draggedStack.getMaxStackSize()) {
                        // Prevent over-pulling.
                        return false;
                    }
                } else if (slotStack.getCount() > slotStack.getMaxStackSize()) {
                    // Prevent swapping when clicking slots that hold more items than the max stack size.
                    return false;
                }
            }
        }

        if (this.isSlotEnabled()) {
            return !this.itemHandler.extractItem(this.index, Integer.MAX_VALUE, true).isEmpty();
        }
        return false;
    }

    @Override
    @Nonnull
    public ItemStack decrStackSize(int amount) {
        return this.itemHandler.extractItem(this.index, amount, false);
    }

    @Override
    public boolean isSameInventory(Slot other) {
        return other instanceof AppEngSlot && ((AppEngSlot) other).itemHandler == this.itemHandler;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isEnabled() {
        return this.isSlotEnabled() && !this.hidden;
    }

    public boolean isSlotEnabled() {
        return true;
    }

    /**
     * Deliberately not {@link #isSlotEnabled()}: a disabled slot is one the terminal has no use for, and
     * {@link OptionalSlotFake} empties it, while a hidden one is merely off-screen for now - the pattern
     * terminal's second page of ingredients has to survive being scrolled away from.
     */
    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(final boolean hidden) {
        this.hidden = hidden;
    }

    public ItemStack getDisplayStack() {
        return this.itemHandler.getStackInSlot(this.index);
    }

    public float getOpacityOfIcon() {
        return 0.4f;
    }

    public boolean renderIconWithItem() {
        return false;
    }

    public int getIcon() {
        return this.getIIcon();
    }

    public boolean isPlayerSide() {
        return this.isPlayerSide;
    }

    public boolean shouldDisplay() {
        return this.isSlotEnabled();
    }

    public int getX() {
        return this.defX;
    }

    public int getY() {
        return this.defY;
    }

    /**
     * Moves the slot's home position. The screen re-derives {@link #xPos}/{@link #yPos} from it, so a
     * caller that moves a slot while the screen is open has to reposition it as well.
     */
    public void setX(final int x) {
        this.defX = x;
    }

    public void setY(final int y) {
        this.defY = y;
    }

    /**
     * Puts the slot back where it was built. A screen whose layout is decided by something that comes and
     * goes - a pattern terminal's mode panel - restores its slots before asking, so that what one panel
     * moved is not still moved under the next.
     */
    public void restoreHome() {
        this.defX = this.homeX;
        this.defY = this.homeY;
    }

    private int getIIcon() {
        return this.IIcon;
    }

    public void setIIcon(final int iIcon) {
        this.IIcon = iIcon;
    }

    private boolean isDisplay() {
        return this.isDisplay;
    }

    public void setDisplay(final boolean isDisplay) {
        this.isDisplay = isDisplay;
    }

    public boolean isDraggable() {
        return this.isDraggable;
    }

    private void setDraggable(final boolean isDraggable) {
        this.isDraggable = isDraggable;
    }

    void setPlayerSide(final boolean isPlayerSide) {
        this.isPlayerSide = isPlayerSide;
    }

    public hasCalculatedValidness getIsValid() {
        return this.isValid;
    }

    public void setIsValid(final hasCalculatedValidness isValid) {
        this.isValid = isValid;
    }

    protected AEBaseContainer getContainer() {
        return this.myContainer;
    }

    public void setContainer(final AEBaseContainer myContainer) {
        this.myContainer = myContainer;
    }

    public enum hasCalculatedValidness {
        NotAvailable, Valid, Invalid
    }
}
