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

package appeng.tile.storage;


import appeng.api.AEApi;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.GridFlags;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.DriveWatcher;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngCellInventory;
import appeng.util.Platform;
import appeng.util.inv.InvOperation;
import appeng.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import java.io.IOException;


public class TileDrive extends AENetworkInvTile implements IChestOrDrive, IPriorityHost, ISaveProvider {

    private final AppEngCellInventory inv = new AppEngCellInventory(this, 10);
    private final DriveWatcher[] invBySlot = new DriveWatcher[10];
    private final IActionSource mySrc;
    private int priority = 0;
    private boolean wasActive = false;

    /**
     * The state of all cells inside a drive as bitset, using the following format.
     * <p>
     * Bit 29-0: 3 bits as state of each cell with the cell in slot 0 located in the 3 least significant bits.
     * <p>
     * Cell states:
     * Bit 2-0: cell status, representing {@link appeng.block.storage.DriveSlotState}.
     */
    private int cellState = 0;
    private boolean powered;
    // bit index corresponds to cell index
    private int blinking;

    public TileDrive() {
        this.mySrc = new MachineSource(this);
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.inv.setFilter(new CellValidInventoryFilter());
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);

        int newState = 0;
        for (int x = 0; x < this.getCellCount(); x++) {
            newState |= (this.getCellStatus(x) << (3 * x));
        }

        data.writeInt(newState);
        data.writeBoolean(this.getProxy().isActive());
        data.writeInt(this.blinking);
    }

    @Override
    protected boolean readFromStream(final ByteBuf data) throws IOException {
        final boolean c = super.readFromStream(data);
        final int oldCellState = this.cellState;
        final boolean oldPowered = this.powered;
        final int oldBlinking = this.blinking;
        this.cellState = data.readInt();
        this.powered = data.readBoolean();
        this.blinking = data.readInt();
        return oldCellState != this.cellState || oldPowered != this.powered || oldBlinking != this.blinking || c;
    }

    @Override
    public int getCellCount() {
        return 10;
    }

    @Override
    public int getCellStatus(final int slot) {
        if (Platform.isClient()) {
            return (this.cellState >> (slot * 3)) & 0b111;
        }

        final DriveWatcher watcher = this.invBySlot[slot];
        if (watcher == null) {
            return 0;
        }

        return cellStateToStatus(watcher.getStatus());
    }

    /**
     * Maps the new {@link CellState} back onto the 0-4 scale {@link appeng.block.storage.DriveSlotState} and the
     * client-side rendering still expect: 0 no cell, 1 online/has room, 2 types full, 3 full, 4 online but empty.
     */
    private static int cellStateToStatus(final CellState state) {
        switch (state) {
            case EMPTY:
                return 4;
            case NOT_EMPTY:
                return 1;
            case TYPES_FULL:
                return 2;
            case FULL:
                return 3;
            case ABSENT:
            default:
                return 0;
        }
    }

    @Override
    public boolean isPowered() {
        if (Platform.isClient()) {
            return this.powered;
        }

        return this.getProxy().isActive();
    }

    @Override
    public boolean isCellBlinking(final int slot) {
        return (this.blinking & (1 << slot)) == 1;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.priority = data.getInteger("priority");
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("priority", this.priority);
        return data;
    }

    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.recalculateDisplay();
    }

    private void recalculateDisplay() {
        final boolean currentActive = this.getProxy().isActive();
        final int oldCellState = this.cellState;
        final boolean oldPowered = this.powered;

        this.powered = currentActive;

        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            try {
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
            } catch (final GridAccessException e) {
                // :P
            }
        }

        for (int x = 0; x < this.getCellCount(); x++) {
            cellState |= (this.getCellStatus(x) << (3 * x));
        }

        if (oldCellState != this.cellState || oldPowered != this.powered) {
            this.markForUpdate();
        }
    }

    @MENetworkEventSubscribe
    public void channelRender(final MENetworkChannelsChanged c) {
        this.recalculateDisplay();
    }

    @Override
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public IItemHandler getInternalInventory() {
        return this.inv;
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc, final ItemStack removed, final ItemStack added) {
        try {
            if (this.getProxy().isActive()) {
                final IStorageService gs = this.getProxy().getStorage();
                Platform.postChanges(gs, removed, added, this.mySrc);
            }
            this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException ignored) {
        }

        IStorageProvider.requestUpdate(this.getProxy().getNode());

        this.markForUpdate();
    }

    /**
     * Called back by the network whenever it (re)builds its storage: mounts every non-empty, recognised cell in
     * this drive, watching each one so the slot's status light and blink animation keep working, exactly as the
     * old per-channel {@code getCellArray} used to.
     */
    @Override
    public void mountInventories(final IStorageMounts storageMounts) {
        double power = 2.0;

        for (int x = 0; x < this.inv.getSlots(); x++) {
            final ItemStack is = this.inv.getStackInSlot(x);
            this.invBySlot[x] = null;

            if (!is.isEmpty()) {
                final StorageCell cell = StorageCells.getCellInventory(is, this);

                if (cell != null) {
                    this.inv.setHandler(x, cell);
                    power += cell.getIdleDrain();

                    final int slot = x;
                    final DriveWatcher watcher = new DriveWatcher(cell, () -> this.blinkCell(slot));
                    this.invBySlot[x] = watcher;

                    storageMounts.mount(watcher, this.priority);
                }
            }
        }

        this.getProxy().setIdlePowerUsage(power);
    }

    @Override
    public void onReady() {
        super.onReady();
        IStorageProvider.requestUpdate(this.getProxy().getNode());
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.saveChanges();

        IStorageProvider.requestUpdate(this.getProxy().getNode());

        try {
            this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    /**
     * No longer an {@code @Override}: {@code blinkCell} lived on the old {@code ICellContainer}, which
     * {@link IStorageProvider} does not carry forward. Called directly by the {@link DriveWatcher}s this tile
     * creates in {@link #mountInventories(IStorageMounts)}.
     */
    public void blinkCell(final int slot) {
        this.blinking |= (1 << slot);

        this.recalculateDisplay();
    }

    private class CellValidInventoryFilter implements IAEItemFilter {

        @Override
        public boolean allowExtract(IItemHandler inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(IItemHandler inv, int slot, ItemStack stack) {
            return !stack.isEmpty() && StorageCells.isCellHandled(stack);
        }

    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return AEApi.instance().definitions().blocks().drive().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public GuiBridge getGuiBridge() {
        return GuiBridge.GUI_DRIVE;
    }
}
