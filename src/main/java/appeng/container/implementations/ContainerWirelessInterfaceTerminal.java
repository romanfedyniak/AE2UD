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

public class ContainerWirelessInterfaceTerminal extends ContainerInterfaceTerminal
        implements IInventorySlotAware, IUpgradeableCellContainer, IWirelessTerminalContainer {

    private final WirelessTerminalSupport support;
    protected final List<Slot> upgradeSlots = new ArrayList<>();

    public ContainerWirelessInterfaceTerminal(InventoryPlayer ip, WirelessTerminalGuiObject guiObject) {
        super(ip, guiObject, false);

        this.support = new WirelessTerminalSupport(this, guiObject, UPGRADE_SLOTS);
        this.bindPlayerInventory(ip, 0, 0);
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
                    this.support.getUpgrades(), upgradeSlot, 187, 3 + upgradeSlot * 18, this.getInventoryPlayer());
            slot.setNotDraggable();
            this.upgradeSlots.add(this.addSlotToContainer(slot));
        }
    }
}
