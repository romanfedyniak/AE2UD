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

import appeng.api.upgrades.UpgradeCards;


import appeng.api.AEApi;
import appeng.api.config.*;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.storage.cells.StorageCell;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.parts.automation.BlockUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.UpgradeSpeedCalculations;
import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.InvOperation;
import appeng.util.inv.WrapperChainedItemHandler;
import appeng.util.inv.WrapperFilteredItemHandler;
import appeng.util.inv.filter.AEItemFilters;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import java.io.IOException;
import java.util.List;


public class TileIOPort extends AENetworkInvTile implements IUpgradeableHost, IConfigManagerHost, IGridTickable {
    private static final int NUMBER_OF_CELL_SLOTS = 6;
    private static final int NUMBER_OF_UPGRADE_SLOTS = 3;

    private final ConfigManager manager;

    private final AppEngInternalInventory inputCells = new AppEngInternalInventory(this, NUMBER_OF_CELL_SLOTS, 1);
    private final AppEngInternalInventory outputCells = new AppEngInternalInventory(this, NUMBER_OF_CELL_SLOTS, 1);
    private final IItemHandler combinedInventory = new WrapperChainedItemHandler(this.inputCells, this.outputCells);

    private final IItemHandler inputCellsExt = new WrapperFilteredItemHandler(this.inputCells, AEItemFilters.INSERT_ONLY);
    private final IItemHandler outputCellsExt = new WrapperFilteredItemHandler(this.outputCells, AEItemFilters.EXTRACT_ONLY);

    private final UpgradeInventory upgrades;
    private final IActionSource mySrc;
    private YesNo lastRedstoneState;
    private ItemStack currentCell;
    private StorageCell cachedCell;

    private boolean isActive = false;

    public TileIOPort() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.manager = new ConfigManager(this);
        this.manager.registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.manager.registerSetting(Settings.FULLNESS_MODE, FullnessMode.EMPTY);
        this.manager.registerSetting(Settings.OPERATION_MODE, OperationMode.EMPTY);
        this.mySrc = new MachineSource(this);
        this.lastRedstoneState = YesNo.UNDECIDED;

        final Block ioPortBlock = AEApi.instance().definitions().blocks().iOPort().maybeBlock().get();
        this.upgrades = new BlockUpgradeInventory(ioPortBlock, this, NUMBER_OF_UPGRADE_SLOTS);
    }

    @MENetworkEventSubscribe
    public void onPower(final MENetworkPowerStatusChange ch) {
        this.markForUpdate();
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.manager.writeToNBT(data);
        this.upgrades.writeToNBT(data, "upgrades");
        data.setInteger("lastRedstoneState", this.lastRedstoneState.ordinal());
        return data;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.manager.readFromNBT(data);
        this.upgrades.readFromNBT(data, "upgrades");
        if (data.hasKey("lastRedstoneState")) {
            this.lastRedstoneState = YesNo.values()[data.getInteger("lastRedstoneState")];
        }
    }

    @Override
    protected boolean readFromStream(ByteBuf data) throws IOException {
        boolean c = super.readFromStream(data);

        final boolean oldIsActive = this.isActive;
        this.isActive = data.readBoolean();
        return oldIsActive != this.isActive || c;
    }

    @Override
    protected void writeToStream(ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeBoolean(this.isActive());
    }

    public boolean isActive() {
        if (Platform.isServer()) {
            try {
                return this.getProxy().getEnergy().isNetworkPowered();
            } catch (GridAccessException e) {
                return false;
            }
        }
        return this.isActive;
    }

    @Override
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    private void updateTask() {
        try {
            if (this.hasWork()) {
                this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
            } else {
                this.getProxy().getTick().sleepDevice(this.getProxy().getNode());
            }
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    public void gridChanged() {
        super.gridChanged();
        updateTask();
    }

    public void updateRedstoneState() {
        final YesNo currentState = this.world.getRedstonePowerFromNeighbors(this.pos) != 0 ? YesNo.YES : YesNo.NO;
        if (this.lastRedstoneState != currentState) {
            this.lastRedstoneState = currentState;
            this.updateTask();
            if (currentState == YesNo.YES) {
                if (this.manager.getSetting(Settings.REDSTONE_CONTROLLED) == RedstoneMode.SIGNAL_PULSE) {
                    this.doWork();
                }
            }
        }
    }

    private boolean getRedstoneState() {
        if (this.lastRedstoneState == YesNo.UNDECIDED) {
            this.updateRedstoneState();
        }

        return this.lastRedstoneState == YesNo.YES;
    }

    private boolean isEnabled() {
        if (this.getInstalledUpgrades(UpgradeCards.redstone()) == 0) {
            return true;
        }
        final RedstoneMode rs = (RedstoneMode) this.manager.getSetting(Settings.REDSTONE_CONTROLLED);
        switch (rs) {
            case IGNORE:
                return true;

            case HIGH_SIGNAL:
                return this.getRedstoneState();

            case LOW_SIGNAL:
                return !this.getRedstoneState();

            case SIGNAL_PULSE:
            default:
                return false;
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.manager;
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        if (name.equals("upgrades")) {
            return this.upgrades;
        }

        if (name.equals("cells")) {
            return this.combinedInventory;
        }

        return null;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        this.updateTask();
    }

    private boolean hasWork() {
        if (this.isEnabled()) {
            return !ItemHandlerUtil.isEmpty(this.inputCells);
        }

        return false;
    }

    @Override
    public IItemHandler getInternalInventory() {
        return this.combinedInventory;
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc, final ItemStack removed, final ItemStack added) {
        if (this.inputCells == inv) {
            this.updateTask();
        }
    }

    @Override
    protected IItemHandler getItemHandlerForSide(final EnumFacing facing) {
        if (facing == this.getUp() || facing == this.getUp().getOpposite()) {
            return this.inputCellsExt;
        } else {
            return this.outputCellsExt;
        }
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.IOPort.getMin(), TickRates.IOPort.getMax(), !this.hasWork(), false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!this.getProxy().isActive()) {
            return TickRateModulation.IDLE;
        }
        return this.doWork();
    }

    private TickRateModulation doWork() {
        TickRateModulation ret = TickRateModulation.SLEEP;
        long itemsToMove = UpgradeSpeedCalculations.ioPortTransferLimit(this.getInstalledSpeedPoints());

        try {
            final IEnergySource energy = this.getProxy().getEnergy();
            for (int x = 0; x < NUMBER_OF_CELL_SLOTS; x++) {
                final ItemStack is = this.inputCells.getStackInSlot(x);
                if (!is.isEmpty()) {
                    boolean shouldMove = true;

                    if (itemsToMove > 0) {
                        final MEStorage network = this.getProxy().getStorage().getInventory();
                        final StorageCell inv = this.getInv(is);

                        if (inv != null) {
                            final AEKeyType keyType = is.getItem() instanceof ICellWorkbenchItem workbenchItem
                                    ? workbenchItem.getKeyType()
                                    : AEKeyType.items();

                            if (this.manager.getSetting(Settings.OPERATION_MODE) == OperationMode.EMPTY) {
                                itemsToMove = this.transferContents(energy, inv, network, itemsToMove, keyType);
                            } else {
                                itemsToMove = this.transferContents(energy, network, inv, itemsToMove, keyType);
                            }

                            shouldMove = this.shouldMove(inv);

                            if (itemsToMove > 0) {
                                ret = TickRateModulation.IDLE;
                            } else {
                                ret = TickRateModulation.URGENT;
                            }
                        }
                    }

                    if (itemsToMove > 0 && shouldMove && this.moveSlot(x)) {
                        ret = TickRateModulation.URGENT;
                    } else {
                        ret = TickRateModulation.URGENT;
                    }

                }
            }
        } catch (final GridAccessException e) {
            ret = TickRateModulation.IDLE;
        }

        return ret;
    }

    @Override
    public int getInstalledUpgrades(final ItemStack upgradeCard) {
        return this.upgrades.getInstalledUpgrades(upgradeCard);
    }

    @Override
    public int getInstalledSpeedPoints() {
        return this.upgrades.getInstalledSpeedPoints();
    }

    @Override
    public int getInstalledCapacityPoints() {
        return this.upgrades.getInstalledCapacityPoints();
    }

    /**
     * A {@link StorageCell} is not tied to one channel any more, so unlike the old per-channel
     * {@code Map<IStorageChannel<?>, IMEInventory<?>>} there is only ever a single cached cell to resolve here.
     */
    private StorageCell getInv(final ItemStack is) {
        if (this.currentCell != is) {
            this.currentCell = is;
            this.cachedCell = StorageCells.getCellInventory(is, null);
        }

        return this.cachedCell;
    }

    private long transferContents(final IEnergySource energy, final MEStorage src, final MEStorage destination, long itemsToMove, final AEKeyType keyType) {
        final KeyCounter myList = src.getAvailableStacks();

        itemsToMove *= keyType.getAmountPerOperation();

        boolean didStuff;

        do {
            didStuff = false;

            for (final var entry : myList) {
                final AEKey what = entry.getKey();
                final long totalStackSize = entry.getLongValue();
                if (totalStackSize > 0) {
                    final long insertable = destination.insert(what, totalStackSize, Actionable.SIMULATE, this.mySrc);

                    if (insertable > 0) {
                        final long possibleBeforeExtract = Math.min(insertable, itemsToMove);

                        final long extracted = src.extract(what, possibleBeforeExtract, Actionable.MODULATE, this.mySrc);
                        if (extracted > 0) {
                            final long actuallyInserted = Platform.poweredInsert(energy, destination, what, extracted, this.mySrc);
                            final long failed = extracted - actuallyInserted;

                            if (failed > 0) {
                                src.insert(what, failed, Actionable.MODULATE, this.mySrc);
                            }

                            if (actuallyInserted > 0) {
                                itemsToMove -= actuallyInserted;
                                didStuff = true;
                            }

                            break;
                        }
                    }
                }
            }
        }
        while (itemsToMove > 0 && didStuff);

        return itemsToMove / keyType.getAmountPerOperation();
    }

    private boolean shouldMove(final MEStorage inv) {
        final FullnessMode fm = (FullnessMode) this.manager.getSetting(Settings.FULLNESS_MODE);

        if (inv != null) {
            return this.matches(fm, inv);
        }

        return true;
    }

    private boolean moveSlot(final int x) {
        final InventoryAdaptor ad = new AdaptorItemHandler(this.outputCells);
        if (ad.addItems(this.inputCells.getStackInSlot(x)).isEmpty()) {
            this.inputCells.setStackInSlot(x, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    private boolean matches(final FullnessMode fm, final MEStorage src) {
        if (fm == FullnessMode.HALF) {
            return true;
        }

        final KeyCounter myList = src.getAvailableStacks();

        if (fm == FullnessMode.EMPTY) {
            return myList.isEmpty();
        }

        final AEKey test = myList.getFirstKey();
        if (test != null) {
            return src.insert(test, 1, Actionable.SIMULATE, this.mySrc) <= 0;
        }
        return false;
    }

    /**
     * Adds the items in the upgrade slots to the drop list.
     *
     * @param w     world
     * @param x     x pos of tile entity
     * @param y     y pos of tile entity
     * @param z     z pos of tile entity
     * @param drops drops of tile entity
     */
    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        super.getDrops(w, pos, drops);

        for (int upgradeIndex = 0; upgradeIndex < this.upgrades.getSlots(); upgradeIndex++) {
            final ItemStack stackInSlot = this.upgrades.getStackInSlot(upgradeIndex);

            if (!stackInSlot.isEmpty()) {
                drops.add(stackInSlot);
            }
        }
    }
}
