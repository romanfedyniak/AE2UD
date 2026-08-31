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

import java.util.List;
import java.util.ArrayList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import appeng.api.implementations.IUpgradeableCellContainer;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.interfaces.IWirelessTerminalContainer;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;

public class ContainerWirelessInterfaceConfigurationTerminal extends ContainerInterfaceConfigurationTerminal
        implements IInventorySlotAware, IUpgradeableCellContainer, IWirelessTerminalContainer {

    private final WirelessTerminalSupport support;
    protected final List<Slot> upgradeSlots = new ArrayList<>();

    public ContainerWirelessInterfaceConfigurationTerminal(final InventoryPlayer ip,
            final WirelessTerminalGuiObject guiObject) {
        super(ip, guiObject, false);

        this.support = new WirelessTerminalSupport(this, guiObject, UPGRADE_SLOTS);
        this.bindPlayerInventory(ip, 14, 235 - /* height of player inventory */82);
        this.setupUpgrades();
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            this.support.tick();
            super.detectAndSendChanges();
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, @NotNull EntityPlayer player) {
        if (slotId >= 0 && slotId < this.inventorySlots.size()
                && this.upgradeSlots.contains(this.inventorySlots.get(slotId))
                && WirelessTerminalSupport.toggleMagnetCard(this.inventorySlots.get(slotId), dragType,
                        clickTypeIn)) {
            return ItemStack.EMPTY;
        }

        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public ItemStack getTerminal() {
        return this.support.getTerminal();
    }

    @Override
    public int getInventorySlot() {
        return this.support.getInventorySlot();
    }

    @Override
    public boolean isBaubleSlot() {
        return this.support.isBaubleSlot();
    }

    @Override
    public int availableUpgrades() {
        return UPGRADE_SLOTS;
    }

    @Override
    public void setupUpgrades() {
        for (int upgradeSlot = 0; upgradeSlot < this.availableUpgrades(); upgradeSlot++) {
            final SlotRestrictedInput slot = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES,
                    this.support.getUpgrades(), upgradeSlot, 201, 169 + upgradeSlot * 18, this.getInventoryPlayer());
            slot.setNotDraggable();
            this.upgradeSlots.add(this.addSlotToContainer(slot));
        }
    }
}
