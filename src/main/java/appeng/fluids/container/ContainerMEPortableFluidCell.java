package appeng.fluids.container;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.implementations.IUpgradeableCellContainer;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.me.GridInventoryEntry;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEFluidInventoryUpdate;
import appeng.core.sync.packets.PacketTargetFluidStack;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import baubles.api.BaublesApi;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.List;

/**
 * Live updates follow CONTRACT.md §10 "Third case: terminal live updates" - this is a portable-cell / view-only
 * cell terminal (an {@link IPortableCell}, never a grid machine of its own), so per the contract's own
 * classification (and CONTRACT.md's wave 5 prerequisites / STATUS.md's "before starting wave 5" notes) it always
 * falls into case 2: a server-side per-tick diff of {@link MEStorage#getAvailableStacks()}, mirroring what
 * {@code ContainerMEMonitorable} does for its own case-2 hosts and what upstream's
 * {@code MEStorageMenu.broadcastChanges()} does unconditionally. There is no case-1 branch here because an
 * {@link IPortableCell} is never an {@code appeng.parts.reporting.AbstractPartTerminal}.
 * <p/>
 * As with {@link ContainerFluidTerminal}, this container never surfaced a craftable flag or a requestable amount
 * for fluids before the migration (checked by reading the whole file first) - every {@link GridInventoryEntry}
 * sent from here therefore carries {@code requestableAmount = 0} and {@code craftable = false}.
 */
public class ContainerMEPortableFluidCell extends AEBaseContainer implements IAEAppEngInventory, IConfigManagerHost, IConfigurableObject, IUpgradeableCellContainer, IInventorySlotAware {

    protected final WirelessTerminalGuiObject wirelessTerminalGUIObject;

    private final IConfigManager clientCM;
    private final MEStorage monitor;
    @GuiSync(99)
    public boolean hasPower = false;
    private final ITerminalHost terminal;
    private IConfigManager serverCM;
    private IConfigManagerHost gui;
    private IGridNode networkNode;
    // Holds the fluid the client wishes to extract, or null for insert
    private AEKey clientRequestedTargetFluid = null;
    private double powerMultiplier = 0.5;
    private int ticks = 0;
    private final int slot;

    /** Case 2 snapshot: the amounts last broadcast to listeners. Seeded at construction, see {@link ContainerFluidTerminal}. */
    private KeyCounter previousAvailableStacks = new KeyCounter();

    protected AppEngInternalInventory upgrades;

    public ContainerMEPortableFluidCell(final InventoryPlayer ip, final IPortableCell monitorable) {
        this(ip, monitorable, null, true);
    }

    public ContainerMEPortableFluidCell(final InventoryPlayer ip, final IPortableCell monitorable, WirelessTerminalGuiObject iGuiItemObject) {
        this(ip, monitorable, iGuiItemObject, true);
    }

    public ContainerMEPortableFluidCell(InventoryPlayer ip, IPortableCell monitorable, WirelessTerminalGuiObject iGuiItemObject, boolean bindInventory) {
        super(ip, monitorable);

        this.terminal = monitorable;
        this.wirelessTerminalGUIObject = (WirelessTerminalGuiObject) monitorable;

        this.clientCM = new ConfigManager(this);

        this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        if (Platform.isServer()) {
            this.serverCM = terminal.getConfigManager();
            this.monitor = terminal.getInventory();

            if (this.monitor != null) {
                this.setPowerSource((IEnergySource) terminal);
                final IGridNode node;
                if (terminal instanceof IGridHost) {
                    node = ((IGridHost) terminal).getGridNode(AEPartLocation.INTERNAL);
                } else {
                    node = ((IActionHost) terminal).getActionableNode();
                }

                if (node != null) {
                    this.networkNode = node;
                }

                this.previousAvailableStacks = this.monitor.getAvailableStacks();
            }
        } else {
            this.monitor = null;
        }

        if (monitorable != null) {
            final int slotIndex = ((IInventorySlotAware) monitorable).getInventorySlot();
            if (!((IInventorySlotAware) monitorable).isBaubleSlot()) {
                this.lockPlayerInventorySlot(slotIndex);
            }
            this.slot = slotIndex;
        } else {
            this.slot = -1;
            this.lockPlayerInventorySlot(ip.currentItem);
        }

        if (bindInventory) {
            this.bindPlayerInventory(ip, 0, 140);
        }
        hasPower = this.wirelessTerminalGUIObject.extractAEPower(this.getPowerMultiplier(), Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.001;
        upgrades = new StackUpgradeInventory(wirelessTerminalGUIObject.getItemStack(), this, 2);
        this.loadFromNBT();

        this.setupUpgrades();
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            final ItemStack currentItem;
            if (wirelessTerminalGUIObject.isBaubleSlot()) {
                currentItem = BaublesApi.getBaublesHandler(this.getPlayerInv().player).getStackInSlot(this.slot);
            } else {
                currentItem = this.slot < 0 ? this.getPlayerInv().getCurrentItem() : this.getPlayerInv().getStackInSlot(this.slot);
            }

            if (currentItem.isEmpty()) {
                this.setValidContainer(false);
            } else if (!this.wirelessTerminalGUIObject.getItemStack().isEmpty() && currentItem != this.wirelessTerminalGUIObject.getItemStack()) {
                if (ItemStack.areItemsEqual(this.wirelessTerminalGUIObject.getItemStack(), currentItem)) {
                    if (wirelessTerminalGUIObject.isBaubleSlot()) {
                        BaublesApi.getBaublesHandler(this.getPlayerInv().player).setStackInSlot(this.slot, this.wirelessTerminalGUIObject.getItemStack());
                    } else {
                        this.getPlayerInv().setInventorySlotContents(this.slot, this.wirelessTerminalGUIObject.getItemStack());
                    }
                } else {
                    this.setValidContainer(false);
                }
            }

            // drain 1 ae t
            this.ticks++;
            if (this.ticks > 10) {
                double ext = this.wirelessTerminalGUIObject.extractAEPower(this.getPowerMultiplier() * this.ticks, Actionable.MODULATE, PowerMultiplier.CONFIG);
                if (ext < this.getPowerMultiplier() * this.ticks) {
                    if (Platform.isServer() && this.isValidContainer()) {
                        this.getPlayerInv().player.sendMessage(PlayerMessages.DeviceNotPowered.get());
                    }

                    this.setValidContainer(false);
                }
                this.ticks = 0;
                this.hasPower = ext > 0.001;
            }

            if (!this.wirelessTerminalGUIObject.rangeCheck()) {
                if (Platform.isServer() && this.isValidContainer()) {
                    this.getPlayerInv().player.sendMessage(PlayerMessages.OutOfRange.get());
                }

                this.setValidContainer(false);
            } else {
                this.setPowerMultiplier(AEConfig.instance().wireless_getDrainRate(this.wirelessTerminalGUIObject.getRange()));
            }

            if (this.monitor != this.terminal.getInventory()) {
                this.setValidContainer(false);
            }

            for (final Settings set : this.serverCM.getSettings()) {
                final Enum<?> sideLocal = this.serverCM.getSetting(set);
                final Enum<?> sideRemote = this.clientCM.getSetting(set);

                if (sideLocal != sideRemote) {
                    this.clientCM.putSetting(set, sideLocal);
                    for (final IContainerListener crafter : this.listeners) {
                        if (crafter instanceof EntityPlayerMP) {
                            try {
                                NetworkHandler.instance().sendTo(new PacketValueConfig(set.name(), sideLocal.name()), (EntityPlayerMP) crafter);
                            } catch (final IOException e) {
                                AELog.debug(e);
                            }
                        }
                    }
                }
            }

            if (this.monitor != null) {
                try {
                    final List<GridInventoryEntry> changes = this.collectChanges();

                    if (!changes.isEmpty()) {
                        PacketMEFluidInventoryUpdate piu = new PacketMEFluidInventoryUpdate();

                        for (final GridInventoryEntry entry : changes) {
                            piu.appendFluid(entry);
                        }

                        if (!piu.isEmpty()) {
                            for (final Object c : this.listeners) {
                                if (c instanceof EntityPlayer) {
                                    NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);
                                }
                            }
                        }
                    }
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            }
            super.detectAndSendChanges();
        }
    }

    /**
     * Case 2 (CONTRACT.md §10): server-side per-tick diff of {@link MEStorage#getAvailableStacks()}, exactly
     * like upstream's {@code MEStorageMenu.broadcastChanges()}.
     */
    private List<GridInventoryEntry> collectChanges() {
        final List<GridInventoryEntry> result = new ArrayList<>();
        final KeyCounter current = this.monitor.getAvailableStacks();

        for (final var entry : current) {
            final AEKey what = entry.getKey();
            final long amount = entry.getLongValue();
            if (amount != this.previousAvailableStacks.get(what)) {
                result.add(new GridInventoryEntry(what, amount, 0, false));
            }
        }

        for (final var entry : this.previousAvailableStacks) {
            final AEKey what = entry.getKey();
            if (entry.getLongValue() != 0 && current.get(what) == 0) {
                result.add(new GridInventoryEntry(what, 0, 0, false));
            }
        }

        this.previousAvailableStacks = current;

        return result;
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer p, final int idx) {
        if (Platform.isClient()) {
            return ItemStack.EMPTY;
        }
        EntityPlayerMP player = (EntityPlayerMP) p;
        if (this.inventorySlots.get(idx) instanceof SlotPlayerInv || this.inventorySlots.get(idx) instanceof SlotPlayerHotBar) {
            final AppEngSlot clickSlot = (AppEngSlot) this.inventorySlots.get(idx); // require AE SLots!
            ItemStack itemStack = clickSlot.getStack();

            ItemStack copy = itemStack.copy();
            copy.setCount(1);
            IFluidHandlerItem fh = FluidUtil.getFluidHandler(copy);
            if (fh == null) {
                // only fluid handlers items
                return ItemStack.EMPTY;
            }

            int heldAmount = itemStack.getCount();
            for (int i = 0; i < heldAmount; i++) {
                copy = itemStack.copy();
                copy.setCount(1);
                fh = FluidUtil.getFluidHandler(copy);

                final FluidStack extract = fh.drain(Integer.MAX_VALUE, false);
                if (extract == null || extract.amount < 1) {
                    return ItemStack.EMPTY;
                }

                final AEFluidKey extractKey = AEFluidKey.of(extract);

                // Check if we can push into the system
                final long inserted = Platform.poweredInsert(this.getPowerSource(), this.monitor, extractKey, extract.amount, this.getActionSource(), Actionable.SIMULATE);
                final long notStorable = extract.amount - inserted;

                if (notStorable > 0) {
                    final FluidStack storable = fh.drain((int) inserted, false);

                    if (storable == null || storable.amount == 0) {
                        return ItemStack.EMPTY;
                    } else {
                        extract.amount = storable.amount;
                    }
                }

                // Actually drain
                final FluidStack drained = fh.drain(extract, true);
                extract.amount = drained.amount;

                final long reallyInserted = Platform.poweredInsert(this.getPowerSource(), this.monitor, extractKey, extract.amount, this.getActionSource());
                final long leftover = extract.amount - reallyInserted;

                if (leftover > 0) {
                    final long spillInserted = this.monitor.insert(extractKey, leftover, Actionable.MODULATE, this.getActionSource());
                    final long spill = leftover - spillInserted;
                    if (spill > 0) {
                        fh.fill(extractKey.toStack((int) spill), true);
                    }
                }

                if (leftover == 0) {
                    if (!player.inventory.addItemStackToInventory(fh.getContainer())) {
                        player.dropItem(fh.getContainer(), false);
                    }
                    clickSlot.decrStackSize(1);
                }
            }
            this.detectAndSendChanges();
            return ItemStack.EMPTY;
        }
        return super.transferStackInSlot(p, idx);
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        if (action != InventoryAction.FILL_ITEM && action != InventoryAction.EMPTY_ITEM) {
            super.doAction(player, action, slot, id);
            return;
        }

        final ItemStack held = player.inventory.getItemStack();
        ItemStack heldCopy = held.copy();
        heldCopy.setCount(1);
        IFluidHandlerItem fh = FluidUtil.getFluidHandler(heldCopy);
        if (fh == null) {
            // only fluid handlers items
            return;
        }

        if (action == InventoryAction.FILL_ITEM && this.clientRequestedTargetFluid instanceof AEFluidKey targetFluid) {
            // Check how much we can store in the item
            int amountAllowed = fh.fill(targetFluid.toStack(Integer.MAX_VALUE), false);
            int heldAmount = held.getCount();
            for (int i = 0; i < heldAmount; i++) {
                ItemStack copiedFluidContainer = held.copy();
                copiedFluidContainer.setCount(1);
                fh = FluidUtil.getFluidHandler(copiedFluidContainer);

                // Check if we can pull out of the system
                final long canPull = Platform.poweredExtraction(this.getPowerSource(), this.monitor, targetFluid, amountAllowed, this.getActionSource(), Actionable.SIMULATE);
                if (canPull < 1) {
                    return;
                }

                // How much could fit into the container
                final int canFill = fh.fill(targetFluid.toStack((int) canPull), false);
                if (canFill == 0) {
                    return;
                }

                // Now actually pull out of the system
                final long pulled = Platform.poweredExtraction(this.getPowerSource(), this.monitor, targetFluid, canFill, this.getActionSource());
                if (pulled < 1) {
                    // Something went wrong
                    AELog.error("Unable to pull fluid out of the ME system even though the simulation said yes ");
                    return;
                }

                // Actually fill
                final int used = fh.fill(targetFluid.toStack((int) pulled), true);

                if (used != canFill) {
                    AELog.error("Fluid item [%s] reported a different possible amount than it actually accepted.", held.getDisplayName());
                }

                if (held.getCount() == 1) {
                    player.inventory.setItemStack(fh.getContainer());
                } else {
                    player.inventory.getItemStack().shrink(1);
                    if (!player.inventory.addItemStackToInventory(fh.getContainer())) {
                        player.dropItem(fh.getContainer(), false);
                    }
                }
            }
            this.updateHeld(player);

        } else if (action == InventoryAction.EMPTY_ITEM) {
            int heldAmount = held.getCount();
            for (int i = 0; i < heldAmount; i++) {
                ItemStack copiedFluidContainer = held.copy();
                copiedFluidContainer.setCount(1);
                fh = FluidUtil.getFluidHandler(copiedFluidContainer);

                // See how much we can drain from the item
                final FluidStack extract = fh.drain(Integer.MAX_VALUE, false);
                if (extract == null || extract.amount < 1) {
                    return;
                }

                final AEFluidKey extractKey = AEFluidKey.of(extract);

                // Check if we can push into the system
                final long inserted = Platform.poweredInsert(this.getPowerSource(), this.monitor, extractKey, extract.amount, this.getActionSource(), Actionable.SIMULATE);
                final long notStorable = extract.amount - inserted;

                if (notStorable > 0) {
                    final FluidStack storable = fh.drain((int) inserted, false);

                    if (storable == null || storable.amount == 0) {
                        return;
                    } else {
                        extract.amount = storable.amount;
                    }
                }

                // Actually drain
                final FluidStack drained = fh.drain(extract, true);
                extract.amount = drained.amount;

                final long reallyInserted = Platform.poweredInsert(this.getPowerSource(), this.monitor, extractKey, extract.amount, this.getActionSource());
                final long leftover = extract.amount - reallyInserted;

                if (leftover > 0) {
                    final long spillInserted = this.monitor.insert(extractKey, leftover, Actionable.MODULATE, this.getActionSource());
                    final long spill = leftover - spillInserted;
                    if (spill > 0) {
                        fh.fill(extractKey.toStack((int) spill), true);
                    }
                }

                if (held.getCount() == 1) {
                    player.inventory.setItemStack(fh.getContainer());
                } else {
                    player.inventory.getItemStack().shrink(1);
                    if (!player.inventory.addItemStackToInventory(fh.getContainer())) {
                        player.dropItem(fh.getContainer(), false);
                    }
                }
            }
            this.updateHeld(player);
        }
    }

    private double getPowerMultiplier() {
        return this.powerMultiplier;
    }

    void setPowerMultiplier(final double powerMultiplier) {
        this.powerMultiplier = powerMultiplier;
    }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);

        this.queueInventory(listener);
    }

    private void queueInventory(final IContainerListener c) {
        if (Platform.isServer() && c instanceof EntityPlayer && this.monitor != null) {
            try {
                PacketMEFluidInventoryUpdate piu = new PacketMEFluidInventoryUpdate();

                for (final var entry : this.monitor.getAvailableStacks()) {
                    final GridInventoryEntry send = new GridInventoryEntry(entry.getKey(), entry.getLongValue(), 0, false);
                    try {
                        piu.appendFluid(send);
                    } catch (final BufferOverflowException boe) {
                        NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);

                        piu = new PacketMEFluidInventoryUpdate();
                        piu.appendFluid(send);
                    }
                }

                NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        if (Platform.isServer()) {
            return this.serverCM;
        }
        return this.clientCM;
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
        if (this.getGui() != null) {
            this.getGui().updateSetting(manager, settingName, newValue);
        }
    }

    public void setTargetStack(@Nullable final AEKey stack) {
        if (Platform.isClient()) {
            if (stack == null && this.clientRequestedTargetFluid == null) {
                return;
            }
            // AEKey carries no amount (unlike GenericStack) - equals() here is already the size-insensitive
            // identity check the old FluidStack#isFluidEqual was. See CONTRACT.md §9.1.
            if (stack != null && stack.equals(this.clientRequestedTargetFluid)) {
                return;
            }
            NetworkHandler.instance().sendToServer(new PacketTargetFluidStack(stack));
        }

        this.clientRequestedTargetFluid = stack;
    }

    private IConfigManagerHost getGui() {
        return this.gui;
    }

    public boolean isPowered() {
        return this.hasPower;
    }

    public void setGui(@Nonnull final IConfigManagerHost gui) {
        this.gui = gui;
    }

    @Override
    public int availableUpgrades() {
        return 1;
    }

    @Override
    public void setupUpgrades() {
        if (wirelessTerminalGUIObject != null) {
            for (int upgradeSlot = 0; upgradeSlot < availableUpgrades(); upgradeSlot++) {
                this.addSlotToContainer(
                        (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, upgradeSlot, 183, 139 + upgradeSlot * 18, this.getInventoryPlayer()))
                                .setNotDraggable());
            }
        }
    }

    @Override
    public void saveChanges() {
        if (Platform.isServer()) {
            NBTTagCompound tag = new NBTTagCompound();
            this.upgrades.writeToNBT(tag, "upgrades");

            this.wirelessTerminalGUIObject.saveChanges(tag);
        }
    }

    private void loadFromNBT() {
        NBTTagCompound data = wirelessTerminalGUIObject.getItemStack().getTagCompound();
        if (data != null) {
            upgrades.readFromNBT(wirelessTerminalGUIObject.getItemStack().getTagCompound().getCompoundTag("upgrades"));
        }
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack newStack) {
    }

    @Override
    public int getInventorySlot() {
        return wirelessTerminalGUIObject.getInventorySlot();
    }

    @Override
    public boolean isBaubleSlot() {
        return wirelessTerminalGUIObject.isBaubleSlot();
    }
}
