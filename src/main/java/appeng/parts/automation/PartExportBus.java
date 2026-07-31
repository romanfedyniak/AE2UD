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

package appeng.parts.automation;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackWorldBehaviors;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.RedstoneMode;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.settings.TickRates;
import appeng.core.sync.GuiBridge;
import appeng.helpers.MultiCraftingTracker;
import appeng.helpers.Reflected;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.parts.PartModel;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.prioritylist.IPartitionList;


/**
 * Moves the bus' configured keys from the network into the adjacent block. Like {@link PartImportBus},
 * this class has no item-specific logic of its own -- {@link StackExportStrategy}s registered per
 * {@code AEKeyType} (see {@link InitStackWorldBehaviors}) do the actual work of pushing content into
 * the adjacent block.
 * <p>
 * The one exception is craft-on-demand: {@link MultiCraftingTracker#handleCrafting} still needs an
 * {@link InventoryAdaptor} to pre-check whether the destination can even accept the crafted result
 * before a job is started (see CONTRACT.md &sect;9, wave 1b's frozen shape for {@code appeng.helpers}).
 * Autocrafting itself is still entirely item/{@link GenericStack}-based in this codebase (a
 * deliberately out-of-scope alignment per CONTRACT.md &sect;4.4), so this is not a regression this
 * wave introduced.
 */
public class PartExportBus extends PartSharedItemBus implements ICraftingRequester {
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID, "part/export_bus_base");

    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/export_bus_off"));

    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/export_bus_on"));

    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/export_bus_has_channel"));

    private final MultiCraftingTracker craftingTracker = new MultiCraftingTracker(this, 63);
    private final IActionSource mySrc;
    private int nextSlot = 0;
    private boolean didSomething = false;

    private StackExportStrategy exportStrategy;

    @Reflected
    public PartExportBus(final ItemStack is) {
        super(is);

        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.CRAFT_ONLY, YesNo.NO);
        this.getConfigManager().registerSetting(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);
        this.mySrc = new MachineSource(this);
    }

    @Override
    public void readFromNBT(final NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.craftingTracker.readFromNBT(extra);
        this.nextSlot = extra.getInteger("nextSlot");
    }

    @Override
    public void writeToNBT(final NBTTagCompound extra) {
        super.writeToNBT(extra);
        this.craftingTracker.writeToNBT(extra);
        extra.setInteger("nextSlot", this.nextSlot);
    }

    private StackExportStrategy getExportStrategy() {
        if (this.exportStrategy == null) {
            final var self = this.getHost().getTile();
            final var fromPos = self.getPos().offset(this.getSide().getFacing());
            final var fromSide = this.getSide().getFacing().getOpposite();
            this.exportStrategy = new StackExportFacade(
                    StackWorldBehaviors.createExportStrategies(self.getWorld(), fromPos, fromSide));
        }
        return this.exportStrategy;
    }

    @Override
    protected TickRateModulation doBusWork() {
        if (!this.getProxy().isActive() || !this.canDoBusWork()) {
            return TickRateModulation.IDLE;
        }

        this.didSomething = false;

        final MEStorage internalStorage;
        final IStorageService storageService;
        final IEnergySource energy;
        final ICraftingGrid cg;
        try {
            storageService = this.getProxy().getStorage();
            internalStorage = storageService.getInventory();
            energy = this.getProxy().getEnergy();
            cg = this.getProxy().getCrafting();
        } catch (final GridAccessException e) {
            return TickRateModulation.IDLE;
        }

        final FuzzyMode fzMode = (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE);
        final SchedulingMode schedulingMode = (SchedulingMode) this.getConfigManager().getSetting(Settings.SCHEDULING_MODE);

        final StackTransferContextImpl context = new StackTransferContextImpl(internalStorage, energy, this.mySrc,
                this.calculateItemsToSend(), IPartitionList.builder().build(), null);

        int x;
        for (x = 0; x < this.availableSlots() && context.hasOperationsLeft(); x++) {
            final int slotToExport = this.getStartingSlot(schedulingMode, x);

            final var configuredStack = this.getConfig().getAEStackInSlot(slotToExport);
            if (configuredStack == null) {
                continue;
            }
            final AEKey what = configuredStack.what();

            if (this.craftOnly()) {
                this.didSomething = this.attemptCrafting(context, cg, slotToExport, what) || this.didSomething;
                continue;
            }

            final int before = context.getOperationsRemaining();

            if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
                for (var fuzzyEntry : ImmutableList.copyOf(storageService.getCachedInventory().findFuzzy(what, fzMode))) {
                    if (fuzzyEntry.getLongValue() > 0) {
                        this.exportOne(context, fuzzyEntry.getKey());
                        if (!context.hasOperationsLeft()) {
                            break;
                        }
                    }
                }
            } else {
                this.exportOne(context, what);
            }

            if (context.getOperationsRemaining() == before && this.isCraftingEnabled()) {
                this.didSomething = this.attemptCrafting(context, cg, slotToExport, what) || this.didSomething;
            }
        }

        this.updateSchedulingMode(schedulingMode, x);

        return this.didSomething || context.hasDoneWork() ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    private void exportOne(StackTransferContextImpl context, AEKey what) {
        final long transferFactor = Math.max(1, what.getAmountPerOperation());
        final long maxAmount = (long) context.getOperationsRemaining() * transferFactor;
        final long moved = this.getExportStrategy().transfer(context, what, maxAmount);
        if (moved > 0) {
            context.reduceOperationsRemaining(Math.max(1, moved / transferFactor));
            this.didSomething = true;
        }
    }

    private boolean attemptCrafting(StackTransferContextImpl context, ICraftingGrid cg, int slotToExport, AEKey what) {
        final InventoryAdaptor destination = this.getHandler();
        if (destination == null) {
            return false;
        }
        try {
            return this.craftingTracker.handleCrafting(slotToExport, context.getOperationsRemaining(), what,
                    destination, this.getTile().getWorld(), this.getProxy().getGrid(), cg, this.mySrc);
        } catch (final GridAccessException e) {
            return false;
        }
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(4, 4, 12, 12, 12, 14);
        bch.addBox(5, 5, 14, 11, 11, 15);
        bch.addBox(6, 6, 15, 10, 10, 16);
        bch.addBox(6, 6, 11, 10, 10, 12);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 5;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isServer()) {
            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_BUS);
        }
        return true;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.ExportBus.getMin(), TickRates.ExportBus.getMax(), this.isSleeping(), false);
    }

    @Override
    public RedstoneMode getRSMode() {
        return (RedstoneMode) this.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        return this.doBusWork();
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.craftingTracker.getRequestedJobs();
    }

    @Override
    public GenericStack injectCraftedItems(final ICraftingLink link, final GenericStack items, final Actionable mode) {
        try {
            if (this.getProxy().isActive()) {
                final IEnergyGrid energy = this.getProxy().getEnergy();
                final double power = items.amount();

                if (energy.extractAEPower(power, mode, PowerMultiplier.CONFIG) > power - 0.01) {
                    final long inserted = this.getExportStrategy().push(items.what(), items.amount(), mode);
                    final long remaining = items.amount() - inserted;

                    if (remaining <= 0) {
                        return null;
                    }
                    return new GenericStack(items.what(), remaining);
                }
            }
        } catch (final GridAccessException e) {
            AELog.debug(e);
        }

        return items;
    }

    @Override
    public void jobStateChange(final ICraftingLink link) {
        this.craftingTracker.jobStateChange(link);
    }

    private boolean craftOnly() {
        return this.getConfigManager().getSetting(Settings.CRAFT_ONLY) == YesNo.YES;
    }

    private boolean isCraftingEnabled() {
        return this.getInstalledUpgrades(Upgrades.CRAFTING) > 0;
    }

    private int getStartingSlot(final SchedulingMode schedulingMode, final int x) {
        if (schedulingMode == SchedulingMode.RANDOM) {
            return Platform.getRandom().nextInt(this.availableSlots());
        }

        if (schedulingMode == SchedulingMode.ROUNDROBIN) {
            return (this.nextSlot + x) % this.availableSlots();
        }

        return x;
    }

    private void updateSchedulingMode(final SchedulingMode schedulingMode, final int x) {
        if (schedulingMode == SchedulingMode.ROUNDROBIN) {
            this.nextSlot = (this.nextSlot + x) % this.availableSlots();
        }
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

}
