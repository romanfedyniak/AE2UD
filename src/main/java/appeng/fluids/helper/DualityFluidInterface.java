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

package appeng.fluids.helper;


import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.capabilities.Capabilities;
import appeng.core.settings.TickRates;
import appeng.fluids.util.AEFluidInventory;
import appeng.fluids.util.AENetworkFluidInventory;
import appeng.fluids.util.IAEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import appeng.helpers.ICustomNameObject;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.MEMonitorIFluidHandler;
import appeng.me.storage.NullInventory;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import gregtech.api.block.machines.BlockMachine;
import gregtech.api.metatileentity.MetaTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;


/**
 * Fluid-side counterpart of {@code appeng.helpers.DualityInterface} - follows that class's post-migration
 * shape exactly (CONTRACT.md §9, "Wave 5 prerequisites"): drops the deleted {@code IStorageMonitorable} and
 * now {@code implements MEStorage} directly, exposing itself through an inner {@link Accessor}.
 * <p/>
 * Unlike the item interface, this class never had crafting patterns, a crafting tracker or a facing-based
 * item push queue - fluids have no crafting model in this fork - so none of that is introduced here either;
 * only the storage-facing half of {@code DualityInterface}'s shape applies.
 */
public class DualityFluidInterface implements IGridTickable, MEStorage, IAEFluidInventory, IAEAppEngInventory, IUpgradeableHost, IConfigManagerHost, IConfigurableFluidInventory {
    public static final int NUMBER_OF_TANKS = 9;
    public static final int TANK_CAPACITY = Fluid.BUCKET_VOLUME * 4;
    private static final Collection<Block> BAD_BLOCKS = new HashSet<>(100);
    private final ConfigManager cm = new ConfigManager(this);
    private final AENetworkProxy gridProxy;
    private final IFluidInterfaceHost iHost;
    private final IActionSource mySource;
    private final IActionSource interfaceRequestSource;
    private final UpgradeInventory upgrades;
    private boolean hasConfig = false;
    private final Accessor accessor = new Accessor();
    private final AEFluidInventory tanks;
    private final AEFluidInventory config = new AEFluidInventory(this, NUMBER_OF_TANKS);
    private final GenericStack[] requireWork = new GenericStack[NUMBER_OF_TANKS];
    private int isWorking = -1;
    private int priority;

    /** The network's storage, covering every registered key type. Swapped out as the grid connects/disconnects. */
    private MEStorage networkStorage = NullInventory.of();
    private boolean resetConfigCache = true;
    private MEStorage configCachedHandler;

    public DualityFluidInterface(final AENetworkProxy networkProxy, final IFluidInterfaceHost ih) {
        this.gridProxy = networkProxy;
        this.gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);

        this.upgrades = new StackUpgradeInventory(this.gridProxy.getMachineRepresentation(), this, 2);
        this.cm.registerSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);

        this.iHost = ih;

        this.mySource = new MachineSource(this.iHost);
        this.interfaceRequestSource = new InterfaceRequestSource(this.iHost);
        this.tanks = new AENetworkFluidInventory(this::getStorageGrid, this.mySource, this, NUMBER_OF_TANKS, TANK_CAPACITY);
    }

    @Nullable
    private IStorageService getStorageGrid() {
        try {
            return this.gridProxy.getStorage();
        } catch (GridAccessException e) {
            return null;
        }
    }

    public IUpgradeableHost getHost() {
        return this.iHost;
    }

    /**
     * The MEStorage view of this interface: the interface's own tanks while it has a config (whitelist
     * mode), or the whole network otherwise. Exactly {@code appeng.helpers.DualityInterface#getInventory()}'s
     * shape - the old per-channel split (item pass-through always full network; fluid pass-through gated by
     * config) collapses naturally once storage is no longer per-channel: a config-less fluid interface now
     * shares the same unified "whole network, any type" view the item interface does.
     */
    public MEStorage getInventory() {
        if (this.hasConfig()) {
            if (resetConfigCache) {
                resetConfigCache = false;
                configCachedHandler = new InterfaceInventory(this);
            }
            return configCachedHandler;
        }

        return this.networkStorage;
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        return this.getInventory().insert(what, amount, mode, source);
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        return this.getInventory().extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        this.getInventory().getAvailableStacks(out);
    }

    @Override
    public ITextComponent getDescription() {
        return this.getInventory().getDescription();
    }

    @Nullable
    public MEStorage getMonitorable(final IActionSource src) {
        if (Platform.canAccess(this.gridProxy, src)) {
            return this;
        }

        return null;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.Interface.getMin(), TickRates.Interface.getMax(), !this.hasWorkToDo(), true);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!this.gridProxy.isActive()) {
            return TickRateModulation.SLEEP;
        }

        final boolean couldDoWork = this.updateStorage();
        return this.hasWorkToDo() ? (couldDoWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER) : TickRateModulation.SLEEP;
    }

    public void notifyNeighbors() {
        if (this.gridProxy.isActive()) {
            try {
                this.gridProxy.getTick().wakeDevice(this.gridProxy.getNode());
            } catch (final GridAccessException e) {
                // :P
            }
        }

        final TileEntity te = this.iHost.getTileEntity();
        if (te != null && te.getWorld() != null) {
            Platform.notifyBlocksOfNeighbors(te.getWorld(), te.getPos());
        }
    }

    public void gridChanged() {
        try {
            this.networkStorage = this.gridProxy.getStorage().getInventory();
        } catch (final GridAccessException gae) {
            this.networkStorage = NullInventory.of();
        }

        this.notifyNeighbors();
    }

    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.SMART;
    }

    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this.iHost.getTileEntity());
    }

    private boolean sameGrid(final IGrid grid) throws GridAccessException {
        return grid == this.gridProxy.getGrid();
    }

    public String getTermName() {
        final TileEntity hostTile = this.iHost.getTileEntity();
        final World hostWorld = hostTile.getWorld();

        if (((ICustomNameObject) this.iHost).hasCustomInventoryName()) {
            return ((ICustomNameObject) this.iHost).getCustomInventoryName();
        }

        final EnumSet<EnumFacing> possibleDirections = this.iHost.getTargets();
        for (final EnumFacing direction : possibleDirections) {
            final BlockPos targ = hostTile.getPos().offset(direction);
            final TileEntity directedTile = hostWorld.getTileEntity(targ);

            if (directedTile == null) {
                continue;
            }

            if (directedTile instanceof IFluidInterfaceHost) {
                try {
                    if (((IFluidInterfaceHost) directedTile).getDualityFluidInterface().sameGrid(this.gridProxy.getGrid())) {
                        continue;
                    }
                } catch (final GridAccessException e) {
                    continue;
                }
            }

            final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(directedTile, direction.getOpposite());
            if (directedTile instanceof ICraftingMachine || adaptor != null) {
                if (adaptor != null && !adaptor.hasSlots()) {
                    continue;
                }

                final IBlockState directedBlockState = hostWorld.getBlockState(targ);
                final Block directedBlock = directedBlockState.getBlock();
                ItemStack what = new ItemStack(directedBlock, 1, directedBlock.getMetaFromState(directedBlockState));

                if (Platform.GTLoaded && directedBlock instanceof BlockMachine) {
                    MetaTileEntity metaTileEntity = Platform.getMetaTileEntity(directedTile.getWorld(), directedTile.getPos());
                    if (metaTileEntity != null) {
                        return metaTileEntity.getMetaFullName();
                    }
                }

                try {
                    Vec3d from = new Vec3d(hostTile.getPos().getX() + 0.5, hostTile.getPos().getY() + 0.5, hostTile.getPos().getZ() + 0.5);
                    from = from.add(direction.getXOffset() * 0.501, direction.getYOffset() * 0.501, direction.getZOffset() * 0.501);
                    final Vec3d to = from.add(direction.getXOffset(), direction.getYOffset(), direction.getZOffset());
                    final RayTraceResult mop = hostWorld.rayTraceBlocks(from, to, true);
                    if (mop != null && !BAD_BLOCKS.contains(directedBlock)) {
                        if (mop.getBlockPos().equals(directedTile.getPos())) {
                            final ItemStack g = directedBlock.getPickBlock(directedBlockState, mop, hostWorld, directedTile.getPos(), null);
                            if (!g.isEmpty()) {
                                what = g;
                            }
                        }
                    }
                } catch (final Throwable t) {
                    BAD_BLOCKS.add(directedBlock); // nope!
                }

                if (what.getItem() != Items.AIR) {
                    return what.getItem().getItemStackDisplayName(what);
                }

                final Item item = Item.getItemFromBlock(directedBlock);
                if (item == Items.AIR) {
                    return directedBlock.getTranslationKey();
                }
            }
        }

        return "Nothing";
    }

    public long getSortValue() {
        final TileEntity te = this.iHost.getTileEntity();
        return (te.getPos().getZ() << 24) ^ (te.getPos().getX() << 8) ^ te.getPos().getY();
    }

    public boolean hasCapability(Capability<?> capabilityClass, EnumFacing facing) {
        return capabilityClass == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || capabilityClass == Capabilities.STORAGE_MONITORABLE_ACCESSOR;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capabilityClass, EnumFacing facing) {
        if (capabilityClass == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return (T) this.tanks;
        } else if (capabilityClass == Capabilities.STORAGE_MONITORABLE_ACCESSOR) {
            return (T) this.accessor;
        }
        return null;
    }

    private boolean hasConfig() {
        return this.hasConfig;
    }

    private void readConfig() {
        this.hasConfig = false;

        for (int i = 0; i < this.config.getSlots(); i++) {
            if (this.config.getFluidInSlot(i) != null) {
                this.hasConfig = true;
                break;
            }
        }

        final boolean had = this.hasWorkToDo();

        for (int x = 0; x < NUMBER_OF_TANKS; x++) {
            this.updatePlan(x);
        }

        final boolean has = this.hasWorkToDo();

        if (had != has) {
            try {
                if (has) {
                    this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
                } else {
                    this.gridProxy.getTick().sleepDevice(this.gridProxy.getNode());
                }
            } catch (final GridAccessException e) {
                // :P
            }
        }

        this.notifyNeighbors();
    }

    private boolean updateStorage() {
        boolean didSomething = false;
        for (int x = 0; x < NUMBER_OF_TANKS; x++) {
            if (this.requireWork[x] != null) {
                didSomething = this.usePlan(x) || didSomething;
            }
        }
        return didSomething;
    }

    private boolean hasWorkToDo() {
        for (final GenericStack requiredWork : this.requireWork) {
            if (requiredWork != null) {
                return true;
            }
        }

        return false;
    }

    private void updatePlan(final int slot) {
        final GenericStack req = this.config.getFluidInSlot(slot);
        final GenericStack stored = this.tanks.getFluidInSlot(slot);

        if (req == null && (stored != null && stored.amount() > 0)) {
            this.requireWork[slot] = new GenericStack(stored.what(), -stored.amount());
            return;
        } else if (req != null) {
            int tankSize = (int) (Math.pow(4, this.getInstalledUpgrades(Upgrades.CAPACITY) + 1) * Fluid.BUCKET_VOLUME);
            if (stored == null || stored.amount() == 0) // need to add stuff!
            {
                this.requireWork[slot] = new GenericStack(req.what(), tankSize);
                return;
                // CONTRACT.md §9.1: the old `req.equals(stored)` used IAEFluidStack's size-insensitive equals
                // ("same fluid"); GenericStack.equals() compares the amount too, so identity has to be
                // checked on the keys instead, or a tank sitting at a different level than requested would
                // wrongly be treated as "different fluid, dispose everything" below.
            } else if (req.what().equals(stored.what())) // same fluid ( qty different? )!
            {
                if (stored.amount() != tankSize) {
                    this.requireWork[slot] = new GenericStack(req.what(), tankSize - stored.amount());
                    return;
                }
                // else: already sitting exactly at capacity - fall through, nothing to do
            } else
            // Stored != null; dispose!
            {
                this.requireWork[slot] = new GenericStack(stored.what(), -stored.amount());
                return;
            }
        }

        // else

        this.requireWork[slot] = null;
    }

    private boolean usePlan(final int slot) {
        final GenericStack work = this.requireWork[slot];
        this.isWorking = slot;

        boolean changed = false;
        try {
            final MEStorage dest = this.gridProxy.getStorage().getInventory();
            final IEnergySource src = this.gridProxy.getEnergy();

            if (work.amount() > 0 && work.what() instanceof AEFluidKey fluidKey) {
                // make sure strange things didn't happen...
                if (this.tanks.fill(slot, fluidKey.toStack((int) work.amount()), false) != work.amount()) {
                    changed = true;
                } else {
                    final long acquired = Platform.poweredExtraction(src, dest, fluidKey, work.amount(), this.interfaceRequestSource);
                    if (acquired > 0) {
                        changed = true;
                        final int filled = this.tanks.fill(slot, fluidKey.toStack((int) acquired), true);
                        if (filled != acquired) {
                            throw new IllegalStateException("bad attempt at managing tanks. ( fill )");
                        }
                    }
                }
            } else if (work.amount() < 0 && work.what() instanceof AEFluidKey fluidKey) {
                final long toStoreAmount = -work.amount();

                // make sure strange things didn't happen...
                final FluidStack canExtract = this.tanks.drain(slot, fluidKey.toStack((int) toStoreAmount), false);
                if (canExtract == null || canExtract.amount != toStoreAmount) {
                    changed = true;
                } else {
                    final long inserted = Platform.poweredInsert(src, dest, fluidKey, toStoreAmount, this.interfaceRequestSource);

                    if (inserted > 0) {
                        // extract items!
                        changed = true;
                        final FluidStack removed = this.tanks.drain(slot, fluidKey.toStack((int) inserted), true);
                        if (removed == null || inserted != removed.amount) {
                            throw new IllegalStateException("bad attempt at managing tanks. ( drain )");
                        }
                    }
                }
            }
        } catch (final GridAccessException e) {
            // :P
        }

        if (changed) {
            this.updatePlan(slot);
        }

        this.isWorking = -1;
        return changed;
    }

    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot) {
        onFluidInventoryChanged(inv, slot, null, null, null);
    }

    @Override
    public void onFluidInventoryChanged(final IAEFluidTank inventory, FluidStack added, FluidStack removed) {
        if (inventory == this.tanks) {
            if (added != null) {
                iHost.onStackReturnNetwork(GenericStack.fromFluidStack(added));
            }
            this.saveChanges();
        }
    }

    @Override
    public void onFluidInventoryChanged(final IAEFluidTank inventory, final int slot, InvOperation operation, FluidStack added, FluidStack removed) {
        if (this.isWorking == slot) {
            return;
        }

        if (inventory == this.config) {
            boolean cfg = hasConfig();
            this.readConfig();
            if (cfg != hasConfig) {
                resetConfigCache = true;
                this.notifyNeighbors();
            }
        } else if (inventory == this.tanks) {
            if (added != null) {
                iHost.onStackReturnNetwork(GenericStack.fromFluidStack(added));
            }
            this.saveChanges();

            final boolean had = this.hasWorkToDo();

            this.updatePlan(slot);

            final boolean now = this.hasWorkToDo();

            if (had != now) {
                try {
                    if (now) {
                        this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
                    } else {
                        this.gridProxy.getTick().sleepDevice(this.gridProxy.getNode());
                    }
                } catch (final GridAccessException e) {
                    // :P
                }
            }
        }
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(final int newValue) {
        this.priority = newValue;
    }

    public void writeToNBT(final NBTTagCompound data) {
        data.setInteger("priority", this.priority);
        this.tanks.writeToNBT(data, "storage");
        this.config.writeToNBT(data, "config");
        this.upgrades.writeToNBT(data, "upgrades");
    }

    public void readFromNBT(final NBTTagCompound data) {
        this.config.readFromNBT(data, "config");
        this.tanks.readFromNBT(data, "storage");
        this.priority = data.getInteger("priority");
        this.upgrades.readFromNBT(data, "upgrades");
        this.tanks.setCapacity((int) (Math.pow(4, this.getInstalledUpgrades(Upgrades.CAPACITY) + 1) * Fluid.BUCKET_VOLUME));
        this.readConfig();
    }

    public IAEFluidTank getConfig() {
        return this.config;
    }

    public IAEFluidTank getTanks() {
        return this.tanks;
    }

    private class InterfaceRequestSource extends MachineSource {
        private final InterfaceRequestContext context;

        InterfaceRequestSource(IActionHost v) {
            super(v);
            this.context = new InterfaceRequestContext();
        }

        @Override
        public <T> Optional<T> context(Class<T> key) {
            if (key == InterfaceRequestContext.class) {
                return (Optional<T>) Optional.of(this.context);
            }

            return super.context(key);
        }
    }

    private class InterfaceRequestContext implements Comparable<Integer> {
        @Override
        public int compareTo(Integer o) {
            return Integer.compare(DualityFluidInterface.this.priority, o);
        }
    }

    private class InterfaceInventory extends MEMonitorIFluidHandler {

        InterfaceInventory(final DualityFluidInterface tileInterface) {
            super(tileInterface.tanks);
        }

        @Override
        public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource src) {
            final Optional<InterfaceRequestContext> context = src.context(InterfaceRequestContext.class);
            final boolean isInterface = context.isPresent();

            if (isInterface) {
                return 0;
            }

            return super.insert(what, amount, mode, src);
        }

        @Override
        public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource src) {
            final Optional<InterfaceRequestContext> context = src.context(InterfaceRequestContext.class);
            final boolean hasLowerOrEqualPriority = context.map(c -> c.compareTo(DualityFluidInterface.this.priority) <= 0).orElse(false);

            if (hasLowerOrEqualPriority) {
                return 0;
            }

            return super.extract(what, amount, mode, src);
        }
    }

    public void saveChanges() {
        this.iHost.saveChanges();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack newStack) {
        if (inv == this.upgrades) {
            this.tanks.setCapacity((int) (Math.pow(4, this.getInstalledUpgrades(Upgrades.CAPACITY) + 1) * Fluid.BUCKET_VOLUME));
            try {
                this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
            } catch (GridAccessException ignored) {
            }
            for (int x = 0; x < NUMBER_OF_TANKS; x++) {
                this.updatePlan(x);
            }
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.cm;
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if (name.equals("upgrades")) {
            return this.upgrades;
        }
        return null;
    }

    @Override
    public IFluidHandler getFluidInventoryByName(final String name) {
        if (name.equals("config")) {
            return this.config;
        }
        return null;
    }

    @Override
    public int getInstalledUpgrades(Upgrades u) {
        if (this.upgrades == null) {
            return 0;
        }
        return this.upgrades.getInstalledUpgrades(u);
    }

    public void addDrops(final List<ItemStack> drops) {
        for (final ItemStack is : this.upgrades) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    @Override
    public TileEntity getTile() {
        return (TileEntity) (this.iHost instanceof TileEntity ? this.iHost : null);
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
    }

    private class Accessor implements IStorageMonitorableAccessor {

        @Nullable
        @Override
        public MEStorage getInventory(IActionSource src) {
            return DualityFluidInterface.this.getMonitorable(src);
        }

    }
}
