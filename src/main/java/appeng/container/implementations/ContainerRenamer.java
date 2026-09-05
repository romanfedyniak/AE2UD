/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.container.implementations;

import appeng.api.config.SecurityPermissions;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotFakeTypeOnly;
import appeng.helpers.ICustomIconObject;
import appeng.helpers.ICustomNameObject;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

public class ContainerRenamer extends AEBaseContainer implements IAEAppEngInventory {

    public static final int ICON_X = 9;
    public static final int ICON_Y = 31;
    private static final int PLAYER_INV_X = 39;
    private static final int PLAYER_INV_Y = 68;

    private static final int DETECT_INTERVAL = 10;

    private static final int CHOSEN = 0;
    private static final int DETECTED = 1;

    private final ICustomNameObject namedObject;
    private final ICustomIconObject iconObject;
    private final AppEngInternalInventory icons = new AppEngInternalInventory(this, 2, 1);

    /** Until the slots are filled in, a write back to the machine would only be the value we just read. */
    private boolean ready;

    private int untilDetected;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField textField;

    public ContainerRenamer(InventoryPlayer ip, ICustomNameObject obj) {
        super(ip, obj instanceof TileEntity ? (TileEntity) obj : null, obj instanceof IPart ? (IPart) obj : null);
        namedObject = obj;
        this.iconObject = obj instanceof ICustomIconObject ? (ICustomIconObject) obj : null;

        if (this.iconObject != null) {
            this.icons.setStackInSlot(CHOSEN, this.iconObject.getCustomIcon());
            this.addSlotToContainer(new IconSlot(this.icons, CHOSEN, ICON_X, ICON_Y));
            this.addSlotToContainer(new DetectedSlot(this.icons, DETECTED, ICON_X, ICON_Y));
            this.bindPlayerInventory(ip, PLAYER_INV_X, PLAYER_INV_Y);
        }

        this.ready = true;
    }

    public boolean hasIcon() {
        return this.iconObject != null;
    }

    public ItemStack getChosenIcon() {
        return this.icons.getStackInSlot(CHOSEN);
    }

    /** What the machine pictures itself as, which is what an empty slot falls back to. */
    public ItemStack getDetectedIcon() {
        return this.icons.getStackInSlot(DETECTED);
    }

    @SideOnly(Side.CLIENT)
    public void setTextField(final MEGuiTextField name) {
        this.textField = name;
        if (getCustomName() != null) textField.setText(getCustomName());
    }

    public void setNewName(String newValue) {
        this.namedObject.setCustomName(newValue);
    }

    /** Both at once, which is the one thing a right click on either cannot do. */
    public void reset() {
        this.namedObject.setCustomName("");

        if (this.iconObject != null) {
            this.icons.setStackInSlot(CHOSEN, ItemStack.EMPTY);
        }
    }

    @Override
    public void setCustomName(final String customName) {
        super.setCustomName(customName);
        if (!Platform.isServer() && customName != null) textField.setText(customName);
    }

    @Override
    public void detectAndSendChanges() {
        verifyPermissions(SecurityPermissions.BUILD, false);

        // Working the hint out means looking at all six neighbours, ray traces and all, which is more than
        // a hint is worth sixty times a second.
        if (Platform.isServer() && this.iconObject != null && --this.untilDetected <= 0) {
            this.untilDetected = DETECT_INTERVAL;
            this.icons.setStackInSlot(DETECTED, this.iconObject.getDefaultIcon());
        }

        super.detectAndSendChanges();
        if (!Platform.isServer() && getCustomName() != null) textField.setText(getCustomName());
    }

    @Override
    public void saveChanges() {
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        if (this.ready && slot == CHOSEN && this.iconObject != null && Platform.isServer()) {
            this.iconObject.setCustomIcon(this.icons.getStackInSlot(CHOSEN));
        }
    }

    /** A picture, so one item and no wrapped key: everything that draws it draws an item model. */
    private static class IconSlot extends SlotFakeTypeOnly {

        IconSlot(final IItemHandler inv, final int idx, final int x, final int y) {
            super(inv, idx, x, y);
        }

        @Override
        public void putStack(final ItemStack is) {
            // A ghost dragged out of HEI arrives wrapped; anything but an item has no picture to give.
            final GenericStack wrapped = GenericStack.unwrapItemStack(is);

            if (wrapped == null) {
                super.putStack(is);
            } else if (wrapped.what() instanceof AEItemKey key) {
                super.putStack(key.toStack());
            } else {
                super.putStack(ItemStack.EMPTY);
            }
        }
    }

    /** Never touched by a player: it is the machine's own answer, sent over so the screen can show it. */
    private static class DetectedSlot extends AppEngSlot {

        DetectedSlot(final IItemHandler inv, final int idx, final int x, final int y) {
            super(inv, idx, x, y);
            this.setHidden(true);
        }

        @Override
        public boolean isItemValid(@Nonnull final ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(final EntityPlayer player) {
            return false;
        }
    }
}
