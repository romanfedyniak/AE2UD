/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.parts.misc;

import appeng.api.upgrades.UpgradeCards;


import appeng.api.AEApi;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.StackWorldBehaviors;
import appeng.api.config.*;
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
import appeng.api.stacks.GenericStack;
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
import appeng.helpers.IPriorityHost;
import appeng.helpers.Reflected;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.ITickingMonitor;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.NullInventory;
import appeng.parts.PartModel;
import appeng.parts.automation.PartUpgradeable;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileCableBus;
import appeng.util.Platform;
import appeng.util.inv.InvOperation;
import appeng.util.prioritylist.IPartitionList;
import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
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
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


/**
 * Storage bus. Wraps whatever is on the far side of the block it's attached to as one {@link MEStorage} and mounts
 * it into the network at {@link #priority}.
 * <p/>
 * Since the {@code MEStorage}/{@code AEKeyType} port (see CONTRACT.md), the target inventory is no longer resolved
 * by hand for items only: {@link #findExternalStorage} tries, in order,
 * <ol>
 * <li>a direct link to another ME network ({@link IStorageMonitorableAccessor}),</li>
 * <li>AE2UD's fork-specific Storage Drawers integration ({@link IItemRepository}, see CONTRACT.md §10/§11),</li>
 * <li>the generic {@link ExternalStorageStrategy} registry, one wrapper per registered {@link AEKeyType}, composed
 * transparently by {@link CompositeExternalStorage} when more than one type answers (items today; fluids from
 * wave 5 onward; anything an addon registers) - this is what lets the same bus serve every type without any
 * further change here.</li>
 * </ol>
 */
public class PartStorageBus extends PartUpgradeable implements IGridTickable, IStorageProvider, IPriorityHost {
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_base");
    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_off"));
    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_on"));
    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_has_channel"));
    @CapabilityInject(IItemRepository.class)
    public static Capability<IItemRepository> ITEM_REPOSITORY_CAPABILITY = null;

    protected final IActionSource mySrc;
    protected final AppEngInternalAEInventory Config = new AppEngInternalAEInventory(this, 63);
    protected final StorageBusHandler handler = new StorageBusHandler(NullInventory.of());
    protected int priority = 0;
    protected boolean cached = false;
    protected ITickingMonitor monitor = null;
    protected int handlerHash = 0;
    private boolean wasActive = false;
    private byte resetCacheLogic = 0;
    @Nullable
    private Map<AEKeyType, ExternalStorageStrategy> externalStorageStrategies;

    @Reflected
    public PartStorageBus(final ItemStack is) {
        super(is);
        this.getConfigManager().registerSetting(Settings.ACCESS, AccessRestriction.READ_WRITE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        this.getConfigManager().registerSetting(Settings.STICKY_MODE, YesNo.NO);
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
        // Fork note on the ACCESS old/new comparison (see CONTRACT.md §10): the pre-migration code used the OLD
        // access value to post one last "everything just disappeared" notification via the removed
        // IMEMonitor#postAlterationOfStoredItems before applying a more restrictive new access - there is no
        // equivalent call any more, IStorageService has no per-source posting method at all, MEStorage is not a
        // per-listener monitor, and the network now diffs its own cached view once per tick instead of being
        // pushed to (see CraftingGridCache/GridStorageCache). The visible result - losing READ access makes this
        // bus's contents disappear from every terminal - still happens: resetCache(true) below always forces a
        // full rebuild of `handler` (fresh allowExtraction/allowInsertion/filter flags) and a
        // IStorageProvider.requestUpdate() call, so the network re-derives its available-stacks cache as soon as
        // it next ticks, exactly as before, just centrally instead of through a per-bus push.
        this.resetCache(true);
        this.getHost().markForSave();
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc, final ItemStack removedStack, final ItemStack newStack) {
        super.onChangeInventory(inv, slot, mc, removedStack, newStack);

        if (inv == this.Config) {
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
        this.Config.readFromNBT(data, "config");
        this.priority = data.getInteger("priority");
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.Config.writeToNBT(data, "config");
        data.setInteger("priority", this.priority);
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        if (name.equals("config")) {
            return this.Config;
        }

        return super.getInventoryByName(name);
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
                } else if (iPart instanceof PartInterface) {
                    if (createHandlerHash(te) != handlerHash) {
                        this.resetCache(true);
                        this.resetCache();
                    }
                }
            } else if (te == null) {
                this.resetCache(true);
                this.resetCache();
            } else if (te instanceof TileInterface) {
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
            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_STORAGEBUS);
        }
        return true;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.StorageBus.getMin(), TickRates.StorageBus.getMax(), this.monitor == null, true);
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
        // 1. A direct link to another ME network (sub-networking through a storage bus).
        final IStorageMonitorableAccessor accessor = target.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide);
        if (accessor != null) {
            return accessor.getInventory(this.mySrc);
        }

        // 2. Fork-specific: Storage Drawers' IItemRepository. Upstream has no equivalent (CONTRACT.md §10/§11); kept
        // as an explicit extra rather than a registered ExternalStorageStrategy because the capability is not keyed
        // by AEKeyType at all.
        if (ITEM_REPOSITORY_CAPABILITY != null && target.hasCapability(ITEM_REPOSITORY_CAPABILITY, targetSide)) {
            final IItemRepository repo = target.getCapability(ITEM_REPOSITORY_CAPABILITY, targetSide);
            if (repo != null) {
                return new ItemRepositoryAdapter(repo, changeListener);
            }
        }

        // 3. Generic external-storage strategies: one per registered AEKeyType.
        return this.createGenericExternalStorage(extractableOnly, changeListener);
    }

    private Map<AEKeyType, ExternalStorageStrategy> getExternalStorageStrategies() {
        if (this.externalStorageStrategies == null) {
            final TileEntity self = this.getHost().getTile();
            final BlockPos targetPos = self.getPos().offset(this.getSide().getFacing());
            final EnumFacing targetSide = this.getSide().getFacing().getOpposite();
            this.externalStorageStrategies = StackWorldBehaviors.createExternalStorageStrategies(self.getWorld(), targetPos, targetSide);
        }
        return this.externalStorageStrategies;
    }

    @Nullable
    private MEStorage createGenericExternalStorage(final boolean extractableOnly, final Runnable changeListener) {
        final Map<AEKeyType, ExternalStorageStrategy> strategies = this.getExternalStorageStrategies();
        if (strategies.isEmpty()) {
            return null;
        }

        Map<AEKeyType, MEStorage> wrappers = null;
        MEStorage single = null;
        for (final Map.Entry<AEKeyType, ExternalStorageStrategy> entry : strategies.entrySet()) {
            final MEStorage wrapper = entry.getValue().createWrapper(extractableOnly, changeListener);
            if (wrapper == null) {
                continue;
            }
            if (wrappers != null) {
                wrappers.put(entry.getKey(), wrapper);
            } else if (single == null) {
                single = wrapper;
                wrappers = new LinkedHashMap<>();
                wrappers.put(entry.getKey(), wrapper);
            }
        }

        if (wrappers == null) {
            return null;
        }
        return wrappers.size() == 1 ? single : new CompositeExternalStorage(wrappers);
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

        final IItemHandler itemHandler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, targetSide);

        if (itemHandler != null) {
            return Objects.hash(target, itemHandler, itemHandler.getSlots());
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
        // filterOnExtraction is forced on unconditionally: the old generic MEInventoryHandler<T> gated BOTH
        // extraction and visibility on hasReadAccess unconditionally; the new one only does either when told to
        // via setExtractFiltering, so READ (and the whitelist) must always be asked here to reproduce that.
        this.handler.setExtractFiltering(true, !allowExtraction || extractableOnlyFilter);
        this.handler.setWhitelist(this.getInstalledUpgrades(UpgradeCards.inverter()) > 0 ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
        this.handler.setPartitionList(this.createFilter());
        this.handler.setSticky(this.getInstalledUpgrades(UpgradeCards.sticky()) > 0);

        // update sleep state...
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

    /**
     * Builds the priority/filter list from the config slots. Overridden by {@link PartOreDicStorageBus} to swap in
     * an ore-dictionary rule list instead - everything else (ACCESS, STORAGE_FILTER, UpgradeCards.inverter(),
     * UpgradeCards.sticky(), priority) is shared, unchanged, through {@link #getInternalHandler()}.
     */
    protected IPartitionList createFilter() {
        final IPartitionList.Builder filterBuilder = IPartitionList.builder();
        if (this.getInstalledUpgrades(UpgradeCards.fuzzy()) > 0) {
            filterBuilder.fuzzyMode((FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE));
        }

        final int slotsToUse = 18 + this.getInstalledCapacityPoints() * 9;
        for (int x = 0; x < this.Config.getSlots() && x < slotsToUse; x++) {
            final GenericStack is = this.Config.getAEStackInSlot(x);
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
        return AEApi.instance().definitions().parts().storageBus().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public GuiBridge getGuiBridge() {
        return GuiBridge.GUI_STORAGEBUS;
    }

    /**
     * Persistent wrapper around whatever {@link #findExternalStorage} last resolved. Kept as a single, stable
     * instance for the part's whole lifetime (mirroring AE2-original's {@code StorageBusPart.StorageBusInventory})
     * so that settings changes only need to update flags in place; only {@link #setDelegate} swaps the backing
     * storage when the target itself changes. Re-declaring the protected accessors here (rather than leaving them
     * on {@link MEInventoryHandler}) is what makes them callable from {@link PartStorageBus} despite being
     * `protected`: the override moves their declaring class into this package, see the outer class's uses.
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
     * Combines one {@link MEStorage} per {@link AEKeyType} into a single view, so a single storage bus can serve
     * items, fluids and any type an addon registers through {@link ExternalStorageStrategy}, with no branching in
     * the bus itself. Only relevant once more than one type answers (items today; fluids from wave 5 onward).
     */
    private static final class CompositeExternalStorage implements MEStorage, ITickingMonitor {
        private static final TickRateModulation[] URGENCY_ORDER = {
                TickRateModulation.URGENT, TickRateModulation.FASTER, TickRateModulation.SAME,
                TickRateModulation.SLOWER, TickRateModulation.IDLE, TickRateModulation.SLEEP,
        };

        private final Map<AEKeyType, MEStorage> storages;

        CompositeExternalStorage(final Map<AEKeyType, MEStorage> storages) {
            this.storages = storages;
        }

        @Override
        public boolean isPreferredStorageFor(final AEKey what, final IActionSource source) {
            final MEStorage storage = this.storages.get(what.getType());
            return storage != null && storage.isPreferredStorageFor(what, source);
        }

        @Override
        public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
            final MEStorage storage = this.storages.get(what.getType());
            return storage == null ? 0 : storage.insert(what, amount, mode, source);
        }

        @Override
        public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
            final MEStorage storage = this.storages.get(what.getType());
            return storage == null ? 0 : storage.extract(what, amount, mode, source);
        }

        @Override
        public void getAvailableStacks(final KeyCounter out) {
            for (final MEStorage storage : this.storages.values()) {
                storage.getAvailableStacks(out);
            }
        }

        @Override
        public ITextComponent getDescription() {
            for (final MEStorage storage : this.storages.values()) {
                return storage.getDescription();
            }
            return new TextComponentString("");
        }

        @Override
        public TickRateModulation onTick() {
            TickRateModulation result = TickRateModulation.SLEEP;
            for (final MEStorage storage : this.storages.values()) {
                if (storage instanceof ITickingMonitor) {
                    result = mostUrgent(result, ((ITickingMonitor) storage).onTick());
                }
            }
            return result;
        }

        private static TickRateModulation mostUrgent(final TickRateModulation a, final TickRateModulation b) {
            for (final TickRateModulation m : URGENCY_ORDER) {
                if (a == m || b == m) {
                    return m;
                }
            }
            return TickRateModulation.SLEEP;
        }
    }
}
