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

package appeng.tile.misc;

import com.google.common.math.IntMath;


import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.InscriberInputCapacity;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.definitions.IComparableDefinition;
import appeng.api.definitions.ITileDefinition;
import appeng.api.features.IInscriberRecipe;
import appeng.api.features.IInscriberRecipeBuilder;
import appeng.api.features.InscriberProcessType;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.parts.automation.DefinitionUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.UpgradeSpeedCalculations;
import appeng.util.inv.InvOperation;
import appeng.util.inv.WrapperChainedItemHandler;
import appeng.util.inv.WrapperFilteredItemHandler;
import appeng.util.inv.filter.IAEItemFilter;
import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;


/**
 * @author AlgorithmX2
 * @author thatsIch
 * @version rv2
 * @since rv0
 */
public class TileInscriber extends AENetworkPowerTile implements IGridTickable, IUpgradeableHost, IConfigManagerHost {
    private final int maxProcessingTime = 100;

    private final IConfigManager settings;
    private final UpgradeInventory upgrades;
    private int processingTime = 0;
    // cycles from 0 - 16, at 8 it preforms the action, at 16 it re-enables the normal routine.
    private boolean smash;
    private int finalStep;
    private long clientStart;
    private final AppEngInternalInventory topItemHandler = new AppEngInternalInventory(this, 1, 64);
    private final AppEngInternalInventory bottomItemHandler = new AppEngInternalInventory(this, 1, 64);
    private final AppEngInternalInventory sideItemHandler = new AppEngInternalInventory(this, 2, 64);

    private final IItemHandler topItemHandlerExtern;
    private final IItemHandler bottomItemHandlerExtern;
    private final IItemHandler sideItemHandlerExtern;
    private final IItemHandler combinedItemHandlerExtern;

    private IInscriberRecipe cachedTask = null;

    private final IItemHandlerModifiable inv = new WrapperChainedItemHandler(this.topItemHandler, this.bottomItemHandler, this.sideItemHandler);

    public TileInscriber() {
        this.getProxy().setValidSides(EnumSet.noneOf(EnumFacing.class));
        this.setInternalMaxPower(1600);
        this.getProxy().setIdlePowerUsage(0);
        this.settings = new ConfigManager(this);
        this.settings.registerSetting(Settings.INSCRIBER_SEPARATE_SIDES, YesNo.NO);
        this.settings.registerSetting(Settings.AUTO_EXPORT, YesNo.NO);
        this.settings.registerSetting(Settings.INSCRIBER_INPUT_CAPACITY, InscriberInputCapacity.SIXTY_FOUR);

        final ITileDefinition inscriberDefinition = AEApi.instance().definitions().blocks().inscriber();
        this.upgrades = new DefinitionUpgradeInventory(inscriberDefinition, this, this.getUpgradeSlots());

        this.applyInputCapacity();

        final IAEItemFilter filter = new ItemHandlerFilter();
        this.topItemHandlerExtern = new WrapperFilteredItemHandler(this.topItemHandler, filter);
        this.bottomItemHandlerExtern = new WrapperFilteredItemHandler(this.bottomItemHandler, filter);
        this.sideItemHandlerExtern = new WrapperFilteredItemHandler(this.sideItemHandler, filter);
        this.combinedItemHandlerExtern = new WrapperChainedItemHandler(this.topItemHandlerExtern, this.bottomItemHandlerExtern, this.sideItemHandlerExtern);
    }

    private boolean isSeparateSides() {
        return this.settings.getSetting(Settings.INSCRIBER_SEPARATE_SIDES) == YesNo.YES;
    }

    private boolean isAutoExport() {
        return this.settings.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    /**
     * The output keeps the full stack it was built with - only what goes in is capped.
     */
    private void applyInputCapacity() {
        final int capacity = ((InscriberInputCapacity) this.settings.getSetting(Settings.INSCRIBER_INPUT_CAPACITY)).capacity;

        this.topItemHandler.setMaxStackSize(0, capacity);
        this.bottomItemHandler.setMaxStackSize(0, capacity);
        this.sideItemHandler.setMaxStackSize(0, capacity);
    }

    private int getUpgradeSlots() {
        return 3;
    }

    @Override
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.COVERED;
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.upgrades.writeToNBT(data, "upgrades");
        this.settings.writeToNBT(data);
        return data;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.upgrades.readFromNBT(data, "upgrades");
        this.settings.readFromNBT(data);
    }

    @Override
    protected boolean readFromStream(final ByteBuf data) throws IOException {
        final boolean c = super.readFromStream(data);
        final int slot = data.readByte();

        final boolean oldSmash = this.isSmash();
        final boolean newSmash = (slot & 64) == 64;

        if (oldSmash != newSmash && newSmash) {
            this.setSmash(true);
            this.setClientStart(System.currentTimeMillis());
        }

        for (int num = 0; num < this.inv.getSlots(); num++) {
            if ((slot & (1 << num)) > 0) {
                final GenericStack stack = GenericStack.readBuffer(data);
                final ItemStack is = stack != null && stack.what() instanceof AEItemKey itemKey
                        ? itemKey.toStack((int) Math.min(Integer.MAX_VALUE, stack.amount()))
                        : ItemStack.EMPTY;
                this.inv.setStackInSlot(num, is);
            } else {
                this.inv.setStackInSlot(num, ItemStack.EMPTY);
            }
        }
        this.cachedTask = null;

        return c;
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
        int slot = this.isSmash() ? 64 : 0;

        for (int num = 0; num < this.inv.getSlots(); num++) {
            if (!this.inv.getStackInSlot(num).isEmpty()) {
                slot |= (1 << num);
            }
        }

        data.writeByte(slot);
        for (int num = 0; num < this.inv.getSlots(); num++) {
            if ((slot & (1 << num)) > 0) {
                GenericStack.writeBuffer(GenericStack.fromItemStack(this.inv.getStackInSlot(num)), data);
            }
        }
    }

    @Override
    public void setOrientation(final EnumFacing inForward, final EnumFacing inUp) {
        super.setOrientation(inForward, inUp);
        this.getProxy().setValidSides(EnumSet.complementOf(EnumSet.of(this.getForward())));
        this.setPowerSides(EnumSet.complementOf(EnumSet.of(this.getForward())));
    }

    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        super.getDrops(w, pos, drops);

        for (int h = 0; h < this.upgrades.getSlots(); h++) {
            final ItemStack is = this.upgrades.getStackInSlot(h);
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    @Override
    public boolean requiresTESR() {
        return true;
    }

    @Override
    public IItemHandler getInternalInventory() {
        return this.inv;
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc, final ItemStack removed, final ItemStack added) {
        try {
            // Only a different item throws away progress. A change in count alone is the buffer being
            // topped up while the machine works, which InvOperation reports as INSERT or EXTRACT.
            if (slot == 0 && mc == InvOperation.SET) {
                this.setProcessingTime(0);
            }

            if (!this.isSmash()) {
                this.markForUpdate();
            }

            this.cachedTask = null;
            this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    //
    // @Override
    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.Inscriber.getMin(), TickRates.Inscriber.getMax(),
                !this.hasCraftWork() && !this.hasAutoExportWork(), false);
    }

    private boolean hasCraftWork() {
        final IInscriberRecipe task = this.getTask();
        if (task != null) {
            // Only work while the result would fit. A buffered input would otherwise keep the machine
            // grinding with nowhere to put what it makes.
            return this.sideItemHandler.insertItem(1, task.getOutput().copy(), true).isEmpty();
        }

        this.setProcessingTime(0);
        return this.isSmash();
    }

    private boolean hasAutoExportWork() {
        return this.isAutoExport() && !this.sideItemHandler.getStackInSlot(1).isEmpty();
    }

    @Nullable
    public IInscriberRecipe getTask() {
        if (this.cachedTask == null) {
            this.cachedTask = this.getTask(this.sideItemHandler.getStackInSlot(0), this.topItemHandler.getStackInSlot(0),
                    this.bottomItemHandler.getStackInSlot(0));
        }
        return this.cachedTask;
    }

    @Nullable
    private IInscriberRecipe getTask(final ItemStack input, final ItemStack plateA, final ItemStack plateB) {
        if (input.isEmpty()) {
            return null;
        }

        final IComparableDefinition namePress = AEApi.instance().definitions().materials().namePress();
        final boolean isNameA = namePress.isSameAs(plateA);
        final boolean isNameB = namePress.isSameAs(plateB);

        if ((isNameA && isNameB) || isNameA && plateB.isEmpty()) {
            return this.makeNamePressRecipe(input, plateA, plateB);
        } else if (plateA.isEmpty() && isNameB) {
            return this.makeNamePressRecipe(input, plateB, plateA);
        }

        for (final IInscriberRecipe recipe : AEApi.instance().registries().inscriber().getRecipes()) {

            // Check if plateA matches any item in the list of top components of the recipe
            final boolean matchA = plateA.isEmpty() && recipe.getTopInputs().isEmpty() ||
                    recipe.getTopInputs().stream().anyMatch(topItem -> Platform.itemComparisons().isSameItem(plateA, topItem)) &&
                            (plateB.isEmpty() && recipe.getBottomInputs().isEmpty() ||
                                    recipe.getBottomInputs().stream().anyMatch(bottomItem -> Platform.itemComparisons().isSameItem(plateB, bottomItem)));

            // Check if plateB matches any item in the list of top components of the recipe
            final boolean matchB = plateB.isEmpty() && recipe.getTopInputs().isEmpty() ||
                    recipe.getTopInputs().stream().anyMatch(topItem -> Platform.itemComparisons().isSameItem(plateB, topItem)) &&
                            (plateA.isEmpty() && recipe.getBottomInputs().isEmpty() ||
                                    recipe.getBottomInputs().stream().anyMatch(bottomItem -> Platform.itemComparisons().isSameItem(plateA, bottomItem)));

            // If either matchA or matchB is true, iterate through the recipe's inputs
            if (matchA || matchB) {
                for (final ItemStack option : recipe.getInputs()) {
                    if (Platform.itemComparisons().isSameItem(input, option)) {
                        return recipe;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (this.isSmash()) {
            this.finalStep++;
            if (this.finalStep == 8) {
                final IInscriberRecipe out = this.getTask();
                if (out != null) {
                    final ItemStack outputCopy = out.getOutput().copy();

                    if (this.sideItemHandler.insertItem(1, outputCopy, false).isEmpty()) {
                        this.setProcessingTime(0);
                        if (out.getProcessType() == InscriberProcessType.PRESS) {
                            this.topItemHandler.extractItem(0, 1, false);
                            this.bottomItemHandler.extractItem(0, 1, false);
                        }
                        this.sideItemHandler.extractItem(0, 1, false);
                    }
                }
                this.saveChanges();
            } else if (this.finalStep == 16) {
                this.finalStep = 0;
                this.setSmash(false);
                this.markForUpdate();
            }
        } else if (this.hasCraftWork()) {
            try {
                final IEnergyGrid eg = this.getProxy().getEnergy();
                IEnergySource src = this;

                // Base 1, increase by 1 for each card
                final int speedFactor = UpgradeSpeedCalculations.linearSpeed(
                        this.upgrades.getInstalledSpeedPoints());
                final int powerConsumption = IntMath.saturatedMultiply(10, speedFactor);
                final double powerThreshold = powerConsumption - 0.01;
                double powerReq = this.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);

                if (powerReq <= powerThreshold) {
                    src = eg;
                    powerReq = eg.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                }

                if (powerReq > powerThreshold) {
                    src.extractAEPower(powerConsumption, Actionable.MODULATE, PowerMultiplier.CONFIG);

                    final int increment = this.getProcessingTime() == 0 ? speedFactor
                            : IntMath.saturatedMultiply(ticksSinceLastCall, speedFactor);
                    this.setProcessingTime(IntMath.saturatedAdd(this.getProcessingTime(), increment));
                }
            } catch (final GridAccessException e) {
                // :P
            }

            if (this.getProcessingTime() > this.getMaxProcessingTime()) {
                this.setProcessingTime(this.getMaxProcessingTime());
                final IInscriberRecipe out = this.getTask();
                if (out != null) {
                    final ItemStack outputCopy = out.getOutput().copy();
                    if (this.sideItemHandler.insertItem(1, outputCopy, true).isEmpty()) {
                        this.setSmash(true);
                        this.finalStep = 0;
                        this.markForUpdate();
                    }
                }
            }
        }

        if (this.pushOutResult()) {
            return TickRateModulation.URGENT;
        }

        return this.hasCraftWork() ? TickRateModulation.URGENT
                : this.hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    /**
     * Hands the finished item to whatever sits against the machine, a face at a time.
     *
     * @return true if anything moved
     */
    private boolean pushOutResult() {
        if (!this.hasAutoExportWork()) {
            return false;
        }

        final EnumSet<EnumFacing> pushSides = EnumSet.allOf(EnumFacing.class);
        if (this.isSeparateSides()) {
            // Those two faces belong to the plates, and the result is not theirs to hand out.
            pushSides.remove(this.getUp());
            pushSides.remove(this.getUp().getOpposite());
        }

        for (final EnumFacing dir : pushSides) {
            final TileEntity neighbour = this.world.getTileEntity(this.pos.offset(dir));
            if (neighbour == null) {
                continue;
            }

            final InventoryAdaptor target = InventoryAdaptor.getAdaptor(neighbour, dir.getOpposite());
            if (target == null) {
                continue;
            }

            // Asked before anything is taken out: an extraction that comes straight back is still an
            // inventory change, and one per face per tick would have the machine telling the whole client
            // about itself for nothing.
            final ItemStack result = this.sideItemHandler.getStackInSlot(1);
            final ItemStack refused = target.simulateAdd(result.copy());
            final int movable = result.getCount() - (refused.isEmpty() ? 0 : refused.getCount());

            if (movable <= 0) {
                continue;
            }

            final ItemStack leftOver = target.addItems(this.sideItemHandler.extractItem(1, movable, false));
            if (!leftOver.isEmpty()) {
                this.sideItemHandler.insertItem(1, leftOver, false);
            }

            return true;
        }

        return false;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.settings;
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        if (name.equals("inv")) {
            return this.getInternalInventory();
        }

        if (name.equals("upgrades")) {
            return this.upgrades;
        }

        return null;
    }

    @Override
    protected IItemHandler getItemHandlerForSide(@Nonnull EnumFacing facing) {
        if (!this.isSeparateSides()) {
            return this.combinedItemHandlerExtern;
        }

        if (facing == this.getUp()) {
            return this.topItemHandlerExtern;
        } else if (facing == this.getUp().getOpposite()) {
            return this.bottomItemHandlerExtern;
        } else {
            return this.sideItemHandlerExtern;
        }
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

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (settingName == Settings.INSCRIBER_INPUT_CAPACITY) {
            this.applyInputCapacity();
        }

        if (settingName == Settings.AUTO_EXPORT) {
            try {
                this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
            } catch (final GridAccessException e) {
                // :P
            }
        }

        // Which face reaches which slot just changed, so anyone holding our handler has to ask again. Not
        // while the tile is still being read out of the save, where there is nobody to tell and asking the
        // world what block we are would pull a chunk in to answer.
        if (settingName == Settings.INSCRIBER_SEPARATE_SIDES && this.world != null && !this.world.isRemote
                && this.world.isBlockLoaded(this.pos)) {
            this.world.notifyNeighborsOfStateChange(this.pos, this.getBlockType(), false);
        }

        this.saveChanges();
    }

    public long getClientStart() {
        return this.clientStart;
    }

    private void setClientStart(final long clientStart) {
        this.clientStart = clientStart;
    }

    public boolean isSmash() {
        return this.smash;
    }

    public void setSmash(final boolean smash) {
        this.smash = smash;
    }

    public int getMaxProcessingTime() {
        return this.maxProcessingTime;
    }

    public int getProcessingTime() {
        return this.processingTime;
    }

    private void setProcessingTime(final int processingTime) {
        this.processingTime = processingTime;
    }

    private IInscriberRecipe makeNamePressRecipe(ItemStack input, ItemStack plateA, ItemStack plateB) {
        String name = "";

        if (!plateA.isEmpty()) {
            final NBTTagCompound tag = Platform.openNbtData(plateA);
            name += tag.getString("InscribeName");
        }

        if (!plateB.isEmpty()) {
            final NBTTagCompound tag = Platform.openNbtData(plateB);
            name += " " + tag.getString("InscribeName");
        }

        // One at a time, whatever the input slot is holding. The recipe is built from the stack that is
        // in there, and a buffered slot would otherwise have the press hand back sixty-four renamed items
        // for the one it consumed.
        final ItemStack startingItem = input.copy();
        startingItem.setCount(1);
        final ItemStack renamedItem = input.copy();
        renamedItem.setCount(1);
        final NBTTagCompound tag = Platform.openNbtData(renamedItem);

        final NBTTagCompound display = tag.getCompoundTag("display");
        tag.setTag("display", display);

        if (name.length() > 0) {
            display.setString("Name", name);
        } else {
            display.removeTag("Name");
        }

        final List<ItemStack> inputs = Lists.newArrayList(startingItem);
        final InscriberProcessType type = InscriberProcessType.INSCRIBE;

        final IInscriberRecipeBuilder builder = AEApi.instance().registries().inscriber().builder();
        builder.withInputs(inputs).withOutput(renamedItem).withProcessType(type);

        if (!plateA.isEmpty()) {
            builder.withTopOptional(Collections.singletonList(plateA));
        }

        if (!plateB.isEmpty()) {
            builder.withBottomOptional(Collections.singletonList(plateB));
        }

        return builder.build();
    }

    /**
     * This is an item handler that exposes the inscribers inventory while providing simulation capabilities that do not
     * reset the progress if there's already an item in a slot. Previously, the progress of the inscriber was reset when
     * another mod attempted insertion of items when there were already items in the slot.
     */
    private class ItemHandlerFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(IItemHandler inv, int slot, int amount) {
            // The result is always fair game, even mid-smash - it was made before the press came down.
            if (slot == 1) {
                return true;
            }

            if (TileInscriber.this.isSmash()) {
                return false;
            }

            // Plates come back out only where a face of their own reaches them. With every face reaching
            // everything, an export bus pointed at the machine would pull the presses out of it.
            return TileInscriber.this.isSeparateSides()
                    && (inv == TileInscriber.this.topItemHandler || inv == TileInscriber.this.bottomItemHandler);
        }

        @Override
        public boolean allowInsert(IItemHandler inv, int slot, ItemStack stack) {
            // output slot
            if (slot == 1) {
                return false;
            }

            if (TileInscriber.this.isSmash()) {
                return false;
            }

            if (inv == TileInscriber.this.topItemHandler || inv == TileInscriber.this.bottomItemHandler) {
                if (AEApi.instance().definitions().materials().namePress().isSameAs(stack)) {
                    return true;
                }
                for (final ItemStack optionals : AEApi.instance().registries().inscriber().getOptionals()) {
                    if (Platform.itemComparisons().isSameItem(stack, optionals)) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }
    }
}
