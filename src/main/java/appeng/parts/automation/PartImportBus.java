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


import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackWorldBehaviors;
import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.upgrades.UpgradeCards;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.core.AppEng;
import appeng.helpers.ISubMenuHost;
import appeng.core.settings.TickRates;
import appeng.core.sync.GuiBridge;
import appeng.helpers.Reflected;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.parts.PartModel;
import appeng.util.Platform;
import appeng.util.prioritylist.IPartitionList;


/**
 * Moves the bus' configured keys from the adjacent block into the network. Unlike the pre-port
 * version (which talked to the adjacent inventory as {@code ItemStack}s directly), this class knows
 * nothing about items: it just builds a {@link appeng.api.behaviors.StackTransferContext} once per
 * tick and hands it to whatever {@link StackImportStrategy}s are registered for the configured key
 * types (see {@link InitStackWorldBehaviors}). A future addon registering a new {@code AEKeyType}
 * with its own import strategy gets a working import bus for free.
 */
public class PartImportBus extends PartSharedItemBus implements KeyTypeSelectionHost, ISubMenuHost {

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID, "part/import_bus_base");
    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/import_bus_off"));
    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/import_bus_on"));
    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(AppEng.MOD_ID, "part/import_bus_has_channel"));

    private final IActionSource source;
    private final KeyTypeSelection keyTypeSelection;

    private StackImportStrategy importStrategy;

    @Reflected
    public PartImportBus(final ItemStack is) {
        super(is);

        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.source = new MachineSource(this);
        this.keyTypeSelection = new KeyTypeSelection(() -> {
            this.getHost().markForSave();
            // Which strategies exist is decided once and cached, so it has to be rebuilt.
            this.importStrategy = null;
        }, StackWorldBehaviors.hasImportStrategyTypeFilter());
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return this.keyTypeSelection;
    }

    @Override
    public GuiBridge getGuiBridge() {
        return GuiBridge.GUI_BUS;
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return this.getItemStack();
    }

    @Override
    public void readFromNBT(final NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.keyTypeSelection.readFromNBT(extra);
    }

    @Override
    public void writeToNBT(final NBTTagCompound extra) {
        super.writeToNBT(extra);
        this.keyTypeSelection.writeToNBT(extra);
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(6, 6, 11, 10, 10, 13);
        bch.addBox(5, 5, 13, 11, 11, 14);
        bch.addBox(4, 4, 14, 12, 12, 16);
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
        return new TickingRequest(TickRates.ImportBus.getMin(), TickRates.ImportBus.getMax(), this.isSleeping(), false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        return this.doBusWork();
    }

    @Override
    protected TickRateModulation doBusWork() {
        if (!this.getProxy().isActive() || !this.canDoBusWork()) {
            return TickRateModulation.IDLE;
        }

        if (this.importStrategy == null) {
            final var self = this.getHost().getTile();
            final var fromPos = self.getPos().offset(this.getSide().getFacing());
            final var fromSide = this.getSide().getFacing().getOpposite();
            this.importStrategy = new StackImportFacade(
                    StackWorldBehaviors.createImportStrategies(self.getWorld(), fromPos, fromSide,
                            this.keyTypeSelection.enabledPredicate()));
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
        final FuzzyMode fzMode = this.getInstalledUpgrades(UpgradeCards.fuzzy()) > 0
                ? (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE)
                : null;

        final StackTransferContextImpl context = new StackTransferContextImpl(internalStorage, energy, this.source,
                this.calculateItemsToSend(), filter, fzMode);
        context.setInverted(this.getInstalledUpgrades(UpgradeCards.inverter()) > 0);

        this.importStrategy.transfer(context);

        return context.hasDoneWork() ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    /**
     * Builds the partition list from the bus' configured slots, matching the pre-port behaviour where
     * an unconfigured bus imports anything and a configured one imports either the listed keys or their
     * complement when an inverter card is installed. Matching is fuzzy or precise depending
     * on {@code UpgradeCards.fuzzy()}.
     */
    private IPartitionList buildFilter() {
        final var builder = IPartitionList.builder();
        if (this.getInstalledUpgrades(UpgradeCards.fuzzy()) > 0) {
            builder.fuzzyMode((FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE));
        }
        for (int x = 0; x < this.availableSlots(); x++) {
            final var stack = this.getConfig().getAEStackInSlot(x);
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
