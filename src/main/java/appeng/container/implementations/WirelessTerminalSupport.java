package appeng.container.implementations;

import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.container.AEBaseContainer;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import baubles.api.BaublesApi;

/**
 * Everything a screen opened from a wireless terminal has to do whatever screen it is: hold on to the item it
 * was opened from, drain it, close when the network goes out of range, and carry the upgrade card.
 *
 * <p>Held rather than inherited, because the screens that need it descend from different containers - the
 * interface terminal and the interface configuration terminal have no ancestor in common with the storage
 * one.</p>
 */
public final class WirelessTerminalSupport implements IAEAppEngInventory {

    private final AEBaseContainer container;
    private final WirelessTerminalGuiObject terminal;
    private final AppEngInternalInventory upgrades;
    private final int slot;

    private double powerMultiplier = 0.5;
    private int ticks = 0;

    public WirelessTerminalSupport(final AEBaseContainer container, final WirelessTerminalGuiObject terminal,
            final int upgradeSlots) {
        this.container = container;
        this.terminal = terminal;

        if (terminal != null) {
            final int slotIndex = terminal.getInventorySlot();
            if (!terminal.isBaubleSlot()) {
                container.lockPlayerInventorySlot(slotIndex);
            }
            this.slot = slotIndex;
        } else {
            container.lockPlayerInventorySlot(container.getPlayerInv().currentItem);
            this.slot = -1;
        }

        this.upgrades = new StackUpgradeInventory(terminal.getItemStack(), this, upgradeSlots);
        this.upgrades.readFromNBT(terminal.getItemStack().getTagCompound() == null ? new NBTTagCompound()
                : terminal.getItemStack().getTagCompound().getCompoundTag("upgrades"));
    }

    public AppEngInternalInventory getUpgrades() {
        return this.upgrades;
    }

    public ItemStack getTerminal() {
        return this.terminal.getItemStack();
    }

    public int getInventorySlot() {
        return this.terminal.getInventorySlot();
    }

    public boolean isBaubleSlot() {
        return this.terminal.isBaubleSlot();
    }

    /**
     * The once-a-tick housekeeping: the terminal is still where it was, it still has charge, and the network
     * is still in reach. Closes the screen itself when one of those stops being true.
     */
    public void tick() {
        final ItemStack currentItem;
        if (this.terminal.isBaubleSlot()) {
            currentItem = BaublesApi.getBaublesHandler(this.container.getPlayerInv().player).getStackInSlot(this.slot);
        } else {
            currentItem = this.slot < 0 ? this.container.getPlayerInv().getCurrentItem()
                    : this.container.getPlayerInv().getStackInSlot(this.slot);
        }

        if (currentItem.isEmpty()) {
            this.container.setValidContainer(false);
        } else if (!this.terminal.getItemStack().isEmpty() && currentItem != this.terminal.getItemStack()) {
            if (ItemStack.areItemsEqual(this.terminal.getItemStack(), currentItem)) {
                if (this.terminal.isBaubleSlot()) {
                    BaublesApi.getBaublesHandler(this.container.getPlayerInv().player)
                            .setStackInSlot(this.slot, this.terminal.getItemStack());
                } else {
                    this.container.getPlayerInv().setInventorySlotContents(this.slot, this.terminal.getItemStack());
                }
            } else {
                this.container.setValidContainer(false);
            }
        }

        // drain 1 ae t
        this.ticks++;
        if (this.ticks > 10) {
            final double ext = this.terminal.extractAEPower(this.powerMultiplier * this.ticks, Actionable.MODULATE,
                    PowerMultiplier.CONFIG);
            if (ext < this.powerMultiplier * this.ticks) {
                if (Platform.isServer() && this.container.isValidContainer()) {
                    this.container.getPlayerInv().player.sendMessage(PlayerMessages.DeviceNotPowered.get());
                }

                this.container.setValidContainer(false);
            }
            this.ticks = 0;
        }

        if (!this.terminal.rangeCheck()) {
            if (Platform.isServer() && this.container.isValidContainer()) {
                this.container.getPlayerInv().player.sendMessage(PlayerMessages.OutOfRange.get());
            }

            this.container.setValidContainer(false);
        } else {
            this.powerMultiplier = AEConfig.instance().wireless_getDrainRate(this.terminal.getRange());
        }
    }

    /**
     * Right-clicking a magnet card in its slot turns it off and on rather than picking it up.
     *
     * @return true when the click was that, and the container should do nothing else with it.
     */
    public static boolean toggleMagnetCard(final Slot magnetSlot, final int dragType, final ClickType clickType) {
        if (clickType != ClickType.PICKUP || dragType != 1 || magnetSlot == null || magnetSlot.getStack().isEmpty()) {
            return false;
        }

        final ItemStack itemStack = magnetSlot.getStack();
        NBTTagCompound tag = itemStack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
        }

        if (tag.hasKey("enabled")) {
            tag.setBoolean("enabled", !tag.getBoolean("enabled"));
        } else {
            tag.setBoolean("enabled", false);
        }

        itemStack.setTagCompound(tag);
        magnetSlot.onSlotChanged();
        return true;
    }

    @Override
    public void saveChanges() {
        if (Platform.isServer()) {
            final NBTTagCompound tag = new NBTTagCompound();
            this.upgrades.writeToNBT(tag, "upgrades");
            this.terminal.saveChanges(tag);
        }
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
    }

}
