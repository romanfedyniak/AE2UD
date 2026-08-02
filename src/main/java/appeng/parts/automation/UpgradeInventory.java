/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.automation;

import javax.annotation.Nonnull;

import com.google.common.math.IntMath;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeInventoryListener;
import appeng.api.upgrades.IUpgradeRegistry;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.inv.filter.IAEItemFilter;

public abstract class UpgradeInventory extends AppEngInternalInventory
        implements IAEAppEngInventory, IUpgradeInventory {

    private final IAEAppEngInventory parent;
    private final IUpgradeInventoryListener listener;

    protected UpgradeInventory(final IAEAppEngInventory parent, final int slots) {
        this(parent, slots, null);
    }

    protected UpgradeInventory(final IAEAppEngInventory parent, final int slots,
            final IUpgradeInventoryListener listener) {
        super(null, slots, 1);
        this.setTileEntity(this);
        this.parent = parent;
        this.listener = listener;
        this.setFilter(new UpgradeInvFilter());
    }

    @Override
    protected boolean eventsEnabled() {
        return true;
    }

    @Override
    public int getInstalledUpgrades(final ItemStack upgradeCard) {
        if (upgradeCard.isEmpty()) {
            return 0;
        }

        int installed = 0;
        for (final ItemStack stack : this) {
            if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, upgradeCard)) {
                installed = IntMath.saturatedAdd(installed, stack.getCount());
            }
        }
        return Math.min(installed, this.getMaxInstalled(upgradeCard));
    }

    @Override
    public int getMaxInstalled(final ItemStack upgradeCard) {
        return registry().getMaxInstallable(upgradeCard, this.getUpgradableItem());
    }

    @Override
    public int getInstalledSpeedPoints() {
        int points = 0;
        final IUpgradeRegistry registry = registry();
        for (final ItemStack stack : this) {
            if (!stack.isEmpty() && this.getMaxInstalled(stack) > 0) {
                points = IntMath.saturatedAdd(points,
                        IntMath.saturatedMultiply(registry.getSpeedPoints(stack), stack.getCount()));
            }
        }
        return points;
    }

    @Override
    public int getInstalledCapacityPoints() {
        int points = 0;
        final IUpgradeRegistry registry = registry();
        for (final ItemStack stack : this) {
            if (!stack.isEmpty() && this.getMaxInstalled(stack) > 0) {
                points = IntMath.saturatedAdd(points,
                        IntMath.saturatedMultiply(registry.getCapacityPoints(stack), stack.getCount()));
            }
        }
        return Math.min(points, registry.getCapacityLimit(this.getUpgradableItem()));
    }

    @Override
    public void saveChanges() {
        if (this.parent != null) {
            this.parent.saveChanges();
        }
        if (this.listener != null) {
            this.listener.onUpgradesChanged(this);
        }
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation operation,
            final ItemStack removedStack, final ItemStack newStack) {
        if (this.parent != null && Platform.isServer()) {
            this.parent.onChangeInventory(inv, slot, operation, removedStack, newStack);
        }
    }

    @Override
    public void readFromNBT(final NBTTagCompound target) {
        super.readFromNBT(target);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        return super.extractItem(slot, amount, simulate);
    }

    private static IUpgradeRegistry registry() {
        return AEApi.instance().registries().upgrades();
    }

    private final class UpgradeInvFilter implements IAEItemFilter {

        @Override
        public boolean allowExtract(final IItemHandler inv, final int slot, final int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(final IItemHandler inv, final int slot, final ItemStack stack) {
            if (stack.isEmpty() || stack.getItem() == Items.AIR) {
                return false;
            }

            final IUpgradeRegistry registry = registry();
            if (UpgradeInventory.this.getInstalledUpgrades(stack) >= UpgradeInventory.this.getMaxInstalled(stack)) {
                return false;
            }

            final int capacityPoints = registry.getCapacityPoints(stack);
            final int speedPoints = registry.getSpeedPoints(stack);
            final ItemStack host = UpgradeInventory.this.getUpgradableItem();
            return capacityPoints == 0 || !registry.isCapacityCardSupported(stack, host)
                    || (speedPoints > 0
                            && registry.isSpeedCardSupported(stack, host))
                    || UpgradeInventory.this.getInstalledCapacityPoints()
                            < registry.getCapacityLimit(host);
        }
    }
}
