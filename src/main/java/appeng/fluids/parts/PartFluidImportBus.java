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


import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackWorldBehaviors;
import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.core.AppEng;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.util.prioritylist.IPartitionList;


/**
 * Moves the bus' configured fluids from the adjacent tank into the network. Fluid counterpart of
 * {@code appeng.parts.automation.PartImportBus}: routes through the same
 * {@link appeng.api.behaviors.StackImportStrategy} layer (here, {@link FluidImportStrategy}, obtained through the
 * public registry exactly like an addon would), rather than talking to the adjacent {@code IFluidHandler}
 * directly the way the pre-port class did.
 * <p/>
 * {@code Settings.SCHEDULING_MODE} is registered here for GUI parity with {@code PartFluidExportBus}, but -- as in
 * the pre-port class -- has no effect on import: the bus doesn't pick one configured slot to try each tick, it
 * just tests the incoming fluid against the whole configured filter set at once.
 */
public class PartFluidImportBus extends PartSharedFluidBus {
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID, "part/fluid_import_bus_base");
    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/fluid_import_bus_off"));
    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/fluid_import_bus_on"));
    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/fluid_import_bus_has_channel"));

    private final IActionSource source;

    private StackImportStrategy importStrategy;

    public PartFluidImportBus(ItemStack is) {
        super(is);
        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.CRAFT_ONLY, YesNo.NO);
        this.getConfigManager().registerSetting(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);
        this.source = new MachineSource(this);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.FluidImportBus.getMin(), TickRates.FluidImportBus.getMax(), this.isSleeping(), false);
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

        if (this.importStrategy == null) {
            final var self = this.getHost().getTile();
            final var fromPos = self.getPos().offset(this.getSide().getFacing());
            final var fromSide = this.getSide().getFacing().getOpposite();
            this.importStrategy = StackWorldBehaviors
                    .createImportStrategies(self.getWorld(), fromPos, fromSide, type -> type == AEKeyType.fluids())
                    .stream().findFirst().orElse(null);
        }
        if (this.importStrategy == null) {
            // No fluid import strategy registered at all -- nothing this bus can do.
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

        final IPartitionList filter = this.buildFilter();
        final FuzzyMode fzMode = this.getInstalledUpgrades(Upgrades.FUZZY) > 0
                ? (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE)
                : null;

        final FluidTransferContext context = new FluidTransferContext(internalStorage, energy, this.source,
                (int) this.calculateAmountToSend(), filter, fzMode);

        final boolean worked = this.importStrategy.transfer(context);

        return worked ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    /**
     * Builds the partition list from the bus' configured slots, matching the pre-port behaviour where an
     * unconfigured bus imports anything and a configured one only imports the listed fluids (fuzzy or precise
     * depending on {@code Upgrades.FUZZY}).
     */
    private IPartitionList buildFilter() {
        final var builder = IPartitionList.builder();
        if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            builder.fuzzyMode((FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE));
        }
        for (int x = 0; x < this.getConfig().getSlots(); x++) {
            final var stack = this.getConfig().getFluidInSlot(x);
            if (stack != null) {
                builder.add(stack.what());
            }
        }
        return builder.build();
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
