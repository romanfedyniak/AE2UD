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


import appeng.api.AEApi;
import appeng.api.config.*;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalPinHost;
import appeng.api.storage.IPlayerTerminalPins;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.me.GridInventoryEntry;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.core.sync.packets.PacketTerminalPins;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cluster.implementations.ICraftingCPUListener;
import appeng.parts.reporting.AbstractPartTerminal;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import com.google.common.math.LongMath;


public class ContainerMEMonitorable extends AEBaseContainer implements IConfigManagerHost, IConfigurableObject,
        IStorageWatcherNode, ICraftingCPUListener {

    protected final SlotRestrictedInput[] cellView = new SlotRestrictedInput[5];
    private final MEStorage monitor;
    private final IConfigManager clientCM;
    private final ITerminalHost host;
    @GuiSync(99)
    public boolean canAccessViewCells = false;
    @GuiSync(98)
    public boolean hasPower = false;
    private IConfigManagerHost gui;
    private IConfigManager serverCM;
    private IGridNode networkNode;
    protected int jeiOffset = Platform.isModLoaded("jei") ? 24 : 0;

    /**
     * Non-null only when {@code monitorable} is one of the standard network-backed terminal parts (plain ME
     * terminal, crafting terminal, pattern terminal, expanded processing pattern terminal - anything built on
     * {@link AbstractPartTerminal}). This is "case 1" of CONTRACT.md §10 ("Third case: terminal live updates"):
     * real push. The grid only ever calls {@code IStorageWatcherNode.updateWatcher} on the node's *machine*,
     * i.e. the part, never on this container (a container is not itself a grid machine/node), so the part
     * relays {@link #onStackChange(AEKey, long)} calls to whichever containers are currently attached - see
     * {@link AbstractPartTerminal#addTerminalListener(IStorageWatcherNode)}.
     * <p/>
     * Every other {@link ITerminalHost} (a portable cell, a view-only cell terminal, an ME chest, a security
     * station, ...) has no grid node of its own to hang a watcher off, or shows a single cell's contents
     * rather than the network's, and falls back to "case 2": a server-side per-tick diff in
     * {@link #collectChanges()}, exactly like upstream's {@code MEStorageMenu.broadcastChanges()}.
     */
    private AbstractPartTerminal networkTerminalPart;

    /**
     * Case 1 buffer. Keys the grid told us changed since the last {@link #detectAndSendChanges()}, each
     * already carrying the authoritative new amount supplied by {@link IStorageService}'s own per-tick cache
     * diff (see {@code GridStorageCache.postWatcherUpdate}), so it is trusted as-is rather than re-read.
     * Drained and cleared every tick.
     */
    private final Map<AEKey, Long> pendingPushChanges = new LinkedHashMap<>();

    /**
     * Case 2 snapshot: the amounts last broadcast to listeners, diffed once per tick against
     * {@link #retainAvailableStacks()}. Also seeded at construction so the tick right after a GUI opens does
     * not immediately re-broadcast the same full listing {@link #queueInventory} just sent to the new
     * listener.
     */
    private KeyCounter previousAvailableStacks = new KeyCounter();

    /**
     * The last set of craftable keys sent to the client. A {@link AEKey} carries no craftable flag any more
     * (CONTRACT.md §8.3) - the flag moved onto {@link ICraftingGrid#getCraftables()}, which has no watcher
     * of its own in either upstream or this fork, so it is asked for every tick regardless of whether case 1
     * or case 2 is driving the amount half of the update. Holding the reference is what makes the identity
     * check in {@link #collectChanges()} work, so whatever is stored here must be immutable.
     */
    private Set<AEKey> previousCraftables = Collections.emptySet();
    private final IPlayerTerminalPins terminalPins;
    private final LinkedHashMap<AEKey, TerminalCraftingPin> craftingPins = new LinkedHashMap<>();
    private boolean terminalPinsRequested;
    private boolean showCraftingPins;
    private boolean showPlayerPins;
    private int craftingPinTicker;
    private List<TerminalCraftingPin> lastSentCraftingPins = Collections.emptyList();
    private final Set<CraftingCPUCluster> subscribedCraftingCpus = new HashSet<>();
    private final Set<AEKey> visiblePlayerPinKeys = new HashSet<>();
    private boolean craftingPinsDirty;
    private int clientTerminalPinSnapshotVersion;
    private int clientCraftingPinRows = 1;
    private int clientPlayerPinRows;
    private final AEKey[] clientPlayerPins = new AEKey[IPlayerTerminalPins.MAX_PINS];
    private List<TerminalCraftingPin> clientCraftingPins = Collections.emptyList();


    public ContainerMEMonitorable(final InventoryPlayer ip, final ITerminalHost monitorable) {
        this(ip, monitorable, true);
    }

    protected ContainerMEMonitorable(final InventoryPlayer ip, final ITerminalHost monitorable, final boolean bindInventory) {
        this(ip, monitorable, null, bindInventory);
    }

    protected ContainerMEMonitorable(final InventoryPlayer ip, final ITerminalHost monitorable, final IGuiItemObject iGuiItemObject, final boolean bindInventory) {
        super(ip, monitorable instanceof TileEntity ? (TileEntity) monitorable : null, monitorable instanceof IPart ? (IPart) monitorable : null, iGuiItemObject);

        this.host = monitorable;
        this.terminalPins = monitorable instanceof ITerminalPinHost
                ? ((ITerminalPinHost) monitorable).getTerminalPinStorage().forPlayer(ip.player.getPersistentID())
                : null;
        this.clientCM = new ConfigManager(this);

        this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);

        if (Platform.isServer()) {
            this.serverCM = monitorable.getConfigManager();

            this.monitor = monitorable.getInventory();
            if (this.monitor != null) {
                this.setCellInventory(this.monitor);

                if (monitorable instanceof IPortableCell) {
                    this.setPowerSource((IEnergySource) monitorable);
                    if (monitorable instanceof WirelessTerminalGuiObject) {
                        this.networkNode = ((WirelessTerminalGuiObject) monitorable).getActionableNode();
                    }
                } else if (monitorable instanceof IMEChest) {
                    this.setPowerSource((IEnergySource) monitorable);
                } else if (monitorable instanceof IGridHost || monitorable instanceof IActionHost) {
                    final IGridNode node;
                    if (monitorable instanceof IGridHost) {
                        node = ((IGridHost) monitorable).getGridNode(AEPartLocation.INTERNAL);
                    } else if (monitorable instanceof IActionHost) {
                        node = ((IActionHost) monitorable).getActionableNode();
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

                if (monitorable instanceof AbstractPartTerminal) {
                    this.networkTerminalPart = (AbstractPartTerminal) monitorable;
                    this.networkTerminalPart.addTerminalListener(this);
                }

                this.previousAvailableStacks = this.retainAvailableStacks();
                this.previousCraftables = this.computeCraftables();
            } else {
                this.setValidContainer(false);
            }
        } else {
            this.monitor = null;
        }

        this.canAccessViewCells = false;
        if (monitorable instanceof IViewCellStorage) {
            for (int y = 0; y < 5; y++) {
                this.cellView[y] = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.VIEW_CELL, ((IViewCellStorage) monitorable)
                        .getViewCellStorage(), y, 206, y * 18 + 8 + jeiOffset, this.getInventoryPlayer());
                this.cellView[y].setAllowEdit(this.canAccessViewCells);
                this.addSlotToContainer(this.cellView[y]);
            }
        }

        if (bindInventory) {
            this.bindPlayerInventory(ip, 0, 0);
        }
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer p, final int idx) {
        if (Platform.isClient()) {
            return ItemStack.EMPTY;
        }

        // Below logic is all about handling shift click for view cells
        if (!(this.host instanceof IViewCellStorage)) {
            return super.transferStackInSlot(p, idx);
        }

        // Is it a view cell?
        final Slot clickSlot = this.inventorySlots.get(idx);
        ItemStack itemStack = clickSlot.getStack();
        if (!AEApi.instance().definitions().items().viewCell().isSameAs(itemStack)) {
            return super.transferStackInSlot(p, idx);
        }

        // Are we clicking from the player's inventory?
        final boolean isPlayerInventorySlot = this.inventorySlots.get(idx) instanceof SlotPlayerInv || this.inventorySlots.get(idx) instanceof SlotPlayerHotBar;
        if (!isPlayerInventorySlot) {
            return super.transferStackInSlot(p, idx);
        }

        // Attempt to move the item into the view cell storage
        final IItemHandler viewCellInv = ((IViewCellStorage) this.host).getViewCellStorage();
        for (int slot = 0; slot < viewCellInv.getSlots(); slot++) {
            if (viewCellInv.isItemValid(slot, itemStack) && viewCellInv.getStackInSlot(slot).isEmpty()) {
                ItemStack remainder = viewCellInv.insertItem(slot, itemStack, true);
                if (!remainder.isEmpty()) { // That slot can't take the item
                    continue;
                }
                remainder = viewCellInv.insertItem(slot, itemStack, false);
                clickSlot.putStack(remainder);
                this.detectAndSendChanges();
                if (!remainder.isEmpty()) {
                    // How??
                    return super.transferStackInSlot(p, idx);
                }
                return ItemStack.EMPTY;
            }
        }
        return super.transferStackInSlot(p, idx);
    }

    public IGridNode getNetworkNode() {
        return this.networkNode;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            if (this.terminalPinsRequested && this.showCraftingPins && this.terminalPins.getCraftingRows() > 0
                    && (this.craftingPinsDirty || ++this.craftingPinTicker >= 20)) {
                this.craftingPinTicker = 0;
                this.craftingPinsDirty = false;
                this.refreshCraftingPins(false);
            }
            if (this.monitor != this.host.getInventory()) {
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
                        final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

                        for (final GridInventoryEntry entry : changes) {
                            piu.appendItem(entry);
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

            final boolean oldAccessible = this.canAccessViewCells;
            this.canAccessViewCells = this.hasAccess(SecurityPermissions.BUILD, false);
            if (this.canAccessViewCells != oldAccessible) {
                for (int y = 0; y < 5; y++) {
                    if (this.cellView[y] != null) {
                        this.cellView[y].setAllowEdit(this.canAccessViewCells);
                    }
                }
            }

            super.detectAndSendChanges();
        }

    }

    public void requestTerminalPins(boolean showCrafting, boolean showPlayer) {
        if (this.terminalPins == null || !Platform.isServer()) {
            return;
        }
        this.terminalPinsRequested = true;
        this.showCraftingPins = showCrafting;
        this.showPlayerPins = showPlayer;
        this.refreshVisiblePlayerPinKeys();
        this.refreshCraftingPins(true);
    }

    public void receiveTerminalPins(int craftingRows, int playerRows, AEKey[] playerPins,
            List<TerminalCraftingPin> craftingPins) {
        if (!Platform.isClient()) {
            return;
        }
        this.clientCraftingPinRows = craftingRows;
        this.clientPlayerPinRows = playerRows;
        Arrays.fill(this.clientPlayerPins, null);
        System.arraycopy(playerPins, 0, this.clientPlayerPins, 0,
                Math.min(playerPins.length, this.clientPlayerPins.length));
        this.clientCraftingPins = new ArrayList<>(craftingPins);
        this.clientTerminalPinSnapshotVersion++;
    }

    public int getTerminalPinSnapshotVersion() {
        return this.clientTerminalPinSnapshotVersion;
    }

    public int getClientCraftingPinRows() {
        return this.clientCraftingPinRows;
    }

    public int getClientPlayerPinRows() {
        return this.clientPlayerPinRows;
    }

    public AEKey[] getClientPlayerPins() {
        return Arrays.copyOf(this.clientPlayerPins, this.clientPlayerPins.length);
    }

    public List<TerminalCraftingPin> getClientCraftingPins() {
        return new ArrayList<>(this.clientCraftingPins);
    }

    public void setTerminalPinRows(int craftingRows, int playerRows) {
        if (this.terminalPins == null || !Platform.isServer()) {
            return;
        }
        this.terminalPins.setCraftingRows(craftingRows);
        this.terminalPins.setPlayerRows(playerRows);
        this.refreshVisiblePlayerPinKeys();
        this.refreshCraftingPins(true);
    }

    public void setTerminalPlayerPin(int slot, @Nullable AEKey key) {
        if (this.terminalPins == null || !Platform.isServer() || slot < 0
                || slot >= IPlayerTerminalPins.MAX_PINS) {
            return;
        }
        this.terminalPins.setPin(slot, key);
        this.refreshVisiblePlayerPinKeys();
        this.sendTerminalPins();
    }

    private void refreshCraftingPins(boolean force) {
        if (this.terminalPins == null) {
            return;
        }

        final Map<AEKey, long[]> active = new LinkedHashMap<>();
        final Set<CraftingCPUCluster> currentClusters = new HashSet<>();
        if (this.showCraftingPins && this.terminalPins.getCraftingRows() > 0
                && this.networkNode != null && this.networkNode.getGrid() != null) {
            ICraftingGrid craftingGrid = this.networkNode.getGrid().getCache(ICraftingGrid.class);
            if (craftingGrid != null) {
                for (ICraftingCPU cpu : craftingGrid.getCpus()) {
                    if (cpu instanceof CraftingCPUCluster) {
                        CraftingCPUCluster cluster = (CraftingCPUCluster) cpu;
                        currentClusters.add(cluster);
                        if (this.subscribedCraftingCpus.add(cluster)) {
                            cluster.addListener(this, this);
                        }
                    }
                    if (!cpu.isBusy() || cpu.getFinalOutput() == null || !this.isOwnCraftingJob(cpu)) {
                        continue;
                    }
                    AEKey what = cpu.getFinalOutput().what();
                    long[] progress = active.computeIfAbsent(what, ignored -> new long[2]);
                    progress[0] = LongMath.saturatedAdd(progress[0], Math.max(0, cpu.getRemainingItemCount()));
                    progress[1] = LongMath.saturatedAdd(progress[1], Math.max(0, cpu.getStartItemCount()));
                }
            }
        }
        for (CraftingCPUCluster old : new HashSet<>(this.subscribedCraftingCpus)) {
            if (!currentClusters.contains(old)) {
                old.removeListener(this);
                this.subscribedCraftingCpus.remove(old);
            }
        }

        for (Map.Entry<AEKey, TerminalCraftingPin> entry : new ArrayList<>(craftingPins.entrySet())) {
            long[] progress = active.remove(entry.getKey());
            if (progress == null) {
                TerminalCraftingPin old = entry.getValue();
                entry.setValue(new TerminalCraftingPin(entry.getKey(), old.getRemaining(), old.getRequested(), false));
            } else {
                entry.setValue(new TerminalCraftingPin(entry.getKey(), progress[0], progress[1], true));
            }
        }

        int capacity = Math.max(0, Math.min(IPlayerTerminalPins.MAX_PINS,
                this.terminalPins.getCraftingRows() * IPlayerTerminalPins.SLOTS_PER_ROW));
        for (Map.Entry<AEKey, long[]> entry : active.entrySet()) {
            while (craftingPins.size() >= capacity && !craftingPins.isEmpty()) {
                AEKey victim = null;
                for (Map.Entry<AEKey, TerminalCraftingPin> candidate : craftingPins.entrySet()) {
                    if (!candidate.getValue().isActive()) {
                        victim = candidate.getKey();
                        break;
                    }
                }
                craftingPins.remove(victim != null ? victim : craftingPins.keySet().iterator().next());
            }
            if (capacity > 0) {
                long[] progress = entry.getValue();
                craftingPins.put(entry.getKey(), new TerminalCraftingPin(entry.getKey(), progress[0], progress[1], true));
            }
        }
        while (craftingPins.size() > capacity) {
            craftingPins.remove(craftingPins.keySet().iterator().next());
        }

        List<TerminalCraftingPin> current = new ArrayList<>(craftingPins.values());
        if (force || !current.equals(this.lastSentCraftingPins)) {
            this.lastSentCraftingPins = current;
            this.sendTerminalPins();
        }
    }

    private boolean isOwnCraftingJob(ICraftingCPU cpu) {
        EntityPlayer player = this.getInventoryPlayer().player;
        if (cpu.getActionSource() != null && cpu.getActionSource().player().isPresent()
                && player.getPersistentID().equals(cpu.getActionSource().player().get().getPersistentID())) {
            return true;
        }
        return Objects.equals(player.getName(), cpu.getSourcePlayer());
    }

    private void sendTerminalPins() {
        if (!this.terminalPinsRequested || !(this.getInventoryPlayer().player instanceof EntityPlayerMP)) {
            return;
        }
        AEKey[] playerKeys = new AEKey[IPlayerTerminalPins.MAX_PINS];
        if (this.showPlayerPins) {
            for (int i = 0; i < playerKeys.length; i++) {
                playerKeys[i] = this.terminalPins.getPin(i);
            }
        }
        try {
            this.sendPinnedInventoryEntries(playerKeys);
            NetworkHandler.instance().sendTo(PacketTerminalPins.snapshot(
                    this.windowId, this.terminalPins.getCraftingRows(), this.terminalPins.getPlayerRows(), playerKeys,
                    this.showCraftingPins ? craftingPins.values() : Collections.emptyList()),
                    (EntityPlayerMP) this.getInventoryPlayer().player);
        } catch (IOException e) {
            AELog.debug(e);
        }
    }

    private void sendPinnedInventoryEntries(AEKey[] playerKeys) throws IOException {
        Set<AEKey> keys = new LinkedHashSet<>();
        if (this.showCraftingPins) {
            keys.addAll(this.craftingPins.keySet());
        }
        if (this.showPlayerPins) {
            int limit = Math.min(playerKeys.length,
                    this.terminalPins.getPlayerRows() * IPlayerTerminalPins.SLOTS_PER_ROW);
            for (int i = 0; i < limit; i++) {
                if (playerKeys[i] != null) {
                    keys.add(playerKeys[i]);
                }
            }
        }
        if (keys.isEmpty()) {
            return;
        }
        KeyCounter stored = this.readAvailableStacks();
        Set<AEKey> craftables = this.computeCraftables();
        PacketMEInventoryUpdate update = new PacketMEInventoryUpdate();
        for (AEKey what : keys) {
            update.appendItem(new GridInventoryEntry(what, stored.get(what), 0, craftables.contains(what)));
        }
        NetworkHandler.instance().sendTo(update, (EntityPlayerMP) this.getInventoryPlayer().player);
    }

    @Override
    public boolean isValid(Object verificationToken) {
        return verificationToken == this && this.terminalPinsRequested && !this.listeners.isEmpty();
    }

    @Override
    public void onCraftingCPUChange(AEKey what, IActionSource source) {
        this.craftingPinsDirty = true;
    }

    private void unsubscribeCraftingPins() {
        for (CraftingCPUCluster cluster : this.subscribedCraftingCpus) {
            cluster.removeListener(this);
        }
        this.subscribedCraftingCpus.clear();
    }

    /**
     * Builds this tick's delta, merging whichever of case 1 / case 2 (CONTRACT.md §10) applies to this
     * terminal with the craftable-flag diff that applies to both (§8.3).
     */
    private List<GridInventoryEntry> collectChanges() {
        final List<GridInventoryEntry> result = new ArrayList<>();

        final Set<AEKey> craftables = this.computeCraftables();
        final Set<AEKey> newlyCraftable = new HashSet<>();
        final Set<AEKey> noLongerCraftable = new HashSet<>();

        // The same instance means no pattern changed since last tick, so there is no flag diff to compute.
        // This is the common case: patterns change when a player edits them, availability changes constantly.
        if (craftables != this.previousCraftables) {
            newlyCraftable.addAll(craftables);
            newlyCraftable.removeAll(this.previousCraftables);
            noLongerCraftable.addAll(this.previousCraftables);
            noLongerCraftable.removeAll(craftables);
        }

        if (this.networkTerminalPart != null) {
            // Case 1: real push - only the keys the grid actually told us changed, via onStackChange.
            for (final Map.Entry<AEKey, Long> e : this.pendingPushChanges.entrySet()) {
                final AEKey what = e.getKey();
                if (!this.isDisplayedKey(what)) {
                    continue;
                }
                newlyCraftable.remove(what);
                noLongerCraftable.remove(what);
                result.add(new GridInventoryEntry(what, e.getValue(), 0, craftables.contains(what)));
            }
            this.pendingPushChanges.clear();
        } else {
            // Case 2: server-side per-tick diff of a directly-viewed cell (or remote network, for a
            // wireless terminal) snapshot - exactly like upstream's MEStorageMenu.broadcastChanges().
            final KeyCounter current = this.retainAvailableStacks();

            for (final var entry : current) {
                final AEKey what = entry.getKey();
                final long amount = entry.getLongValue();
                if (amount != this.previousAvailableStacks.get(what)) {
                    newlyCraftable.remove(what);
                    noLongerCraftable.remove(what);
                    result.add(new GridInventoryEntry(what, amount, 0, craftables.contains(what)));
                }
            }

            for (final var entry : this.previousAvailableStacks) {
                final AEKey what = entry.getKey();
                if (entry.getLongValue() != 0 && current.get(what) == 0) {
                    newlyCraftable.remove(what);
                    noLongerCraftable.remove(what);
                    result.add(new GridInventoryEntry(what, 0, 0, craftables.contains(what)));
                }
            }

            this.previousAvailableStacks = current;
        }

        // Craftable-only changes: the amount for `what` did not change, but its craftable status did.
        for (final AEKey what : newlyCraftable) {
            if (this.isDisplayedKey(what)) {
                result.add(new GridInventoryEntry(what, this.getCachedAmount(what), 0, true));
            }
        }
        for (final AEKey what : noLongerCraftable) {
            if (this.isDisplayedKey(what)) {
                result.add(new GridInventoryEntry(what, this.getCachedAmount(what), 0, false));
            }
        }

        this.previousCraftables = craftables;

        return result;
    }

    private Set<AEKey> computeCraftables() {
        if (!this.monitorsNetworkInventory()) {
            return Collections.emptySet();
        }

        final ICraftingGrid cc = this.networkNode.getGrid().getCache(ICraftingGrid.class);
        return cc == null ? Collections.emptySet() : cc.getCraftables();
    }

    /**
     * The grid's storage service, but only when this terminal shows the <em>network's</em> contents rather
     * than one cell's. Being attached to a grid is not the same question: a security station and an ME
     * chest have a live node and still monitor their own inventory.
     * <p/>
     * Tested by identity rather than by host type, so an addon terminal handing back the grid's inventory
     * is covered with no change here.
     */
    @Nullable
    private IStorageService networkStorageService() {
        if (this.monitor == null || this.networkNode == null || !this.networkNode.isActive()) {
            return null;
        }

        final IGrid grid = this.networkNode.getGrid();
        if (grid == null) {
            return null;
        }

        final IStorageService ss = grid.getCache(IStorageService.class);
        return ss != null && ss.getInventory() == this.monitor ? ss : null;
    }

    private boolean monitorsNetworkInventory() {
        return this.networkStorageService() != null;
    }

    /**
     * What this terminal shows. <strong>May be the service's own live counter</strong> - anything that
     * outlives the tick has to come from {@link #retainAvailableStacks()} instead.
     */
    private KeyCounter readAvailableStacks() {
        final IStorageService ss = this.networkStorageService();
        return ss != null ? ss.getCachedInventory() : this.monitor.getAvailableStacks();
    }

    /**
     * The same listing, but always this container's own object. Keeping the service's live counter instead
     * would leave the diff comparing it with itself, and the terminal would silently stop updating.
     */
    private KeyCounter retainAvailableStacks() {
        final IStorageService ss = this.networkStorageService();
        final KeyCounter available = ss == null ? this.monitor.getAvailableStacks() : ss.getCachedInventory();
        final KeyCounter copy = new KeyCounter();
        for (final var entry : available) {
            if (this.isDisplayedKey(entry.getKey())) {
                copy.set(entry.getKey(), entry.getLongValue());
            }
        }
        return copy;
    }

    private boolean isKeyTypeVisible(final AEKey what) {
        return !(this.host instanceof KeyTypeSelectionHost)
                || ((KeyTypeSelectionHost) this.host).getKeyTypeSelection().enabledPredicate().test(what.getType());
    }

    private boolean isDisplayedKey(final AEKey what) {
        boolean typeVisible = this.isKeyTypeVisible(what);
        if (typeVisible || this.terminalPins == null || !this.terminalPinsRequested) {
            return typeVisible;
        }
        if (this.showCraftingPins && this.craftingPins.containsKey(what)) {
            return true;
        }
        return this.showPlayerPins && this.visiblePlayerPinKeys.contains(what);
    }

    private void refreshVisiblePlayerPinKeys() {
        this.visiblePlayerPinKeys.clear();
        if (this.terminalPins == null || !this.showPlayerPins) {
            return;
        }
        int limit = Math.min(IPlayerTerminalPins.MAX_PINS,
                this.terminalPins.getPlayerRows() * IPlayerTerminalPins.SLOTS_PER_ROW);
        for (int i = 0; i < limit; i++) {
            AEKey key = this.terminalPins.getPin(i);
            if (key != null) {
                this.visiblePlayerPinKeys.add(key);
            }
        }
    }

    private long getCachedAmount(final AEKey what) {
        if (this.monitor == null) {
            return 0;
        }

        return this.readAvailableStacks().get(what);
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
        } catch (final Throwable t) {
            // :P
        }
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("canAccessViewCells")) {
            for (int y = 0; y < 5; y++) {
                if (this.cellView[y] != null) {
                    this.cellView[y].setAllowEdit(this.canAccessViewCells);
                }
            }
        }

        super.onUpdate(field, oldValue, newValue);
    }

    @Override
    public void addListener(final IContainerListener c) {
        super.addListener(c);

        if (Platform.isServer() && c instanceof EntityPlayerMP && this.terminalPins != null
                && !this.terminalPinsRequested) {
            this.terminalPinsRequested = true;
            this.showCraftingPins = true;
            this.showPlayerPins = true;
            this.refreshVisiblePlayerPinKeys();
            this.refreshCraftingPins(true);
        }
        this.queueInventory(c);
    }

    private void queueInventory(final IContainerListener c) {
        if (Platform.isServer() && c instanceof EntityPlayer && this.monitor != null) {
            try {
                PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();
                final Set<AEKey> craftables = this.computeCraftables();

                // The union, not just what is in stock. A key the network can craft but does not hold has
                // no entry in getAvailableStacks(), and the constructor already seeded previousCraftables,
                // so collectChanges() sees no *change* to report either - the row would never be sent at
                // all, and the terminal would only ever show craftables that happened to be stocked once
                // while it was open.
                final KeyCounter stored = this.readAvailableStacks();
                final Set<AEKey> keys = new LinkedHashSet<>(stored.keySet());
                keys.addAll(craftables);

                for (final AEKey what : keys) {
                    if (!this.isDisplayedKey(what)) {
                        continue;
                    }
                    final GridInventoryEntry send = new GridInventoryEntry(what, stored.get(what), 0, craftables.contains(what));
                    try {
                        piu.appendItem(send);
                    } catch (final BufferOverflowException boe) {
                        NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);

                        piu = new PacketMEInventoryUpdate();
                        piu.appendItem(send);
                    }
                }

                NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    @Override
    public void removeListener(final IContainerListener c) {
        super.removeListener(c);

        if (this.listeners.isEmpty() && this.networkTerminalPart != null) {
            this.networkTerminalPart.removeTerminalListener(this);
        }
        if (this.listeners.isEmpty()) {
            this.unsubscribeCraftingPins();
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        if (this.networkTerminalPart != null) {
            this.networkTerminalPart.removeTerminalListener(this);
        }
        this.unsubscribeCraftingPins();
    }

    /**
     * Called on the server only, relayed from {@link AbstractPartTerminal#onStackChange(AEKey, long)} - see
     * {@link #networkTerminalPart}. Not called directly by the grid: this container is not itself a grid
     * machine, so {@link #updateWatcher(IStackWatcher)} below is never invoked in practice.
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

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (this.getGui() != null) {
            this.getGui().updateSetting(manager, settingName, newValue);
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        if (Platform.isServer()) {
            return this.serverCM;
        }
        return this.clientCM;
    }

    public ItemStack[] getViewCells() {
        final ItemStack[] list = new ItemStack[this.cellView.length];

        for (int x = 0; x < this.cellView.length; x++) {
            list[x] = this.cellView[x].getStack();
        }

        return list;
    }

    public SlotRestrictedInput getCellViewSlot(final int index) {
        return this.cellView[index];
    }

    public boolean isPowered() {
        return this.hasPower;
    }

    private void setPowered(final boolean isPowered) {
        this.hasPower = isPowered;
    }

    public IConfigManagerHost getGui() {
        return this.gui;
    }

    public void setGui(@Nonnull final IConfigManagerHost gui) {
        this.gui = gui;
    }

    public void postUpdate(final List<GridInventoryEntry> list) {
        ((GuiMEMonitorable) this.gui).postUpdate(list);
    }
}
