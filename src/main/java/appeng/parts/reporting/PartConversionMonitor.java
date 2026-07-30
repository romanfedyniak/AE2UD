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

package appeng.parts.reporting;


import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.helpers.Reflected;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.me.helpers.PlayerSource;
import appeng.parts.PartModel;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import java.util.Collections;
import java.util.List;


public class PartConversionMonitor extends AbstractPartMonitor {

    @PartModels
    public static final ResourceLocation MODEL_OFF = new ResourceLocation(AppEng.MOD_ID, "part/conversion_monitor_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = new ResourceLocation(AppEng.MOD_ID, "part/conversion_monitor_on");
    @PartModels
    public static final ResourceLocation MODEL_LOCKED_OFF = new ResourceLocation(AppEng.MOD_ID, "part/conversion_monitor_locked_off");
    @PartModels
    public static final ResourceLocation MODEL_LOCKED_ON = new ResourceLocation(AppEng.MOD_ID, "part/conversion_monitor_locked_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);
    public static final IPartModel MODELS_LOCKED_OFF = new PartModel(MODEL_BASE, MODEL_LOCKED_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_LOCKED_ON = new PartModel(MODEL_BASE, MODEL_LOCKED_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_LOCKED_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_LOCKED_ON, MODEL_STATUS_HAS_CHANNEL);

    @Reflected
    public PartConversionMonitor(final ItemStack is) {
        super(is);
    }

    @Override
    public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!Platform.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        final ItemStack eq = player.getHeldItem(hand);
        FluidStack fluidInTank = null;
        if (eq.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            IFluidHandlerItem fluidHandlerItem = (eq.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null));
            fluidInTank = fluidHandlerItem.drain(Integer.MAX_VALUE, false);
        }

        final AEKey configuredKey = this.getConfiguredKey();

        if (this.isLocked()) {
            if (eq.isEmpty()) {
                this.insertItem(player, hand, true);
            } else if (Platform.isWrench(player, eq, this.getLocation().getPos()) && !(configuredKey instanceof AEItemKey itemKey && itemKey.matches(eq))) {
                // wrench it
                return super.onPartActivate(player, hand, pos);
            } else if (fluidInTank != null && fluidInTank.amount > 0) {
                if (configuredKey instanceof AEFluidKey fluidKey && fluidKey.matches(fluidInTank)) {
                    this.drainFluidContainer(player, hand);
                }
            } else {
                this.insertItem(player, hand, false);
            }
        }

        //If its a fluid container, grab its fluidstack. if its empty pass its itemstack;

        if (fluidInTank != null && fluidInTank.amount > 0) {
            if (configuredKey == null || configuredKey instanceof AEItemKey) {
                return super.onPartActivate(player, hand, pos);
            }
            if (configuredKey instanceof AEFluidKey fluidKey && fluidKey.matches(fluidInTank)) {
                this.drainFluidContainer(player, hand);
            } else {
                return super.onPartActivate(player, hand, pos);
            }
        } else if (configuredKey instanceof AEItemKey itemKey && itemKey.matches(player.getHeldItem(hand))) {
            this.insertItem(player, hand, false);
        }
        return super.onPartActivate(player, hand, pos);
    }

    @Override
    public boolean onClicked(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!Platform.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        final AEKey configuredKey = this.getConfiguredKey();
        if (configuredKey instanceof AEItemKey itemKey) {
            this.extractItem(player, itemKey.getMaxStackSize());
        } else if (configuredKey instanceof AEFluidKey) {
            this.fillFluidContainer(player, hand);
        }

        return true;
    }

    @Override
    public boolean onShiftClicked(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!Platform.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        if (this.getConfiguredKey() != null) {
            this.extractItem(player, 1);
        }

        return true;
    }

    private void insertItem(final EntityPlayer player, final EnumHand hand, final boolean allItems) {
        try {
            final IEnergySource energy = this.getProxy().getEnergy();
            final MEStorage cell = this.getProxy().getStorage().getInventory();

            if (allItems) {
                // "Grab everything matching the configured item from the player's inventory" - only makes sense
                // while an item (not a fluid) is configured.
                if (this.getConfiguredKey() instanceof AEItemKey configuredItemKey) {
                    final IItemHandler inv = new PlayerMainInvWrapper(player.inventory);

                    for (int x = 0; x < inv.getSlots(); x++) {
                        final ItemStack targetStack = inv.getStackInSlot(x);
                        if (configuredItemKey.matches(targetStack)) {
                            final ItemStack canExtract = inv.extractItem(x, targetStack.getCount(), true);
                            if (!canExtract.isEmpty()) {
                                final long inserted = Platform.poweredInsert(energy, cell, configuredItemKey, canExtract.getCount(), new PlayerSource(player, this));
                                if (inserted > 0) {
                                    inv.extractItem(x, (int) inserted, false);
                                }
                            }
                        }
                    }
                }
            } else {
                // Quick-insert of whatever is actually in hand - independent of what the monitor is configured to
                // display, exactly like the old code's unconditional AEItemStack.fromItemStack(heldItem) path.
                final ItemStack held = player.getHeldItem(hand);
                final AEItemKey heldKey = AEItemKey.of(held);
                if (heldKey != null) {
                    final long inserted = Platform.poweredInsert(energy, cell, heldKey, held.getCount(), new PlayerSource(player, this));
                    final ItemStack remainder = inserted >= held.getCount() ? ItemStack.EMPTY : heldKey.toStack((int) (held.getCount() - inserted));
                    player.setHeldItem(hand, remainder);
                }
            }
        } catch (final GridAccessException e) {
            // :P
        }
    }

    private void extractItem(final EntityPlayer player, int count) {
        if (!(this.getConfiguredKey() instanceof AEItemKey configuredItemKey)) {
            return;
        }

        try {
            if (!this.getProxy().isActive()) {
                return;
            }

            final IEnergySource energy = this.getProxy().getEnergy();
            final MEStorage cell = this.getProxy().getStorage().getInventory();

            final long extracted = Platform.poweredExtraction(energy, cell, configuredItemKey, count, new PlayerSource(player, this));
            if (extracted > 0) {
                ItemStack newItems = configuredItemKey.toStack((int) extracted);
                final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player);
                newItems = adaptor.addItems(newItems);
                if (!newItems.isEmpty()) {
                    final TileEntity te = this.getTile();
                    final List<ItemStack> list = Collections.singletonList(newItems);
                    Platform.spawnDrops(player.world, te.getPos().offset(this.getSide().getFacing()), list);
                }

                if (player.openContainer != null) {
                    player.openContainer.detectAndSendChanges();
                }
            }
        } catch (final GridAccessException e) {
            // :P
        }
    }

    private void drainFluidContainer(final EntityPlayer player, final EnumHand hand) {
        try {
            final ItemStack held = player.getHeldItem(hand);
            if (held.getCount() != 1) {
                // only support stacksize 1 for now
                return;
            }

            final IFluidHandlerItem fh = FluidUtil.getFluidHandler(held);
            if (fh == null) {
                // only fluid handlers items
                return;
            }

            // See how much we can drain from the item
            final FluidStack extract = fh.drain(Integer.MAX_VALUE, false);
            if (extract == null || extract.amount < 1) {
                return;
            }

            // Check if we can push into the system
            final IEnergySource energy = this.getProxy().getEnergy();
            final MEStorage cell = this.getProxy().getStorage().getInventory();
            final long canInsert = Platform.poweredInsert(energy, cell, AEFluidKey.of(extract), extract.amount, new PlayerSource(player, this), Actionable.SIMULATE);

            if (canInsert < extract.amount) {
                final int toStore = (int) canInsert;
                final FluidStack storable = fh.drain(toStore, false);

                if (storable == null || storable.amount == 0) {
                    return;
                } else {
                    extract.amount = storable.amount;
                }
            }

            // Actually drain
            final FluidStack drained = fh.drain(extract, true);
            extract.amount = drained.amount;

            final long inserted = Platform.poweredInsert(energy, cell, AEFluidKey.of(extract), extract.amount, new PlayerSource(player, this));

            if (inserted < extract.amount) {
                AELog.error("Fluid item [%s] reported a different possible amount to drain than it actually provided.", held.getDisplayName());
            }

            player.setHeldItem(hand, fh.getContainer());
        } catch (GridAccessException e) {
            e.printStackTrace();
        }
    }

    private void fillFluidContainer(final EntityPlayer player, final EnumHand hand) {
        try {
            final ItemStack held = player.getHeldItem(hand);
            if (held.getCount() != 1) {
                // only support stacksize 1 for now
                return;
            }

            final IFluidHandlerItem fh = FluidUtil.getFluidHandler(held);
            if (fh == null) {
                // only fluid handlers items
                return;
            }

            if (!(this.getConfiguredKey() instanceof AEFluidKey configuredFluidKey)) {
                return;
            }

            // Check how much we can store in the item
            final int amountAllowed = fh.fill(configuredFluidKey.toStack(Integer.MAX_VALUE), false);
            if (amountAllowed <= 0) {
                return;
            }

            // Check if we can pull out of the system
            final IEnergySource energy = this.getProxy().getEnergy();
            final MEStorage cell = this.getProxy().getStorage().getInventory();
            final long canPull = Platform.poweredExtraction(energy, cell, configuredFluidKey, amountAllowed, new PlayerSource(player, this), Actionable.SIMULATE);
            if (canPull < 1) {
                return;
            }

            // How much could fit into the container
            final int canFill = fh.fill(configuredFluidKey.toStack((int) canPull), false);
            if (canFill == 0) {
                return;
            }

            // Now actually pull out of the system
            final long pulled = Platform.poweredExtraction(energy, cell, configuredFluidKey, canFill, new PlayerSource(player, this));
            if (pulled < 1) {
                // Something went wrong
                AELog.error("Unable to pull fluid out of the ME system even though the simulation said yes ");
                return;
            }

            // Actually fill
            final int used = fh.fill(configuredFluidKey.toStack((int) pulled), true);

            if (used != pulled) {
                AELog.error("Fluid item [%s] reported a different possible amount than it actually accepted.", held.getDisplayName());
            }
            player.setHeldItem(hand, fh.getContainer());
        } catch (GridAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL,
                MODELS_LOCKED_OFF, MODELS_LOCKED_ON, MODELS_LOCKED_HAS_CHANNEL);
    }

}
