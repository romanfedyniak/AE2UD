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

package appeng.helpers;


import appeng.api.config.*;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
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
import appeng.core.AELog;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.MEMonitorIInventory;
import appeng.me.storage.NullInventory;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.parts.misc.PartInterface;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.AppEngNetworkInventory;
import appeng.tile.networking.TileCableBus;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.*;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import de.ellpeck.actuallyadditions.api.tile.IPhantomTile;
import gregtech.api.block.machines.BlockMachine;
import gregtech.api.metatileentity.MetaTileEntity;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.*;

import static appeng.api.config.LockCraftingMode.LOCK_UNTIL_PULSE;
import static appeng.api.config.LockCraftingMode.LOCK_UNTIL_RESULT;
import static appeng.helpers.ItemStackHelper.stackFromNBT;
import static appeng.helpers.ItemStackHelper.stackToNBT;


public class DualityInterface implements IGridTickable, MEStorage, IInventoryDestination, IAEAppEngInventory, IConfigManagerHost, ICraftingProvider, IUpgradeableHost {
    public static final int NUMBER_OF_STORAGE_SLOTS = 9;
    public static final int NUMBER_OF_CONFIG_SLOTS = 9;
    public static final int NUMBER_OF_PATTERN_SLOTS = 36;

    private static final Collection<Block> BAD_BLOCKS = new HashSet<>(100);
    private final GenericStack[] requireWork = {null, null, null, null, null, null, null, null, null};
    private final MultiCraftingTracker craftingTracker;
    private final AENetworkProxy gridProxy;
    private final IInterfaceHost iHost;
    private final IActionSource mySource;
    private final IActionSource interfaceRequestSource;
    private final ConfigManager cm = new ConfigManager(this);
    private final AppEngInternalAEInventory config = new AppEngInternalAEInventory(this, NUMBER_OF_CONFIG_SLOTS, 512);
    private final AppEngInternalInventory storage;
    private final AppEngInternalInventory patterns = new AppEngInternalInventory(this, NUMBER_OF_PATTERN_SLOTS, 1);
    /** The network's storage, covering every registered key type. Swapped out as the grid connects/disconnects. */
    private MEStorage networkStorage = NullInventory.of();
    private final UpgradeInventory upgrades;
    private final Accessor accessor = new Accessor();
    private boolean hasConfig = false;
    private int priority;
    private Set<ICraftingPatternDetails> craftingList = null;
    private List<ItemStack> waitingToSend = null;
    private MEStorage destination;
    private int isWorking = -1;
    private EnumSet<EnumFacing> visitedFaces = EnumSet.noneOf(EnumFacing.class);
    private EnumMap<EnumFacing, List<ItemStack>> waitingToSendFacing = new EnumMap<>(EnumFacing.class);
    private boolean resetConfigCache = true;
    private MEStorage configCachedHandler;

    private YesNo redstoneState = YesNo.UNDECIDED;

    @Nullable
    private UnlockCraftingEvent unlockEvent;
    @Nullable
    private GenericStack unlockStack;

    public DualityInterface(final AENetworkProxy networkProxy, final IInterfaceHost ih) {
        this.gridProxy = networkProxy;
        this.gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);

        this.upgrades = new StackUpgradeInventory(this.gridProxy.getMachineRepresentation(), this, 4);
        this.cm.registerSetting(Settings.BLOCK, YesNo.NO);
        this.cm.registerSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);
        this.cm.registerSetting(Settings.UNLOCK, LockCraftingMode.NONE);

        this.iHost = ih;
        this.craftingTracker = new MultiCraftingTracker(this.iHost, 9);

        final MachineSource actionSource = new MachineSource(this.iHost);
        this.mySource = actionSource;
        this.storage = new AppEngNetworkInventory(this::getStorageGrid, this.mySource, this, NUMBER_OF_STORAGE_SLOTS, 512);

        this.interfaceRequestSource = new InterfaceRequestSource(this.iHost);
    }

    @Nullable
    private IStorageService getStorageGrid() {
        try {
            return this.gridProxy.getStorage();
        } catch (GridAccessException e) {
            return null;
        }
    }

    private static boolean invIsCustomBlocking(BlockingInventoryAdaptor inv) {
        return (inv.containsBlockingItems());
    }

    @Override
    public void saveChanges() {
        this.iHost.saveChanges();
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc, final ItemStack removed, final ItemStack added) {
        if (this.isWorking == slot) {
            return;
        }

        if (inv == this.config && (!removed.isEmpty() || !added.isEmpty())) {
            boolean cfg = hasConfig();
            this.readConfig();
            if (cfg != hasConfig) {
                resetConfigCache = true;
                this.notifyNeighbors();
            }
        } else if (inv == this.patterns && (!removed.isEmpty() || !added.isEmpty())) {
            this.updateCraftingList();
        } else if (inv == this.storage && slot >= 0) {
            if (added != ItemStack.EMPTY){
                iHost.onStackReturnNetwork(GenericStack.fromItemStack(added));
            }
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

    public void writeToNBT(final NBTTagCompound data) {
        this.config.writeToNBT(data, "config");
        this.patterns.writeToNBT(data, "patterns");
        this.storage.writeToNBT(data, "storage");
        this.upgrades.writeToNBT(data, "upgrades");
        this.cm.writeToNBT(data);
        this.craftingTracker.writeToNBT(data);
        data.setInteger("priority", this.priority);

        if (unlockEvent == UnlockCraftingEvent.PULSE) {
            data.setByte("unlockEvent", (byte) 1);
        } else if (unlockEvent == UnlockCraftingEvent.RESULT) {
            if (unlockStack != null) {
                data.setByte("unlockEvent", (byte) 2);
                NBTTagCompound unlockStackTag = new NBTTagCompound();
                GenericStack.writeTag(unlockStackTag, unlockStack);
                data.setTag("unlockStack", unlockStackTag);
            } else {
                AELog.error("Saving MEInterface {}, locked waiting for stack, but stack is null!", iHost);
            }
        }
        final NBTTagList waitingToSend = new NBTTagList();
        if (this.waitingToSend != null) {
            for (final ItemStack is : this.waitingToSend) {
                final NBTTagCompound itemNBT = stackToNBT(is);
                waitingToSend.appendTag(itemNBT);
            }
        }
        data.setTag("waitingToSend", waitingToSend);

        NBTTagCompound sidedWaitList = new NBTTagCompound();

        if (this.waitingToSendFacing != null) {
            for (EnumFacing s : this.iHost.getTargets()) {
                NBTTagList waitingListSided = new NBTTagList();
                if (this.waitingToSendFacing.containsKey(s)) {
                    for (final ItemStack is : this.waitingToSendFacing.get(s)) {
                        final NBTTagCompound itemNBT = stackToNBT(is);
                        waitingListSided.appendTag(itemNBT);
                    }
                    sidedWaitList.setTag(s.name(), waitingListSided);
                }
            }
        }
        data.setTag("sidedWaitList", sidedWaitList);
    }

    public void readFromNBT(final NBTTagCompound data) {
        this.waitingToSend = null;
        final NBTTagList waitingList = data.getTagList("waitingToSend", 10);
        if (waitingList != null) {
            for (int x = 0; x < waitingList.tagCount(); x++) {
                final NBTTagCompound c = waitingList.getCompoundTagAt(x);
                if (c != null) {
                    final ItemStack is = stackFromNBT(c);
                    this.addToSendList(is);
                }
            }
        }

        this.waitingToSendFacing = null;
        final NBTTagCompound waitingListSided = data.getCompoundTag("sidedWaitList");

        for (EnumFacing s : EnumFacing.values()) {
            if (waitingListSided.hasKey(s.name())) {
                NBTTagList w = waitingListSided.getTagList(s.name(), 10);
                for (int x = 0; x < w.tagCount(); x++) {
                    final NBTTagCompound c = w.getCompoundTagAt(x);
                    if (c != null) {
                        final ItemStack is = stackFromNBT(c);
                        this.addToSendListFacing(is, EnumFacing.byIndex(s.getIndex()));
                    }
                }
            }
        }

        this.craftingTracker.readFromNBT(data);

        // fix upgrade slot size mismatch
        NBTTagCompound up = data.getCompoundTag("upgrades");
        if (up.hasKey("Size") && up.getInteger("Size") != this.upgrades.getSlots()) {
            up.setInteger("Size", this.upgrades.getSlots());
            this.upgrades.writeToNBT(up, "upgrades");
        }

        this.upgrades.readFromNBT(data, "upgrades");
        this.config.readFromNBT(data, "config");

        NBTTagCompound pa = data.getCompoundTag("patterns");
        if (pa.hasKey("Size") && pa.getInteger("Size") != this.patterns.getSlots()) {
            pa.setInteger("Size", this.patterns.getSlots());
            this.upgrades.writeToNBT(pa, "patterns");
        }

        this.patterns.readFromNBT(data, "patterns");
        this.storage.readFromNBT(data, "storage");
        this.priority = data.getInteger("priority");
        this.cm.readFromNBT(data);
        this.readConfig();
        this.updateCraftingList();

        var unlockEventType = data.getByte("unlockEvent");

        this.unlockEvent = switch (unlockEventType) {
            case 0 -> null;
            case 1 -> UnlockCraftingEvent.PULSE;
            case 2 -> UnlockCraftingEvent.RESULT;
            default -> {
                AELog.error("Unknown unlock event type {} in NBT for MEInterface: {}", unlockEventType, data);
                yield null;
            }
        };

        if (this.unlockEvent == UnlockCraftingEvent.RESULT) {
            this.unlockStack = GenericStack.readTag(data.getCompoundTag("unlockStack"));
            if (this.unlockStack == null) {
                AELog.error("Could not load unlock stack for MEInterface from NBT: {}", data);
            }
        } else {
            this.unlockStack = null;
        }
    }

    private void addToSendList(final ItemStack is) {
        if (is.isEmpty()) {
            return;
        }

        if (this.waitingToSend == null) {
            this.waitingToSend = new ArrayList<>();
        }

        this.waitingToSend.add(is);

        try {
            this.gridProxy.getTick().wakeDevice(this.gridProxy.getNode());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    private void addToSendListFacing(final ItemStack is, EnumFacing f) {
        if (is.isEmpty()) {
            return;
        }
        if (this.waitingToSendFacing == null) {
            this.waitingToSendFacing = new EnumMap<>(EnumFacing.class);
        }

        this.waitingToSendFacing.computeIfAbsent(f, k -> new ArrayList<>());

        this.waitingToSendFacing.get(f).add(is);

        try {
            this.gridProxy.getTick().wakeDevice(this.gridProxy.getNode());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    private void readConfig() {
        this.hasConfig = false;

        for (final ItemStack p : this.config) {
            if (!p.isEmpty()) {
                this.hasConfig = true;
                break;
            }
        }

        final boolean had = this.hasWorkToDo();

        for (int x = 0; x < NUMBER_OF_CONFIG_SLOTS; x++) {
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

    private void updateCraftingList() {
        final Boolean[] accountedFor = new Boolean[this.patterns.getSlots()];
        Arrays.fill(accountedFor, false);

        if (!this.gridProxy.isReady()) {
            return;
        }

        boolean removed = false;

        if (this.craftingList != null) {
            final Iterator<ICraftingPatternDetails> i = this.craftingList.iterator();
            while (i.hasNext()) {
                final ICraftingPatternDetails details = i.next();
                boolean found = false;

                for (int x = 0; x < accountedFor.length; x++) {
                    final ItemStack is = this.patterns.getStackInSlot(x);
                    if (details.getPattern() == is) {
                        accountedFor[x] = found = true;
                    }
                }

                if (!found) {
                    removed = true;
                    i.remove();
                }
            }
        }

        boolean newPattern = false;

        for (int x = 0; x < accountedFor.length; x++) {
            if (!accountedFor[x]) {
                newPattern = true;
                this.addToCraftingList(this.patterns.getStackInSlot(x));
            }
        }

        if (newPattern || removed) {
            try {
                this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
            } catch (GridAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean hasWorkToDo() {

        if (hasItemsToSend()) {
            return true;
        }

        if (hasItemsToSendFacing()) {
            return true;
        }

        for (final GenericStack requiredWork : this.requireWork) {
            if (requiredWork != null) {
                return true;
            }
        }
        return false;
    }

    private void updatePlan(final int slot) {
        GenericStack req = this.config.getAEStackInSlot(slot);
        if (req != null && req.amount() <= 0) {
            this.config.setStackInSlot(slot, ItemStack.EMPTY);
            req = null;
        }

        // This interface stocks items only; the universal one is stage 3 of the fluids decomposition. Until
        // then a configured key of any other type counts as an empty slot rather than as work.
        //
        // It used to count as work: the config inventory became generic in stage 0, so a fluid can be put in
        // the slot, updatePlan happily planned for it, and usePlan then fell through its `instanceof
        // AEItemKey` guard and did nothing - leaving requireWork set forever, so hasWorkToDo() stayed true
        // and the interface never went back to sleep. Nothing visible, just a machine ticking at its fastest
        // rate for good.
        if (req != null && !(req.what() instanceof AEItemKey)) {
            req = null;
        }

        final ItemStack stored = this.storage.getStackInSlot(slot);

        if (req == null && !stored.isEmpty()) {
            final AEItemKey storedKey = AEItemKey.of(stored);
            this.requireWork[slot] = new GenericStack(storedKey, -stored.getCount());
            return;
        } else if (req != null) {
            if (stored.isEmpty()) // need to add stuff!
            {
                this.requireWork[slot] = req;
                return;
            } else if (req.what() instanceof AEItemKey reqKey && reqKey.matches(stored)) // same type and quantity  )!
            {
                if (req.amount() == stored.getCount()) {
                    this.requireWork[slot] = null;
                } else                                // same type ( qty different? )!
                {
                    this.requireWork[slot] = new GenericStack(req.what(), req.amount() - stored.getCount());
                }
                return;
            } else
            // Stored != null; dispose!
            {
                final AEItemKey storedKey = AEItemKey.of(stored);
                this.requireWork[slot] = new GenericStack(storedKey, -stored.getCount());
                return;
            }
        }

        // else

        this.requireWork[slot] = null;
    }

    public void notifyNeighbors() {
        if (this.gridProxy.isActive()) {
            try {
                this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
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

    private void addToCraftingList(final ItemStack is) {
        if (is.isEmpty()) {
            return;
        }

        if (is.getItem() instanceof ICraftingPatternItem cpi) {
            final ICraftingPatternDetails details = cpi.getPatternForItem(is, this.iHost.getTileEntity().getWorld());

            if (details != null) {
                if (this.craftingList == null) {
                    this.craftingList = new ObjectOpenHashSet<>();
                }

                this.craftingList.add(details);
            }
        }
    }

    private boolean hasItemsToSend() {
        return this.waitingToSend != null && !this.waitingToSend.isEmpty();
    }

    private boolean hasItemsToSendFacing() {
        if (waitingToSendFacing != null) {
            for (EnumFacing enumFacing : waitingToSendFacing.keySet()) {
                if (!waitingToSendFacing.get(enumFacing).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void dropExcessPatterns() {
        IItemHandler patterns = getPatterns();

        List<ItemStack> dropList = new ArrayList<>();
        for (int invSlot = 0; invSlot < patterns.getSlots(); invSlot++) {
            if (invSlot > 8 + this.getInstalledUpgrades(Upgrades.PATTERN_EXPANSION) * 9) {
                ItemStack is = patterns.getStackInSlot(invSlot);
                if (is.isEmpty()) {
                    continue;
                }
                dropList.add(patterns.extractItem(invSlot, Integer.MAX_VALUE, false));
            }
        }
        if (dropList.size() > 0) {
            World world = this.getLocation().getWorld();
            BlockPos blockPos = this.getLocation().getPos();
            Platform.spawnDrops(world, blockPos, dropList);
        }

        this.gridProxy.setIdlePowerUsage(Math.pow(4, (this.getInstalledUpgrades(Upgrades.PATTERN_EXPANSION))));
    }

    @Override
    public boolean canInsert(final ItemStack stack) {
        final AEItemKey what = AEItemKey.of(stack);
        final long inserted = this.destination.insert(what, stack.getCount(), Actionable.SIMULATE, null);
        return inserted != 0;
    }

    public IItemHandler getConfig() {
        return this.config;
    }

    public IItemHandler getPatterns() {
        return this.patterns;
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

    public IItemHandler getInternalInventory() {
        return this.storage;
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

        //Previous version might have items saved in this list
        //recover them
        if (this.hasItemsToSend()) {
            this.pushItemsOut(this.iHost.getTargets());
        }

        if (hasItemsToSendFacing()) {
            for (EnumFacing enumFacing : waitingToSendFacing.keySet()) {
                this.pushItemsOut(enumFacing);
            }
        }

        final boolean couldDoWork = this.updateStorage();
        return this.hasWorkToDo() ? (couldDoWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER) : TickRateModulation.SLEEP;
    }

    private void pushItemsOut(final EnumSet<EnumFacing> possibleDirections) {
        if (!this.hasItemsToSend()) {
            return;
        }

        final TileEntity tile = this.iHost.getTileEntity();
        final World w = tile.getWorld();

        final Iterator<ItemStack> i = this.waitingToSend.iterator();
        while (i.hasNext()) {
            ItemStack whatToSend = i.next();

            for (final EnumFacing s : possibleDirections) {
                final TileEntity te = w.getTileEntity(tile.getPos().offset(s));
                if (te == null) {
                    continue;
                }

                final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());
                if (ad != null) {
                    final ItemStack result = ad.addItems(whatToSend);

                    if (result.isEmpty()) {
                        whatToSend = ItemStack.EMPTY;
                    } else {
                        whatToSend.setCount(result.getCount());
                        whatToSend.setTagCompound(result.getTagCompound());
                    }

                    if (whatToSend.isEmpty()) {
                        break;
                    }
                }
            }

            if (whatToSend.isEmpty()) {
                i.remove();
            }
        }

        if (this.waitingToSend.isEmpty()) {
            this.waitingToSend = null;
        }
    }

    private void pushItemsOut(final EnumFacing s) {
        if (!this.waitingToSendFacing.containsKey(s) || (this.waitingToSendFacing.containsKey(s) && this.waitingToSendFacing.get(s).isEmpty())) {
            return;
        }

        final TileEntity tile = this.iHost.getTileEntity();
        final World w = tile.getWorld();

        final TileEntity te = w.getTileEntity(tile.getPos().offset(s));
        if (te == null) {
            return;
        }

        if (te instanceof IInterfaceHost || (te instanceof TileCableBus && ((TileCableBus) te).getPart(s.getOpposite()) instanceof PartInterface)) {
            try {
                IInterfaceHost targetTE;
                if (te instanceof IInterfaceHost) {
                    targetTE = (IInterfaceHost) te;
                } else {
                    targetTE = (IInterfaceHost) ((TileCableBus) te).getPart(s.getOpposite());
                }

                if (!targetTE.getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
                    IStorageMonitorableAccessor mon = te.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, s.getOpposite());
                    if (mon != null) {
                        MEStorage inv = mon.getInventory(this.mySource);
                        if (inv != null && Platform.canAccess(targetTE.getInterfaceDuality().gridProxy, this.mySource)) {
                            final Iterator<ItemStack> i = this.waitingToSendFacing.get(s).iterator();
                            while (i.hasNext()) {
                                ItemStack whatToSend = i.next();
                                final AEItemKey what = AEItemKey.of(whatToSend);
                                final long inserted = inv.insert(what, whatToSend.getCount(), Actionable.MODULATE, this.mySource);
                                if (inserted >= whatToSend.getCount()) {
                                    i.remove();
                                } else {
                                    whatToSend.setCount((int) (whatToSend.getCount() - inserted));
                                }
                            }
                            if (this.waitingToSendFacing.get(s).isEmpty()) {
                                this.waitingToSendFacing.remove(s);
                            }
                        }
                    }
                } else {
                    return;
                }
            } catch (GridAccessException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());

        final Iterator<ItemStack> i = this.waitingToSendFacing.get(s).iterator();
        while (i.hasNext()) {
            ItemStack whatToSend = i.next();
            if (ad != null) {
                final ItemStack result = ad.addItems(whatToSend);
                if (!result.isEmpty()) {
                    whatToSend.setCount(result.getCount());
                    whatToSend.setTagCompound(result.getTagCompound());
                } else {
                    i.remove();
                }
            }
        }

        if (this.waitingToSendFacing.get(s).isEmpty()) {
            this.waitingToSendFacing.remove(s);
        }
    }

    private boolean updateStorage() {
        boolean didSomething = false;

        for (int x = 0; x < NUMBER_OF_STORAGE_SLOTS; x++) {
            if (this.requireWork[x] != null) {
                didSomething = this.usePlan(x, this.requireWork[x]) || didSomething;
            }
        }

        return didSomething;
    }

    private boolean usePlan(final int x, final GenericStack itemStack) {
        final InventoryAdaptor adaptor = this.getAdaptor(x);
        this.isWorking = x;

        boolean changed = false;
        try {
            this.destination = this.gridProxy.getStorage().getInventory();
            final IEnergySource src = this.gridProxy.getEnergy();

            if (itemStack.amount() < 0) {
                final GenericStack toStore = new GenericStack(itemStack.what(), -itemStack.amount());
                long diff = toStore.amount();

                // make sure strange things didn't happen...
                // TODO: check if OK
                final ItemStack canExtract = adaptor.simulateRemove((int) diff, ((AEItemKey) toStore.what()).toStack(), null);
                if (canExtract.isEmpty()) {
                    changed = true;
                    throw new GridAccessException();
                }

                final long insertedAmt = Platform.poweredInsert(src, this.destination, toStore.what(), toStore.amount(), this.interfaceRequestSource);

                if (insertedAmt != 0) {
                    // extract items!
                    changed = true;
                    final ItemStack removed = adaptor.removeItems((int) insertedAmt, ItemStack.EMPTY, null);
                    if (removed.isEmpty()) {
                        throw new IllegalStateException("bad attempt at managing inventory. ( removeItems )");
                    }
                }
            }

            if (this.craftingTracker.isBusy(x)) {
                changed = this.handleCrafting(x, adaptor, itemStack) || changed;
            } else if (itemStack.amount() > 0 && itemStack.what() instanceof AEItemKey itemKey) {
                // make sure strange things didn't happen...

                ItemStack inputStack = itemKey.toStack((int) Math.min(itemStack.amount(), Integer.MAX_VALUE));

                ItemStack remaining = adaptor.simulateAdd(inputStack);

                if (!remaining.isEmpty()) {
                    changed = true;
                    throw new GridAccessException();
                }

                final long acquired = Platform.poweredExtraction(src, this.destination, itemKey, itemStack.amount(), this.interfaceRequestSource);
                if (acquired > 0) {
                    changed = true;
                    inputStack.setCount(Ints.saturatedCast(acquired));
                    final ItemStack issue = adaptor.addItems(inputStack);
                    if (!issue.isEmpty()) {
                        throw new IllegalStateException("bad attempt at managing inventory. ( addItems )");
                    }
                } else if (this.gridProxy.getCrafting().isCraftable(itemKey)) {
                    changed = this.handleCrafting(x, adaptor, itemStack) || changed;
                }
            }
            // else wtf?
        } catch (final GridAccessException e) {
            // :P
        }

        if (changed) {
            this.updatePlan(x);
        }

        this.isWorking = -1;
        return changed;
    }

    private InventoryAdaptor getAdaptor(final int slot) {
        return new AdaptorItemHandler(((AppEngNetworkInventory) this.storage).getBufferWrapper(slot));
    }

    private boolean handleCrafting(final int x, final InventoryAdaptor d, final GenericStack itemStack) {
        try {
            if (this.getInstalledUpgrades(Upgrades.CRAFTING) > 0 && itemStack != null) {
                return this.craftingTracker.handleCrafting(x, itemStack.amount(), itemStack.what(), d, this.iHost.getTileEntity().getWorld(), this.gridProxy.getGrid(), this.gridProxy.getCrafting(), this.mySource);
            }
        } catch (final GridAccessException e) {
            // :P
        }

        return false;
    }

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        if (this.upgrades == null) {
            return 0;
        }
        return this.upgrades.getInstalledUpgrades(u);
    }

    @Override
    public TileEntity getTile() {
        return (TileEntity) (this.iHost instanceof TileEntity ? this.iHost : null);
    }

    /**
     * The MEStorage view of this interface: the interface's own storage slots while it has a config
     * (whitelist mode), or the whole network otherwise.
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

    private boolean hasConfig() {
        return this.hasConfig;
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        if (name.equals("storage")) {
            return this.storage;
        }

        if (name.equals("patterns")) {
            return this.patterns;
        }

        if (name.equals("config")) {
            return this.config;
        }

        if (name.equals("upgrades")) {
            return this.upgrades;
        }

        return null;
    }

    public IItemHandler getStorage() {
        return this.storage;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.cm;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (this.getInstalledUpgrades(Upgrades.CRAFTING) == 0) {
            this.cancelCrafting();
        }

        if (newValue == LockCraftingMode.NONE) {
            resetCraftingLock();
        }

        this.iHost.saveChanges();
    }

    private void cancelCrafting() {
        this.craftingTracker.cancel();
    }

    public MEStorage getMonitorable(final IActionSource src, final MEStorage myInterface) {
        if (Platform.canAccess(this.gridProxy, src)) {
            return myInterface;
        }

        return new InterfaceInventory(this);
    }

    private boolean invIsBlocked(InventoryAdaptor inv) {
        return (inv.containsItems());
    }

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        if (this.hasItemsToSend() || this.hasItemsToSendFacing() || !this.gridProxy.isActive() || !this.craftingList.contains(patternDetails)) {
            return false;
        }

        final TileEntity tile = this.iHost.getTileEntity();
        final World w = tile.getWorld();

        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }

        if (this.visitedFaces.isEmpty()) {
            this.visitedFaces = this.iHost.getTargets();
        }

        for (final EnumFacing s : visitedFaces) {
            final TileEntity te = w.getTileEntity(tile.getPos().offset(s));
            if (te == null) {
                visitedFaces.remove(s);
                continue;
            }

            var mon = te.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, s.getOpposite());
            if (mon != null) {
                visitedFaces.remove(s);

                try {
                    IGridProxyable proxyable;
                    if (te instanceof IGridProxyable) {
                        proxyable = (IGridProxyable) te;
                    } else if (te instanceof IPartHost partHost) {
                        proxyable = (IGridProxyable) partHost.getPart(s.getOpposite());
                    } else {
                        continue;
                    }

                    if (proxyable.getProxy().getGrid() == this.gridProxy.getGrid()) {
                        continue;
                    }

                    MEStorage inv = mon.getInventory(this.mySource);
                    if (inv != null && Platform.canAccess(proxyable.getProxy(), this.mySource)) {
                        if (this.isBlocking() && !inv.getAvailableStacks().isEmpty()) {
                            continue;
                        } else {
                            var allItemsCanBeInserted = true;
                            for (int x = 0; x < table.getSizeInventory(); x++) {
                                final ItemStack is = table.getStackInSlot(x);
                                if (is.isEmpty()) {
                                    continue;
                                }
                                final AEItemKey what = AEItemKey.of(is);
                                final long inserted = inv.insert(what, is.getCount(), Actionable.SIMULATE, this.mySource);
                                if (inserted != is.getCount()) {
                                    allItemsCanBeInserted = false;
                                    break;
                                }
                            }

                            if (!allItemsCanBeInserted) {
                                continue;
                            }

                            this.visitedFaces.clear();
                            for (int x = 0; x < table.getSizeInventory(); x++) {
                                final ItemStack is = table.getStackInSlot(x);
                                if (!is.isEmpty()) {
                                    addToSendListFacing(is, s);
                                }
                            }
                            onPushPatternSuccess(patternDetails);
                            pushItemsOut(s);

                            return true;
                        }
                    }
                } catch (final GridAccessException e) {
                    continue;
                }
                continue;
            }

            if (te instanceof ICraftingMachine cm) {
                if (cm.acceptsPlans()) {
                    visitedFaces.remove(s);
                    if (cm.pushPattern(patternDetails, table, s.getOpposite())) {
                        onPushPatternSuccess(patternDetails);
                        return true;
                    }
                    continue;
                }
            }

            InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());
            if (ad != null) {
                if (this.isBlocking()) {
                    IPhantomTile phantomTE;
                    if (Platform.isModLoaded("actuallyadditions") && te instanceof IPhantomTile) {
                        phantomTE = ((IPhantomTile) te);
                        if (phantomTE.hasBoundPosition()) {
                            TileEntity phantom = w.getTileEntity(phantomTE.getBoundPosition());
                            if (NonBlockingItems.INSTANCE.getMap().containsKey(w.getBlockState(phantomTE.getBoundPosition()).getBlock().getRegistryName().getNamespace())) {
                                if (isCustomInvBlocking(phantom, s)) {
                                    visitedFaces.remove(s);
                                    continue;
                                }
                            }
                        }
                    } else if (NonBlockingItems.INSTANCE.getMap().containsKey(w.getBlockState(tile.getPos().offset(s)).getBlock().getRegistryName().getNamespace())) {
                        if (isCustomInvBlocking(te, s)) {
                            visitedFaces.remove(s);
                            continue;
                        }
                    } else if (invIsBlocked(ad)) {
                        visitedFaces.remove(s);
                        continue;
                    }
                }

                if (this.acceptsItems(ad, table)) {
                    this.visitedFaces.clear();
                    for (int x = 0; x < table.getSizeInventory(); x++) {
                        final ItemStack is = table.getStackInSlot(x);
                        if (!is.isEmpty()) {
                            addToSendListFacing(is, s);
                        }
                    }
                    onPushPatternSuccess(patternDetails);
                    pushItemsOut(s);
                    return true;
                }
            }
            visitedFaces.remove(s);
        }
        return false;
    }

    public void resetCraftingLock() {
        if (unlockEvent != null) {
            unlockEvent = null;
            unlockStack = null;
            saveChanges();
        }
    }

    private void onPushPatternSuccess(ICraftingPatternDetails pattern) {
        resetCraftingLock();

        LockCraftingMode lockMode = (LockCraftingMode) cm.getSetting(Settings.UNLOCK);
        switch (lockMode) {
            case LOCK_UNTIL_PULSE -> {
                unlockEvent = UnlockCraftingEvent.PULSE;
                saveChanges();
            }
            case LOCK_UNTIL_RESULT -> {
                unlockEvent = UnlockCraftingEvent.RESULT;
                unlockStack = pattern.getPrimaryOutput();
                saveChanges();
            }
        }
    }

    /**
     * Gets if the crafting lock is in effect and why.
     *
     * @return {@link LockCraftingMode#NONE} if the lock isn't in effect
     */
    public LockCraftingMode getCraftingLockedReason() {
        var lockMode = cm.getSetting(Settings.UNLOCK);
        if (lockMode == LockCraftingMode.LOCK_WHILE_LOW && !getRedstoneState()) {
            // Crafting locked by redstone signal
            return LockCraftingMode.LOCK_WHILE_LOW;
        } else if (lockMode == LockCraftingMode.LOCK_WHILE_HIGH && getRedstoneState()) {
            return LockCraftingMode.LOCK_WHILE_HIGH;
        } else if (unlockEvent != null) {
            // Crafting locked by waiting for unlock event
            switch (unlockEvent) {
                case PULSE -> {
                    return LOCK_UNTIL_PULSE;
                }
                case RESULT -> {
                    return LOCK_UNTIL_RESULT;
                }
            }
        }
        return LockCraftingMode.NONE;
    }

    @Override
    public boolean isBusy() {
        boolean busy = false;

        if (this.hasItemsToSend() || hasItemsToSendFacing()) {
            return true;
        }

        if (this.isBlocking()) {
            final EnumSet<EnumFacing> possibleDirections = this.iHost.getTargets();
            final TileEntity tile = this.iHost.getTileEntity();
            final World w = tile.getWorld();

            boolean allAreBusy = true;

            for (final EnumFacing s : possibleDirections) {
                final TileEntity te = w.getTileEntity(tile.getPos().offset(s));

                if (te instanceof IInterfaceHost || (te instanceof TileCableBus && ((TileCableBus) te).getPart(s.getOpposite()) instanceof PartInterface)) {
                    try {
                        IInterfaceHost targetTE;
                        if (te instanceof IInterfaceHost) {
                            targetTE = (IInterfaceHost) te;
                        } else {
                            targetTE = (IInterfaceHost) ((TileCableBus) te).getPart(s.getOpposite());
                        }

                        if (targetTE.getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
                            continue;
                        } else {
                            IStorageMonitorableAccessor mon = te.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, s.getOpposite());
                            if (mon != null) {
                                MEStorage inv = mon.getInventory(this.mySource);
                                if (inv != null && Platform.canAccess(targetTE.getInterfaceDuality().gridProxy, this.mySource)) {
                                    if (inv.getAvailableStacks().isEmpty()) {
                                        allAreBusy = false;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (final GridAccessException e) {
                        continue;
                    }
                    continue;
                }

                final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());
                if (ad != null) {
                    if (Platform.isModLoaded("actuallyadditions") && Platform.GTLoaded && te instanceof IPhantomTile phantomTE) {
                        if (phantomTE.hasBoundPosition()) {
                            TileEntity phantom = w.getTileEntity(phantomTE.getBoundPosition());
                            if (NonBlockingItems.INSTANCE.getMap().containsKey(w.getBlockState(phantomTE.getBoundPosition()).getBlock().getRegistryName().getNamespace())) {
                                if (!isCustomInvBlocking(phantom, s)) {
                                    allAreBusy = false;
                                    break;
                                }
                            }
                        }
                    } else if (NonBlockingItems.INSTANCE.getMap().containsKey(w.getBlockState(tile.getPos().offset(s)).getBlock().getRegistryName().getNamespace())) {
                        if (!isCustomInvBlocking(te, s)) {
                            allAreBusy = false;
                            break;
                        }
                    } else {
                        if (!invIsBlocked(ad)) {
                            allAreBusy = false;
                            break;
                        }
                    }
                }
            }
            busy = allAreBusy;
        }
        return busy;
    }

    boolean isCustomInvBlocking(TileEntity te, EnumFacing s) {
        BlockingInventoryAdaptor blockingInventoryAdaptor = BlockingInventoryAdaptor.getAdaptor(te, s.getOpposite());
        return invIsCustomBlocking(blockingInventoryAdaptor);
    }

    private boolean sameGrid(final IGrid grid) throws GridAccessException {
        return grid == this.gridProxy.getGrid();
    }

    private boolean isBlocking() {
        return this.cm.getSetting(Settings.BLOCK) == YesNo.YES;
    }

    private boolean acceptsItems(final InventoryAdaptor ad, final InventoryCrafting table) {
        for (int x = 0; x < table.getSizeInventory(); x++) {
            final ItemStack is = table.getStackInSlot(x);
            if (is.isEmpty()) {
                continue;
            }

            if (!ad.simulateAdd(is).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void provideCrafting(final ICraftingProviderHelper craftingTracker) {
        if (this.gridProxy.isActive() && this.craftingList != null) {
            for (final ICraftingPatternDetails details : this.craftingList) {
                details.setPriority(this.priority);
                craftingTracker.addCraftingOption(this, details);
            }
        }
    }

    public void addDrops(final List<ItemStack> drops) {
        if (this.waitingToSend != null) {
            for (final ItemStack is : this.waitingToSend) {
                if (!is.isEmpty()) {
                    drops.add(is);
                }
            }
        }

        if (this.waitingToSendFacing != null) {
            for (List<ItemStack> itemList : waitingToSendFacing.values()) {
                for (final ItemStack is : itemList) {
                    if (!is.isEmpty()) {
                        drops.add(is);
                    }
                }
            }
        }

        for (final ItemStack is : this.upgrades) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }

        for (final ItemStack is : this.storage) {
            if (!is.isEmpty()) {
                int maxStackSize = is.getMaxStackSize();
                while (is.getCount() > maxStackSize) {
                    ItemStack portionedStack = is.copy();
                    portionedStack.setCount(maxStackSize);
                    is.shrink(maxStackSize);
                    drops.add(portionedStack);
                }
                drops.add(is);
            }
        }

        for (final ItemStack is : this.patterns) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    public IUpgradeableHost getHost() {
        if (this.getPart() instanceof IUpgradeableHost) {
            return (IUpgradeableHost) this.getPart();
        }
        if (this.getTile() instanceof IUpgradeableHost) {
            return (IUpgradeableHost) this.getTile();
        }
        return null;
    }

    private IPart getPart() {
        return (IPart) (this.iHost instanceof IPart ? this.iHost : null);
    }

    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.craftingTracker.getRequestedJobs();
    }

    public GenericStack injectCraftedItems(final ICraftingLink link, final GenericStack items, final Actionable mode) {
        final int slot = this.craftingTracker.getSlot(link);

        if (items != null && slot >= 0 && slot <= this.requireWork.length && items.what() instanceof AEItemKey itemKey) {
            final InventoryAdaptor adaptor = this.getAdaptor(slot);
            final ItemStack toInsert = itemKey.toStack((int) Math.min(items.amount(), Integer.MAX_VALUE));

            if (mode == Actionable.SIMULATE) {
                return GenericStack.fromItemStack(adaptor.simulateAdd(toInsert));
            } else {
                final GenericStack remainder = GenericStack.fromItemStack(adaptor.addItems(toInsert));
                this.updatePlan(slot);
                return remainder;
            }
        }

        return items;
    }

    public void jobStateChange(final ICraftingLink link) {
        this.craftingTracker.jobStateChange(link);
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

            if (directedTile instanceof IInterfaceHost) {
                try {
                    if (((IInterfaceHost) directedTile).getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
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
                    /* getTranslationKey() and getUnlocalizedNameInefficiently() have different return values in some mod
                     * For the Thermal Expansion
                     * getTranslationKey() returns complete key ending with ".name".
                     * getUnlocalizedNameInefficiently() returns localized name
                     * Because CoFH Core overrides method getTranslationKey()
                     */
                    return what.getItem().getTranslationKey(what);
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
        return ((long) te.getPos().getZ() << 24) ^ ((long) te.getPos().getX() << 8) ^ te.getPos().getY();
    }

    public void initialize() {
        this.updateCraftingList();
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.iHost.saveChanges();

        try {
            this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
        } catch (final GridAccessException e) {
            // :P
        }
    }

    public boolean hasCapability(Capability<?> capabilityClass, EnumFacing facing) {
        return capabilityClass == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || capabilityClass == Capabilities.STORAGE_MONITORABLE_ACCESSOR;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capabilityClass, EnumFacing facing) {
        if (capabilityClass == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) this.storage;
        } else if (capabilityClass == Capabilities.STORAGE_MONITORABLE_ACCESSOR) {
            return (T) this.accessor;
        }
        return null;
    }

    public void updateRedstoneState() {
        // If we're waitingh for a pulse, update immediately
        if (unlockEvent == UnlockCraftingEvent.PULSE && getRedstoneState()) {
            unlockEvent = null; // Unlocked!
        } else {
            // Otherwise, just reset back to undecided
            redstoneState = YesNo.UNDECIDED;
        }
        saveChanges(); // In any case, this needs to be changed since the state is now outdated
    }

    /**
     * @return Null if {@linkplain #getCraftingLockedReason()} is not {@link LockCraftingMode#LOCK_UNTIL_RESULT}.
     */
    @Nullable
    public GenericStack getUnlockStack() {
        return unlockStack;
    }

    public void onStackReturnedToNetwork(GenericStack stack) {
        if (unlockEvent != UnlockCraftingEvent.RESULT) {
            return; // If we're not waiting for the result, we don't care
        }

        if (unlockStack == null) {
            // Actually an error state...
            AELog.error("MEInterface was waiting for RESULT, but no result was set");
            unlockEvent = null;
        } else if (stack != null && unlockStack.what().equals(stack.what())) {
            var remainingAmount = unlockStack.amount() - stack.amount();
            if (remainingAmount <= 0) {
                unlockEvent = null;
                unlockStack = null;
            } else {
                unlockStack = new GenericStack(unlockStack.what(), remainingAmount);
            }
        }
    }

    private boolean getRedstoneState() {
        if (redstoneState == YesNo.UNDECIDED) {
            var entity = this.iHost.getTileEntity();
            redstoneState = entity.getWorld().getRedstonePowerFromNeighbors(entity.getPos()) > 0
                    ? YesNo.YES
                    : YesNo.NO;
        }
        return redstoneState == YesNo.YES;
    }

    private class InterfaceRequestSource extends MachineSource {
        private final InterfaceRequestContext context;

        public InterfaceRequestSource(IActionHost v) {
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
            return Integer.compare(DualityInterface.this.priority, o);
        }
    }


    private class InterfaceInventory extends MEMonitorIInventory {

        public InterfaceInventory(final DualityInterface tileInterface) {
            super(new AdaptorItemHandler(tileInterface.storage));
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
            final boolean hasLowerOrEqualPriority = context.map(c -> c.compareTo(DualityInterface.this.priority) <= 0).orElse(false);

            if (hasLowerOrEqualPriority) {
                return 0;
            }

            return super.extract(what, amount, mode, src);
        }
    }


    private class Accessor implements IStorageMonitorableAccessor {

        @Nullable
        @Override
        public MEStorage getInventory(IActionSource src) {
            return DualityInterface.this.getMonitorable(src, DualityInterface.this);
        }

    }

}
