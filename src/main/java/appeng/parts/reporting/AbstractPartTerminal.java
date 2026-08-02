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

package appeng.parts.reporting;


import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalPinHost;
import appeng.api.storage.ITerminalPinStorage;
import appeng.api.storage.MEStorage;
import appeng.api.storage.TerminalPinStorages;
import appeng.api.util.IConfigManager;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.core.sync.GuiBridge;
import appeng.helpers.ISubMenuHost;
import appeng.me.GridAccessException;
import appeng.me.storage.NullInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;


/**
 * Anything resembling an network terminal with view cells can reuse this.
 * <p>
 * Note this applies only to terminals like the ME Terminal. It does not apply for more specialized terminals like the
 * Interface Terminal.
 *
 * @author AlgorithmX2
 * @author yueh
 * @version rv3
 * @since rv3
 */
public abstract class AbstractPartTerminal extends AbstractPartDisplay implements ITerminalHost, IConfigManagerHost,
        IViewCellStorage, IAEAppEngInventory, IStorageWatcherNode, KeyTypeSelectionHost, ISubMenuHost,
        ITerminalPinHost {

    private final IConfigManager cm = new ConfigManager(this);
    private final AppEngInternalInventory viewCell = new AppEngInternalInventory(this, 5);
    private final KeyTypeSelection keyTypeSelection = new KeyTypeSelection(() -> this.getHost().markForSave(), type -> true);
    private final ITerminalPinStorage terminalPinStorage = TerminalPinStorages.forHost(
            () -> this.getHost().markForSave());

    /**
     * Wave 4 addition (CONTRACT.md §10, "Third case: terminal live updates", case 1 - "network-backed
     * terminals, real push"). {@link IStorageWatcherNode}/{@link IStackWatcher} are registered per grid
     * *node*: {@code GridStorageCache.addNode} only ever calls {@link #updateWatcher(IStackWatcher)} on the
     * node's machine, which is this part, never on whichever {@code ContainerMEMonitorable} happens to have
     * it open (a container isn't a grid machine and has no node of its own). So this part is the one thing
     * that can hold a live {@link IStackWatcher}, and it relays {@link #onStackChange(AEKey, long)} to every
     * currently-open terminal container on this part. Without this, every terminal built on
     * {@code AbstractPartTerminal} (the plain ME terminal, crafting terminal, pattern terminal and expanded
     * processing pattern terminal) would have no live update mechanism at all - a real mechanic loss, not
     * merely a missed optimisation, which is why this file was touched outside wave 4's assigned list; see
     * the wave 4c contract entry for the full rationale.
     */
    private final List<IStorageWatcherNode> terminalListeners = new ArrayList<>();
    private IStackWatcher myWatcher;

    public AbstractPartTerminal(final ItemStack is) {
        super(is);

        this.cm.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.cm.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.cm.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        super.getDrops(drops, wrenched);

        for (final ItemStack is : this.viewCell) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.cm;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.cm.readFromNBT(data);
        this.viewCell.readFromNBT(data, "viewCell");
        this.keyTypeSelection.readFromNBT(data);
        this.terminalPinStorage.readFromNBT(data, "terminalPins");
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.cm.writeToNBT(data);
        this.viewCell.writeToNBT(data, "viewCell");
        this.keyTypeSelection.writeToNBT(data);
        this.terminalPinStorage.writeToNBT(data, "terminalPins");
    }

    @Override
    public ITerminalPinStorage getTerminalPinStorage() {
        return this.terminalPinStorage;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (!super.onPartActivate(player, hand, pos)) {
            if (Platform.isServer()) {
                Platform.openGUI(player, this.getHost().getTile(), this.getSide(), this.getGui(player));
            }
        }
        return true;
    }

    public GuiBridge getGui(final EntityPlayer player) {
        return GuiBridge.GUI_ME;
    }

    @Override
    public GuiBridge getGuiBridge() {
        return GuiBridge.GUI_ME;
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return this.getItemStack();
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return this.keyTypeSelection;
    }

    @Override
    public boolean requiresBuildPermissionForKeyTypeSelection() {
        return false;
    }

    @Override
    public MEStorage getInventory() {
        try {
            return this.getProxy().getStorage().getInventory();
        } catch (final GridAccessException e) {
            // err nope?
        }
        return NullInventory.of();
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {

    }

    @Override
    public IItemHandler getViewCellStorage() {
        return this.viewCell;
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc, final ItemStack removedStack, final ItemStack newStack) {
        this.getHost().markForSave();
    }

    /**
     * Called once when the node (re)joins the grid. Kept for as long as the node stays in the grid, then
     * reused across however many terminal GUIs open and close on it in the meantime - {@code setWatchAll}
     * is toggled by {@link #addTerminalListener}/{@link #removeTerminalListener}, not by this method.
     */
    @Override
    public void updateWatcher(final IStackWatcher newWatcher) {
        this.myWatcher = newWatcher;
        if (this.myWatcher != null) {
            this.myWatcher.setWatchAll(!this.terminalListeners.isEmpty());
        }
    }

    @Override
    public void onStackChange(final AEKey what, final long amount) {
        for (final IStorageWatcherNode listener : this.terminalListeners) {
            listener.onStackChange(what, amount);
        }
    }

    /**
     * Attaches an open terminal container so it starts receiving {@link #onStackChange(AEKey, long)} calls.
     * Safe to call more than once for the same listener has no effect beyond the first.
     */
    public void addTerminalListener(final IStorageWatcherNode listener) {
        if (!this.terminalListeners.contains(listener)) {
            this.terminalListeners.add(listener);
            if (this.myWatcher != null) {
                this.myWatcher.setWatchAll(true);
            }
        }
    }

    /**
     * Detaches a closed terminal container. Once the last one is gone, the watcher is told to stop watching
     * everything, so an unopened terminal does not force a full-network cache rebuild every tick.
     */
    public void removeTerminalListener(final IStorageWatcherNode listener) {
        if (this.terminalListeners.remove(listener) && this.myWatcher != null) {
            this.myWatcher.setWatchAll(!this.terminalListeners.isEmpty());
        }
    }
}
