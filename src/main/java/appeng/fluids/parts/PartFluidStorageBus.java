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

package appeng.fluids.parts;


import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.AEApi;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.StackWorldBehaviors;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.capabilities.Capabilities;
import appeng.core.AppEng;
import appeng.core.settings.TickRates;
import appeng.core.sync.GuiBridge;
import appeng.fluids.helper.IConfigurableFluidInventory;
import appeng.fluids.tile.TileFluidInterface;
import appeng.fluids.util.AEFluidInventory;
import appeng.fluids.util.IAEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import appeng.helpers.IPriorityHost;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.ITickingMonitor;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.NullInventory;
import appeng.parts.PartModel;
import appeng.parts.automation.PartUpgradeable;
import appeng.tile.networking.TileCableBus;
import appeng.util.Platform;
import appeng.util.prioritylist.IPartitionList;


/**
 * Fluid storage bus. Fluid counterpart of {@code appeng.parts.misc.PartStorageBus}: same resolution order (direct
 * ME network link, then the generic {@link ExternalStorageStrategy} registry), same stable-handler-instance shape,
 * but restricted to {@link AEKeyType#fluids()} alone and driven by an {@link AEFluidInventory}-shaped GUI/config
 * instead of the item bus' wrapped-item slots -- AE2UD keeps a dedicated fluid storage bus item/GUI rather than
 * merging into the generic one, the same "split, not merged" shape the formation and annihilation planes keep
 * (CONTRACT.md §10).
 * <p/>
 * A direct link to another ME network ({@link IStorageMonitorableAccessor}) is wrapped in {@link FluidOnlyStorage}
 * so this bus only ever contributes fluids even when linked to a sub-network that also carries items -- mirroring
 * the pre-port {@code getInventoryWrapper}'s {@code inventory.getInventory(IFluidStorageChannel.class)}, which had
 * the same effect under the old per-channel model.
 */
public class PartFluidStorageBus extends PartUpgradeable
        implements IGridTickable, IStorageProvider, IPriorityHost, IAEFluidInventory, IConfigurableFluidInventory {
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID, "part/fluid_storage_bus_base");
    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/fluid_storage_bus_off"));
    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/fluid_storage_bus_on"));
    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/fluid_storage_bus_has_channel"));

    private final IActionSource mySrc;
    private final AEFluidInventory config = new AEFluidInventory(this, 63);
    private final StorageBusHandler handler = new StorageBusHandler(NullInventory.of());
    private int priority = 0;
    private boolean cached = false;
    private ITickingMonitor monitor = null;
    private int handlerHash = 0;
    private boolean wasActive = false;
    private byte resetCacheLogic = 0;
    @Nullable
    private ExternalStorageStrategy externalStorageStrategy;
    private boolean externalStorageStrategyResolved;

    public PartFluidStorageBus(final ItemStack is) {
        super(is);
        this.getConfigManager().registerSetting(Settings.ACCESS, AccessRestriction.READ_WRITE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        this.mySrc = new MachineSource(this);
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.updateStatus();
    }

    private void updateStatus() {
        final boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            try {
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
                this.getHost().markForUpdate();
            } catch (final GridAccessException e) {
                // :P
            }
        }
    }

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged changedChannels) {
        this.updateStatus();
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        // Same reasoning as appeng.parts.misc.PartStorageBus (CONTRACT.md §9, wave 3b): there is no per-source
        // posting entry point left on IStorageService, so every settings change forces a full rebuild of the
        // handler's flags and, if the registration-worthy state flips, an IStorageProvider.requestUpdate call --
        // the network re-derives its cache on the next tick instead of through a per-bus push.
        this.resetCache(true);
        this.getHost().markForSave();
    }

    @Override
    public void onFluidInventoryChanged(final IAEFluidTank inv, final int slot) {
        if (inv == this.config) {
            this.resetCache(true);
        }
    }

    @Override
    public void upgradesChanged() {
        super.upgradesChanged();
        this.resetCache(true);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.config.readFromNBT(data, "config");
        this.priority = data.getInteger("priority");
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.config.writeToNBT(data, "config");
        data.setInteger("priority", this.priority);
    }

    @Override
    public IFluidHandler getFluidInventoryByName(final String name) {
        if (name.equals("config")) {
            return this.config;
        }
        return null;
    }

    protected void resetCache(final boolean fullReset) {
        if (this.getHost() == null || this.getHost().getTile() == null || this.getHost().getTile().getWorld() == null || this.getHost().getTile().getWorld().isRemote) {
            return;
        }

        if (fullReset) {
            this.resetCacheLogic = 2;
        } else if (this.resetCacheLogic < 2) {
            this.resetCacheLogic = 1;
        }

        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(3, 3, 15, 13, 13, 16);
        bch.addBox(2, 2, 14, 14, 14, 15);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        if (pos.offset(this.getSide().getFacing()).equals(neighbor)) {
            final TileEntity te = w.getTileEntity(neighbor);

            // In case the TE was destroyed, we have to do a full reset immediately.
            if (te instanceof TileCableBus) {
                IPart iPart = ((TileCableBus) te).getPart(this.getSide().getOpposite());
                if (iPart == null) {
                    this.resetCache(true);
                    this.resetCache();
                } else if (iPart instanceof PartFluidInterface) {
                    if (createHandlerHash(te) != handlerHash) {
                        this.resetCache(true);
                        this.resetCache();
                    }
                }
            } else if (te == null) {
                this.resetCache(true);
                this.resetCache();
            } else if (te instanceof TileFluidInterface) {
                if (createHandlerHash(te) != handlerHash) {
                    this.resetCache(true);
                    this.resetCache();
                }
            } else {
                this.resetCache(false);
            }
        }
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isServer()) {
            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_STORAGEBUS_FLUID);
        }
        return true;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.FluidStorageBus.getMin(), TickRates.FluidStorageBus.getMax(), this.monitor == null, true);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (this.resetCacheLogic != 0) {
            this.resetCache();
        }

        if (this.monitor != null) {
            return this.monitor.onTick();
        }

        return TickRateModulation.SLEEP;
    }

    protected void resetCache() {
        final boolean fullReset = this.resetCacheLogic == 2;
        this.resetCacheLogic = 0;

        if (fullReset) {
            this.handlerHash = 0;
        }

        this.cached = false;
        this.getInternalHandler();
    }

    /**
     * Resolves the storage this bus should expose, in priority order. See the class javadoc.
     */
    @Nullable
    MEStorage findExternalStorage(final TileEntity target, final EnumFacing targetSide, final boolean extractableOnly,
            final Runnable changeListener) {
        // 1. A direct link to another ME network -- wrapped so only fluids ever pass through this bus.
        final IStorageMonitorableAccessor accessor = target.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide);
        if (accessor != null) {
            final MEStorage inventory = accessor.getInventory(this.mySrc);
            return inventory != null ? new FluidOnlyStorage(inventory) : null;
        }

        // 2. The generic external-storage strategy registered for fluids specifically.
        final ExternalStorageStrategy strategy = this.getExternalStorageStrategy();
        return strategy != null ? strategy.createWrapper(extractableOnly, changeListener) : null;
    }

    private ExternalStorageStrategy getExternalStorageStrategy() {
        if (!this.externalStorageStrategyResolved) {
            this.externalStorageStrategyResolved = true;
            final TileEntity self = this.getHost().getTile();
            final BlockPos targetPos = self.getPos().offset(this.getSide().getFacing());
            final EnumFacing targetSide = this.getSide().getFacing().getOpposite();
            this.externalStorageStrategy = StackWorldBehaviors
                    .createExternalStorageStrategies(self.getWorld(), targetPos, targetSide)
                    .get(AEKeyType.fluids());
        }
        return this.externalStorageStrategy;
    }

    private void onExternalStorageChanged() {
        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (final GridAccessException e) {
            // meh
        }
    }

    int createHandlerHash(TileEntity target) {
        if (target == null) {
            return 0;
        }

        final EnumFacing targetSide = this.getSide().getFacing().getOpposite();

        if (target.hasCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide)) {
            IStorageMonitorableAccessor accessor = target.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide);
            if (accessor != null) {
                return Objects.hash(target, accessor.getInventory(this.mySrc));
            }
            return Objects.hash(target, target.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide));
        }

        final IFluidHandler fluidHandler = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, targetSide);

        if (fluidHandler != null) {
            return Objects.hash(target, fluidHandler, fluidHandler.getTankProperties().length);
        }

        return 0;
    }

    public MEInventoryHandler getInternalHandler() {
        if (this.cached) {
            return this.handler;
        }

        final boolean wasSleeping = this.monitor == null;
        final boolean wasRegistered = this.hasRegisteredCellToNetwork();

        this.cached = true;
        final TileEntity self = this.getHost().getTile();
        final TileEntity target = self.getWorld().getTileEntity(self.getPos().offset(this.getSide().getFacing()));
        final int newHandlerHash = this.createHandlerHash(target);

        if (newHandlerHash != 0 && newHandlerHash == this.handlerHash) {
            return this.handler;
        }

        this.handlerHash = newHandlerHash;
        this.monitor = null;

        MEStorage newDelegate = NullInventory.of();
        if (target != null) {
            final EnumFacing targetSide = this.getSide().getFacing().getOpposite();
            final boolean extractableOnly = this.getConfigManager().getSetting(Settings.STORAGE_FILTER) == StorageFilter.EXTRACTABLE_ONLY;
            final MEStorage found = this.findExternalStorage(target, targetSide, extractableOnly, this::onExternalStorageChanged);
            if (found != null) {
                newDelegate = found;
            }
        }

        this.handler.setDelegate(newDelegate);

        if (newDelegate instanceof ITickingMonitor) {
            this.monitor = (ITickingMonitor) newDelegate;
        }

        final AccessRestriction access = (AccessRestriction) this.getConfigManager().getSetting(Settings.ACCESS);
        final boolean allowExtraction = access.hasPermission(AccessRestriction.READ);
        final boolean extractableOnlyFilter = this.getConfigManager().getSetting(Settings.STORAGE_FILTER) == StorageFilter.EXTRACTABLE_ONLY;

        this.handler.setAllowExtraction(allowExtraction);
        this.handler.setAllowInsertion(access.hasPermission(AccessRestriction.WRITE));
        this.handler.setExtractFiltering(true, !allowExtraction || extractableOnlyFilter);
        this.handler.setWhitelist(this.getInstalledUpgrades(Upgrades.INVERTER) > 0 ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
        this.handler.setPartitionList(this.createFilter());
        this.handler.setSticky(this.getInstalledUpgrades(Upgrades.STICKY) > 0);

        if (wasSleeping != (this.monitor == null)) {
            try {
                final ITickManager tm = this.getProxy().getTick();
                if (this.monitor == null) {
                    tm.sleepDevice(this.getProxy().getNode());
                } else {
                    tm.wakeDevice(this.getProxy().getNode());
                }
            } catch (final GridAccessException e) {
                // :(
            }
        }

        if (wasRegistered != this.hasRegisteredCellToNetwork()) {
            IStorageProvider.requestUpdate(this.getProxy().getNode());
        }

        return this.handler;
    }

    private boolean hasRegisteredCellToNetwork() {
        return !(this.handler.getDelegate() instanceof NullInventory);
    }

    private IPartitionList createFilter() {
        final IPartitionList.Builder filterBuilder = IPartitionList.builder();
        if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            filterBuilder.fuzzyMode((FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE));
        }

        final int slotsToUse = 18 + this.getInstalledUpgrades(Upgrades.CAPACITY) * 9;
        for (int x = 0; x < this.config.getSlots() && x < slotsToUse; x++) {
            final var is = this.config.getFluidInSlot(x);
            if (is != null) {
                filterBuilder.add(is.what());
            }
        }

        return filterBuilder.build();
    }

    @Override
    public void mountInventories(final IStorageMounts mounts) {
        if (this.hasRegisteredCellToNetwork()) {
            mounts.mount(this.getInternalHandler(), this.priority);
        }
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.getHost().markForSave();
        this.resetCache(true);
        IStorageProvider.requestUpdate(this.getProxy().getNode());
    }

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return AEApi.instance().definitions().parts().fluidStorageBus().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public GuiBridge getGuiBridge() {
        return GuiBridge.GUI_STORAGEBUS_FLUID;
    }

    public IAEFluidTank getConfig() {
        return this.config;
    }

    /**
     * Persistent wrapper around whatever {@link #findExternalStorage} last resolved. See
     * {@code appeng.parts.misc.PartStorageBus.StorageBusHandler}, which this mirrors exactly, for why the
     * accessors are re-declared here.
     */
    private static final class StorageBusHandler extends MEInventoryHandler {
        StorageBusHandler(final MEStorage delegate) {
            super(delegate);
        }

        @Override
        protected MEStorage getDelegate() {
            return super.getDelegate();
        }

        @Override
        protected void setDelegate(final MEStorage delegate) {
            super.setDelegate(delegate);
        }
    }

    /**
     * Restricts a type-erased {@link MEStorage} (a whole linked sub-network) to fluids only, so this bus never
     * leaks item access when linked to another network -- the new-model equivalent of the pre-port
     * {@code inventory.getInventory(IFluidStorageChannel.class)} call under the old per-channel API.
     */
    private static final class FluidOnlyStorage implements MEStorage {
        private final MEStorage delegate;

        FluidOnlyStorage(final MEStorage delegate) {
            this.delegate = delegate;
        }

        private static boolean isFluid(final AEKey what) {
            return what.getType() == AEKeyType.fluids();
        }

        @Override
        public boolean isPreferredStorageFor(final AEKey what, final IActionSource source) {
            return isFluid(what) && this.delegate.isPreferredStorageFor(what, source);
        }

        @Override
        public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
            return isFluid(what) ? this.delegate.insert(what, amount, mode, source) : 0;
        }

        @Override
        public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
            return isFluid(what) ? this.delegate.extract(what, amount, mode, source) : 0;
        }

        @Override
        public void getAvailableStacks(final KeyCounter out) {
            final KeyCounter all = new KeyCounter();
            this.delegate.getAvailableStacks(all);
            for (final var entry : all) {
                if (isFluid(entry.getKey())) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
            }
        }

        @Override
        public ITextComponent getDescription() {
            return this.delegate.getDescription();
        }
    }
}
