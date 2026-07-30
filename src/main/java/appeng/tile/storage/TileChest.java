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
import appeng.api.config.*;
import appeng.api.implementations.tiles.IColorableTile;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.*;
import appeng.api.networking.events.MENetworkPowerStorage.PowerEventType;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ICellGuiHandler;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.capabilities.Capabilities;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.DriveWatcher;
import appeng.me.storage.NullInventory;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.inv.InvOperation;
import appeng.util.inv.WrapperChainedItemHandler;
import appeng.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;


public class TileChest extends AENetworkPowerTile implements IMEChest, ITerminalHost, IPriorityHost, IConfigManagerHost, IColorableTile, ISaveProvider, ITickable {
    private final AppEngInternalInventory inputInventory = new AppEngInternalInventory(this, 1);
    private final AppEngInternalInventory cellInventory = new AppEngInternalInventory(this, 1);
    private final IItemHandler internalInventory = new WrapperChainedItemHandler(this.inputInventory, this.cellInventory);

    private final IActionSource mySrc = new MachineSource(this);
    private final IConfigManager config = new ConfigManager(this);
    private long lastStateChange = 0;
    private int priority = 0;
    private boolean wasActive = false;
    private AEColor paintedColor = AEColor.TRANSPARENT;
    private boolean isCached = false;
    // the raw cell watcher, kept around for status/idle-drain queries
    private DriveWatcher driveWatcher;
    // the security-checked MEStorage actually mounted into the network / handed out through capabilities
    private MEStorage cellStorage;
    private AEKeyType cellKeyType;
    private Accessor accessor;
    private IFluidHandler fluidHandler;

    // Client-sided caches for rendering
    // state value holds 3 bits per drive
    private int cellState;
    private boolean powered = false;
    // bit index corresponds to cell index
    private int blinking;

    public TileChest() {
        this.setInternalMaxPower(PowerMultiplier.CONFIG.multiply(128));
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.config.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.config.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.config.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);

        this.setInternalPublicPowerStorage(true);
        this.setInternalPowerFlow(AccessRestriction.WRITE);

        this.inputInventory.setFilter(new InputInventoryFilter());
        this.cellInventory.setFilter(new CellInventoryFilter());
    }

    public ItemStack getCell() {
        return this.cellInventory.getStackInSlot(0);
    }

    @Override
    protected void PowerEvent(final PowerEventType x) {
        if (x == PowerEventType.REQUEST_POWER) {
            try {
                this.getProxy().getGrid().postEvent(new MENetworkPowerStorage(this, PowerEventType.REQUEST_POWER));
            } catch (final GridAccessException e) {
                // :(
            }
        } else {
            this.recalculateDisplay();
        }
    }

    private void recalculateDisplay() {
        final int oldCellState = this.cellState;
        final boolean oldPowered = this.powered;

        for (int x = 0; x < this.getCellCount(); x++) {
            this.cellState |= (this.getCellStatus(x) << (3 * x));
        }

        this.powered = this.isPowered();

        final boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            try {
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
            } catch (final GridAccessException e) {
                // :P
            }
        }

        if (oldCellState != this.cellState || oldPowered != this.powered) {
            this.markForUpdate();
        }
    }

    @Override
    public int getCellCount() {
        return 1;
    }

    /**
     * Rebuilds {@link #driveWatcher}/{@link #cellStorage}/{@link #cellKeyType} from whatever is in the cell slot.
     * Replaces the old per-channel loop over {@code AEApi.instance().storage().storageChannels()}: a
     * {@link StorageCell} is not tied to one channel any more, so there is only ever at most one to build. The
     * cell's type (used to gate item vs. fluid access) is read off the cell item itself via
     * {@link IBasicCellItem#getKeyType()}; a cell item that does not declare one (the creative cell, which has
     * always behaved as an item cell here) defaults to {@link AEKeyType#items()}.
     */
    /**
     * Writes the installed cell's contents back into its {@link ItemStack}.
     * <p>
     * A cell reports a change by calling {@link ISaveProvider#saveChanges()} on whatever holds it, and only
     * then does its NBT get rewritten. The pre-port signature was
     * {@code saveChanges(ICellInventory<?> cellInventory)} and this class used the argument to call
     * {@code persist()} on it; the ported interface takes no argument (matching upstream), so the override
     * quietly became {@code AEBaseTile.saveChanges()}, which only marks the chunk dirty. Everything put into
     * a chest lived in the cell's in-memory inventory and was never written down, so pulling the cell out
     * lost the lot and its tooltip always read empty.
     * <p>
     * The drive was unaffected because it holds its cells in an {@code AppEngCellInventory}, which persists
     * them itself.
     */
    @Override
    public void saveChanges() {
        if (this.driveWatcher != null) {
            this.driveWatcher.getCell().persist();
        }
        super.saveChanges();
    }

    private void updateHandler() {
        if (!this.isCached) {
            this.driveWatcher = null;
            this.cellStorage = null;
            this.cellKeyType = null;
            this.fluidHandler = null;
            this.accessor = null;

            final ItemStack is = this.getCell();
            if (!is.isEmpty()) {
                this.isCached = true;
                final StorageCell cell = StorageCells.getCellInventory(is, this);

                if (cell != null) {
                    this.getProxy().setIdlePowerUsage(1.0 + cell.getIdleDrain());

                    this.driveWatcher = new DriveWatcher(cell, () -> this.blinkCell(0));
                    this.cellStorage = new SecurityAwareCellStorage(this.driveWatcher);
                    this.cellKeyType = is.getItem() instanceof IBasicCellItem basicCellItem
                            ? basicCellItem.getKeyType()
                            : AEKeyType.items();

                    this.accessor = new Accessor();

                    if (this.cellKeyType == AEKeyType.fluids()) {
                        this.fluidHandler = new FluidHandler();
                    }
                }
            }
        }
    }

    /**
     * Maps the new {@link CellState} back onto the 0-4 scale the client-side rendering still expects. Identical
     * to {@code TileDrive}'s private mapping of the same name.
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
    public int getCellStatus(final int slot) {
        if (Platform.isClient()) {
            return (this.cellState >> (slot * 3)) & 0b111;
        }

        this.updateHandler();

        if (this.driveWatcher == null) {
            return 0;
        }

        return cellStateToStatus(this.driveWatcher.getStatus());
    }

    @Override
    public boolean isPowered() {
        if (Platform.isClient()) {
            return this.powered;
        }

        boolean gridPowered = this.getAECurrentPower() > 64;

        if (!gridPowered) {
            try {
                gridPowered = this.getProxy().getEnergy().isNetworkPowered();
            } catch (final GridAccessException ignored) {
            }
        }

        return super.getAECurrentPower() > 1 || gridPowered;
    }

    @Override
    public boolean isCellBlinking(final int slot) {
        final long now = this.world.getTotalWorldTime();
        if (now - this.lastStateChange > 8) {
            return false;
        }

        return (this.blinking & (1 << slot)) == 1;
    }

    @Override
    protected double extractAEPower(final double amt, final Actionable mode) {
        double stash = 0.0;

        try {
            final IEnergyGrid eg = this.getProxy().getEnergy();
            stash = eg.extractAEPower(amt, mode, PowerMultiplier.ONE);
            if (stash >= amt) {
                return stash;
            }
        } catch (final GridAccessException e) {
            // no grid :(
        }

        // local battery!
        return super.extractAEPower(amt - stash, mode) + stash;
    }

    @Override
    public void update() {
        if (this.world.isRemote) {
            return;
        }

        final double idleUsage = this.getProxy().getIdlePowerUsage();

        try {
            if (!this.getProxy().getEnergy().isNetworkPowered()) {
                final double powerUsed = this.extractAEPower(idleUsage, Actionable.MODULATE, PowerMultiplier.CONFIG); // drain
                if (powerUsed + 0.1 >= idleUsage != this.powered) {
                    this.recalculateDisplay();
                }
            }
        } catch (final GridAccessException e) {
            final double powerUsed = this.extractAEPower(this.getProxy().getIdlePowerUsage(), Actionable.MODULATE, PowerMultiplier.CONFIG); // drain
            if (powerUsed + 0.1 >= idleUsage != this.powered) {
                this.recalculateDisplay();
            }
        }

        if (!ItemHandlerUtil.isEmpty(this.inputInventory)) {
            this.tryToStoreContents();
        }
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);

        if (this.world.getTotalWorldTime() - this.lastStateChange > 8) {
            this.cellState = 0;
            this.powered = false;
            this.blinking = 0;
        } else {
            // just keep the blinks...
            this.cellState = 0;
            this.powered = false;
        }

        for (int x = 0; x < this.getCellCount(); x++) {
            this.cellState |= (this.getCellStatus(x) << (3 * x));
        }

        this.powered = this.isPowered();

        data.writeByte(this.cellState);
        data.writeBoolean(this.powered);
        data.writeByte(this.blinking);
        data.writeByte(this.paintedColor.ordinal());
    }

    @Override
    protected boolean readFromStream(final ByteBuf data) throws IOException {
        final boolean c = super.readFromStream(data);

        final int oldState = this.cellState;
        final boolean oldPowered = this.powered;
        final int oldBlinking = this.blinking;

        this.cellState = data.readByte();
        this.powered = data.readBoolean();
        this.blinking = data.readByte();
        final AEColor oldPaintedColor = this.paintedColor;
        this.paintedColor = AEColor.values()[data.readByte()];

        this.lastStateChange = this.world.getTotalWorldTime();

        return oldPaintedColor != this.paintedColor || this.cellState != oldState || c || oldPowered != this.powered || oldBlinking != this.blinking;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.config.readFromNBT(data);
        this.priority = data.getInteger("priority");
        if (data.hasKey("paintedColor")) {
            this.paintedColor = AEColor.values()[data.getByte("paintedColor")];
        }
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.config.writeToNBT(data);
        data.setInteger("priority", this.priority);
        data.setByte("paintedColor", (byte) this.paintedColor.ordinal());
        return data;
    }

    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.recalculateDisplay();
    }

    @MENetworkEventSubscribe
    public void channelRender(final MENetworkChannelsChanged c) {
        this.recalculateDisplay();
    }

    @Override
    public MEStorage getInventory() {
        this.updateHandler();
        return this.cellStorage != null ? this.cellStorage : NullInventory.of();
    }

    @Override
    public IItemHandler getInternalInventory() {
        return this.internalInventory;
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc, final ItemStack removed, final ItemStack added) {
        if (inv == this.cellInventory) {
            this.isCached = false; // recalculate the storage cell.

            try {
                if (this.getProxy().isActive()) {
                    final IStorageService gs = this.getProxy().getStorage();
                    Platform.postChanges(gs, removed, added, this.mySrc);
                }
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
            } catch (final GridAccessException ignored) {
            }

            IStorageProvider.requestUpdate(this.getProxy().getNode());

            // update the neighbors
            if (this.world != null) {
                Platform.notifyBlocksOfNeighbors(this.world, this.pos);
                this.markForUpdate();
            }
        }
        if (inv == this.inputInventory && mc == InvOperation.INSERT) {
            this.tryToStoreContents();
        }
    }

    @Override
    protected IItemHandler getItemHandlerForSide(@Nonnull EnumFacing side) {
        if (side == this.getForward()) {
            return this.cellInventory;
        } else {
            return this.inputInventory;
        }
    }

    private void tryToStoreContents() {
        if (!ItemHandlerUtil.isEmpty(this.inputInventory)) {
            this.updateHandler();

            if (this.cellStorage != null && this.cellKeyType == AEKeyType.items()) {
                final ItemStack toStore = this.inputInventory.getStackInSlot(0);
                final AEItemKey what = AEItemKey.of(toStore);

                if (what != null) {
                    final long inserted = Platform.poweredInsert(this, this.cellStorage, what, toStore.getCount(), this.mySrc);

                    if (inserted >= toStore.getCount()) {
                        this.inputInventory.setStackInSlot(0, ItemStack.EMPTY);
                    } else if (inserted > 0) {
                        this.inputInventory.setStackInSlot(0, what.toStack((int) (toStore.getCount() - inserted)));
                    }
                }
            }
        }
    }

    /**
     * Mounts the chest's single cell into whichever network this chest is cabled into. Replaces the old
     * per-channel {@code getCellArray}.
     */
    @Override
    public void mountInventories(final IStorageMounts storageMounts) {
        this.updateHandler();
        if (this.cellStorage != null) {
            storageMounts.mount(this.cellStorage, this.priority);
        }
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.isCached = false; // recalculate the storage cell.

        IStorageProvider.requestUpdate(this.getProxy().getNode());

        try {
            this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    /**
     * No longer an {@code @Override}: {@code blinkCell} lived on the old {@code ICellContainer}, which
     * {@link IStorageProvider} does not carry forward. Called directly by {@link #driveWatcher}.
     */
    public void blinkCell(final int slot) {
        final long now = this.world.getTotalWorldTime();
        if (now - this.lastStateChange > 8) {
            this.cellState = 0;
            this.powered = false;
            this.blinking = 0;
        }
        this.lastStateChange = now;

        this.blinking |= (1 << slot);

        this.recalculateDisplay();
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.config;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {

    }

    /**
     * Opens the GUI for the currently inserted cell. One terminal serves every key type, so the ordinary
     * screen is the default; a registered {@link ICellGuiHandler} only overrides it, and may claim one
     * specific cell item ahead of the generic handler for its type.
     */
    public boolean openGui(final EntityPlayer p) {
        this.updateHandler();
        if (this.driveWatcher == null) {
            return false;
        }

        final ItemStack cellStack = this.getCell();
        final ICellHandler ch = StorageCells.getHandler(cellStack);
        if (ch == null) {
            return false;
        }

        final ICellGuiHandler chg = StorageCells.getGuiHandler(this.cellKeyType, cellStack);
        if (chg != null) {
            chg.openChestGui(p, this, ch, this.driveWatcher.getCell(), cellStack, this.cellKeyType);
        } else {
            Platform.openGUI(p, this, AEPartLocation.fromFacing(this.getUp()), GuiBridge.GUI_ME);
        }

        return true;
    }

    @Override
    public AEColor getColor() {
        return this.paintedColor;
    }

    @Override
    public boolean recolourBlock(final EnumFacing side, final AEColor newPaintedColor, final EntityPlayer who) {
        if (this.paintedColor == newPaintedColor) {
            return false;
        }

        this.paintedColor = newPaintedColor;
        this.saveChanges();
        this.markForUpdate();
        return true;
    }

    private boolean securityCheck(final EntityPlayer player, final SecurityPermissions requiredPermission) {
        final IGridNode gn = this.getActionableNode();
        if (gn != null) {
            final IGrid g = gn.getGrid();
            if (g != null) {
                final ISecurityGrid sg = g.getCache(ISecurityGrid.class);
                return sg.hasPermission(player, requiredPermission);
            }
        }
        return false;
    }

    /**
     * Wraps the chest's {@link DriveWatcher} with the INJECT/EXTRACT permission check the old
     * {@code ChestMonitorHandler} used to perform in its {@code injectItems}/{@code extractItems} overrides.
     * Neither {@code MEInventoryHandler} nor {@link DriveWatcher} has a hook for this (it is not part of the
     * frozen {@code MEStorage} contract), so it lives here instead. Machine-sourced access (e.g. this chest's own
     * input slot) always passes, since {@link IActionSource#player()} is empty for it - exactly like before.
     */
    private class SecurityAwareCellStorage implements MEStorage {
        private final DriveWatcher inner;

        SecurityAwareCellStorage(final DriveWatcher inner) {
            this.inner = inner;
        }

        @Override
        public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
            if (source.player().map(player -> !TileChest.this.securityCheck(player, SecurityPermissions.INJECT)).orElse(false)) {
                return 0;
            }
            return this.inner.insert(what, amount, mode, source);
        }

        @Override
        public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
            if (source.player().map(player -> !TileChest.this.securityCheck(player, SecurityPermissions.EXTRACT)).orElse(false)) {
                return 0;
            }
            return this.inner.extract(what, amount, mode, source);
        }

        @Override
        public void getAvailableStacks(final KeyCounter out) {
            this.inner.getAvailableStacks(out);
        }

        @Override
        public ITextComponent getDescription() {
            return this.inner.getDescription();
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        this.updateHandler();
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && this.fluidHandler != null && facing != this.getForward()) {
            return true;
        }
        if (capability == Capabilities.STORAGE_MONITORABLE_ACCESSOR && this.accessor != null && facing != this.getForward()) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        this.updateHandler();
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && this.fluidHandler != null && facing != this.getForward()) {
            return (T) this.fluidHandler;
        }
        if (capability == Capabilities.STORAGE_MONITORABLE_ACCESSOR && this.accessor != null && facing != this.getForward()) {
            return (T) this.accessor;
        }
        return super.getCapability(capability, facing);
    }

    private class Accessor implements IStorageMonitorableAccessor {
        @Nullable
        @Override
        public MEStorage getInventory(IActionSource src) {
            if (Platform.canAccess(TileChest.this.getProxy(), src)) {
                return TileChest.this.getInventory();
            }
            return null;
        }
    }

    private class FluidHandler implements IFluidHandler {
        private final IFluidTankProperties[] TANK_PROPS = new IFluidTankProperties[]{new FluidTankProperties(null, Fluid.BUCKET_VOLUME)};

        @Override
        public int fill(final FluidStack resource, final boolean doFill) {
            TileChest.this.updateHandler();
            if (TileChest.this.cellStorage != null && TileChest.this.cellKeyType == AEKeyType.fluids()) {
                final AEFluidKey what = AEFluidKey.of(resource);
                if (what == null) {
                    return 0;
                }

                return (int) Platform.poweredInsert(TileChest.this, TileChest.this.cellStorage, what, resource.amount,
                        TileChest.this.mySrc, doFill ? Actionable.MODULATE : Actionable.SIMULATE);
            }
            return 0;
        }

        @Override
        public FluidStack drain(final FluidStack resource, final boolean doDrain) {
            return null;
        }

        @Override
        public FluidStack drain(final int maxDrain, final boolean doDrain) {
            return null;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            TileChest.this.updateHandler();

            if (TileChest.this.cellStorage != null && TileChest.this.cellKeyType == AEKeyType.fluids()) {
                return this.TANK_PROPS;
            }
            return null;
        }
    }

    private class InputInventoryFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(IItemHandler inv, int slot, int amount) {
            return false;
        }

        @Override
        public boolean allowInsert(IItemHandler inv, int slot, ItemStack stack) {
            if (TileChest.this.isPowered()) {
                TileChest.this.updateHandler();
                return TileChest.this.cellStorage != null && TileChest.this.cellKeyType == AEKeyType.items();
            }
            return false;
        }
    }

    private class CellInventoryFilter implements IAEItemFilter {

        @Override
        public boolean allowExtract(IItemHandler inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(IItemHandler inv, int slot, ItemStack stack) {
            return StorageCells.isCellHandled(stack);
        }

    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return AEApi.instance().definitions().blocks().chest().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public GuiBridge getGuiBridge() {
        this.updateHandler();
        // One terminal for every key type, so the chest no longer picks a screen per type. This used to
        // answer null for anything that was neither items nor fluids, which meant a key type registered by
        // an addon gave the chest no GUI at all.
        return this.cellKeyType == null ? null : GuiBridge.GUI_ME;
    }

}
