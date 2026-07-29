/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.fluids.container;


import appeng.api.config.*;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
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
import appeng.container.me.GridInventoryEntry;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEFluidInventoryUpdate;
import appeng.core.sync.packets.PacketTargetFluidStack;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.InventoryAction;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.parts.reporting.AbstractPartTerminal;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * @author BrockWS
 * @version rv6 - 12/05/2018
 * @since rv6 12/05/2018
 * <p/>
 * Live updates follow CONTRACT.md §10 "Third case: terminal live updates", the same split
 * {@code appeng.container.implementations.ContainerMEMonitorable} implements: a fluid terminal part built on
 * {@link AbstractPartTerminal} (the only host this container is constructed with today, {@code PartFluidTerminal})
 * gets case 1 (real push, relayed through {@link AbstractPartTerminal#addTerminalListener}); any other
 * {@link ITerminalHost} falls back to case 2 (a server-side per-tick diff of {@link MEStorage#getAvailableStacks()}).
 * Unlike {@code ContainerMEMonitorable}, there is no craftable-flag column here: the pre-migration fluid terminal
 * never surfaced {@code isCraftable()}/{@code countRequestable()} for fluids (verified by reading the whole file
 * before this rewrite - {@code IAEFluidStack} inherited those members from {@code IAEStack} but this container
 * never read them), so every {@link GridInventoryEntry} sent from here carries {@code requestableAmount = 0} and
 * {@code craftable = false}, preserving that behaviour exactly rather than adding a new one.
 */
public class ContainerFluidTerminal extends AEBaseContainer implements IConfigManagerHost, IConfigurableObject, IStorageWatcherNode {
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

    /**
     * Non-null only when {@code terminal} is a {@link AbstractPartTerminal} (true for {@code PartFluidTerminal},
     * the only host this container is built from today). See CONTRACT.md §10 / {@code ContainerMEMonitorable}'s
     * identical field for the full reasoning: the grid only ever installs a watcher on the node's *machine*, so
     * the part - not this container - holds the {@link IStackWatcher} and relays {@link #onStackChange} to
     * whichever containers are attached.
     */
    private AbstractPartTerminal networkTerminalPart;

    /** Case 1 buffer: keys the grid told us changed since the last {@link #detectAndSendChanges()}. */
    private final Map<AEKey, Long> pendingPushChanges = new LinkedHashMap<>();

    /**
     * Case 2 snapshot: the amounts last broadcast to listeners. Seeded at construction so the tick right after a
     * GUI opens does not immediately re-broadcast the same full listing {@link #queueInventory} already sent.
     */
    private KeyCounter previousAvailableStacks = new KeyCounter();

    public ContainerFluidTerminal(InventoryPlayer ip, ITerminalHost terminal) {
        super(ip, terminal);
        this.terminal = terminal;
        this.clientCM = new ConfigManager(this);

        this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        if (Platform.isServer()) {
            this.serverCM = terminal.getConfigManager();
            this.monitor = terminal.getInventory();

            if (this.monitor != null) {
                if (terminal instanceof IEnergySource) {
                    this.setPowerSource((IEnergySource) terminal);
                } else if (terminal instanceof IGridHost || terminal instanceof IActionHost) {
                    final IGridNode node;
                    if (terminal instanceof IGridHost) {
                        node = ((IGridHost) terminal).getGridNode(AEPartLocation.INTERNAL);
                    } else if (terminal instanceof IActionHost) {
                        node = ((IActionHost) terminal).getActionableNode();
                    } else {
                        node = null;
                    }

                    if (node != null) {
                        this.networkNode = node;
                        final IGrid g = node.getGrid();
                        if (g != null) {
                            this.setPowerSource(new ChannelPowerSrc(this.networkNode, g.getCache(IEnergyGrid.class)));
                        }
                    }
                }

                if (terminal instanceof AbstractPartTerminal) {
                    this.networkTerminalPart = (AbstractPartTerminal) terminal;
                    this.networkTerminalPart.addTerminalListener(this);
                }

                this.previousAvailableStacks = this.monitor.getAvailableStacks();
            }
        } else {
            this.monitor = null;
        }
        this.bindPlayerInventory(ip, 0, 222 - 82);
    }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);

        this.queueInventory(listener);
    }

    @Override
    public void removeListener(final IContainerListener c) {
        super.removeListener(c);

        if (this.listeners.isEmpty() && this.networkTerminalPart != null) {
            this.networkTerminalPart.removeTerminalListener(this);
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        if (this.networkTerminalPart != null) {
            this.networkTerminalPart.removeTerminalListener(this);
        }
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

    /**
     * Called on the server only, relayed from {@link AbstractPartTerminal#onStackChange(AEKey, long)} - see
     * {@link #networkTerminalPart}. Not called directly by the grid: this container is not itself a grid machine,
     * so {@link #updateWatcher(IStackWatcher)} below is never invoked in practice.
     */
    @Override
    public void onStackChange(final AEKey what, final long amount) {
        this.pendingPushChanges.put(what, amount);
    }

    @Override
    public void updateWatcher(final IStackWatcher newWatcher) {
        // Never called: the grid registers watchers against the terminal part (the node's machine), not
        // against this container. See AbstractPartTerminal.updateWatcher/addTerminalListener.
    }

    /**
     * Builds this tick's delta - whichever of case 1 / case 2 (CONTRACT.md §10) applies to this terminal.
     */
    private List<GridInventoryEntry> collectChanges() {
        final List<GridInventoryEntry> result = new ArrayList<>();

        if (this.networkTerminalPart != null) {
            // Case 1: real push - only the keys the grid actually told us changed, via onStackChange.
            for (final Map.Entry<AEKey, Long> e : this.pendingPushChanges.entrySet()) {
                result.add(new GridInventoryEntry(e.getKey(), e.getValue(), 0, false));
            }
            this.pendingPushChanges.clear();
        } else {
            // Case 2: server-side per-tick diff, exactly like upstream's MEStorageMenu.broadcastChanges().
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
        }

        return result;
    }

    @Override
    public IConfigManager getConfigManager() {
        if (Platform.isServer()) {
            return this.serverCM;
        }
        return this.clientCM;
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

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
        if (this.getGui() != null) {
            this.getGui().updateSetting(manager, settingName, newValue);
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
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
            this.updatePowerStatus();

            super.detectAndSendChanges();
        }
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

    protected void updatePowerStatus() {
        try {
            if (this.networkNode != null) {
                this.setPowered(this.networkNode.isActive());
            } else if (this.getPowerSource() instanceof IEnergyGrid) {
                this.setPowered(((IEnergyGrid) this.getPowerSource()).isNetworkPowered());
            } else {
                this.setPowered(this.getPowerSource().extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.8);
            }
        } catch (final Exception ignore) {
            // :P
        }
    }

    private IConfigManagerHost getGui() {
        return this.gui;
    }

    public void setGui(@Nonnull final IConfigManagerHost gui) {
        this.gui = gui;
    }

    public boolean isPowered() {
        return this.hasPower;
    }

    private void setPowered(final boolean isPowered) {
        this.hasPower = isPowered;
    }
}
