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


import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackWorldBehaviors;
import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.core.AppEng;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.util.Platform;
import appeng.util.prioritylist.IPartitionList;


/**
 * Moves the bus' configured fluids from the network into the adjacent tank. Fluid counterpart of
 * {@code appeng.parts.automation.PartExportBus}, routed through {@link FluidExportStrategy}.
 * <p/>
 * {@code Settings.CRAFT_ONLY} is registered here for GUI parity with the pre-port class, but -- as in the
 * pre-port class -- has no craft-on-demand implementation behind it: this bus never implemented
 * {@code ICraftingRequester}/{@code MultiCraftingTracker}, unlike the item export bus. See this wave's report for
 * why that gap was not closed here (it would be new functionality, not a preserved mechanic).
 * <p/>
 * {@code Settings.SCHEDULING_MODE} <b>is</b> wired up for real here (round-robin/sequential/random slot order),
 * matching the item export bus -- the pre-port fluid export bus stopped at the first configured slot that
 * accepted anything each tick, which this preserves; scheduling mode only changes which slot is tried first.
 */
public class PartFluidExportBus extends PartSharedFluidBus {
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID, "part/fluid_export_bus_base");
    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/fluid_export_bus_off"));
    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/fluid_export_bus_on"));
    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/fluid_export_bus_has_channel"));

    private final IActionSource source;
    private int nextSlot = 0;

    private List<StackExportStrategy> exportStrategies;

    public PartFluidExportBus(ItemStack is) {
        super(is);
        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.CRAFT_ONLY, YesNo.NO);
        this.getConfigManager().registerSetting(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);
        this.source = new MachineSource(this);
    }

    @Override
    public void readFromNBT(final NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.nextSlot = extra.getInteger("nextSlot");
    }

    @Override
    public void writeToNBT(final NBTTagCompound extra) {
        super.writeToNBT(extra);
        extra.setInteger("nextSlot", this.nextSlot);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.FluidExportBus.getMin(), TickRates.FluidExportBus.getMax(), this.isSleeping(), false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        return this.doBusWork();
    }

    @Override
    protected boolean canDoBusWork() {
        return this.getProxy().isActive();
    }

    @Override
    protected TickRateModulation doBusWork() {
        if (!this.canDoBusWork()) {
            return TickRateModulation.IDLE;
        }

        final MEStorage internalStorage;
        final IEnergySource energy;
        try {
            internalStorage = this.getProxy().getStorage().getInventory();
            energy = this.getProxy().getEnergy();
        } catch (final GridAccessException e) {
            return TickRateModulation.IDLE;
        }

        final SchedulingMode schedulingMode = (SchedulingMode) this.getConfigManager().getSetting(Settings.SCHEDULING_MODE);
        final FluidTransferContext context = new FluidTransferContext(internalStorage, energy, this.source,
                Integer.MAX_VALUE, IPartitionList.builder().build(), null);

        final int slots = this.getConfig().getSlots();
        boolean didSomething = false;
        int x;
        for (x = 0; x < slots; x++) {
            final int slotToExport = this.getStartingSlot(schedulingMode, x, slots);

            final var configuredStack = this.getConfig().getFluidInSlot(slotToExport);
            if (configuredStack == null) {
                continue;
            }
            final AEKey what = configuredStack.what();

            final long moved = this.transferOne(context, what, this.calculateAmountToSend());
            if (moved > 0) {
                didSomething = true;
                // The pre-port bus stopped at the first slot that accepted anything each tick; preserved here.
                break;
            }
        }

        this.updateSchedulingMode(schedulingMode, x, slots);

        return didSomething ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    private long transferOne(FluidTransferContext context, AEKey what, long maxAmount) {
        this.ensureExportStrategies();
        for (StackExportStrategy strategy : this.exportStrategies) {
            final long moved = strategy.transfer(context, what, maxAmount);
            if (moved > 0) {
                return moved;
            }
        }
        return 0;
    }

    private void ensureExportStrategies() {
        if (this.exportStrategies == null) {
            final var self = this.getHost().getTile();
            final var fromPos = self.getPos().offset(this.getSide().getFacing());
            final var fromSide = this.getSide().getFacing().getOpposite();
            // No type filter exists on this frozen API call (CONTRACT.md §3.1), so this returns every
            // registered export strategy (items included). That's harmless here: this bus only ever calls
            // transfer()/push() with an AEFluidKey "what", and every non-fluid strategy already guards its own
            // key type internally and returns 0 for anything else, exactly like StorageExportFacade's dispatch.
            this.exportStrategies = StackWorldBehaviors.createExportStrategies(self.getWorld(), fromPos, fromSide);
        }
    }

    private int getStartingSlot(final SchedulingMode schedulingMode, final int x, final int slots) {
        if (schedulingMode == SchedulingMode.RANDOM) {
            return Platform.getRandom().nextInt(slots);
        }

        if (schedulingMode == SchedulingMode.ROUNDROBIN) {
            return (this.nextSlot + x) % slots;
        }

        return x;
    }

    private void updateSchedulingMode(final SchedulingMode schedulingMode, final int x, final int slots) {
        if (schedulingMode == SchedulingMode.ROUNDROBIN) {
            this.nextSlot = (this.nextSlot + x) % slots;
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
    public RedstoneMode getRSMode() {
        return (RedstoneMode) this.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED);
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
}
