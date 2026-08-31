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

package appeng.items.tools.powered.powersink;


import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.core.localization.Tooltips;
import appeng.api.upgrades.CardTraits;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellUpgrades;
import appeng.util.Platform;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;


public abstract class AEBasePoweredItem extends AEBaseItem implements IAEItemPowerStorage {
    private static final String CURRENT_POWER_NBT_KEY = "internalCurrentPower";
    private static final String MAX_POWER_NBT_KEY = "internalMaxPower";
    private final double powerCapacity;

    public AEBasePoweredItem(final double powerCapacity) {
        this.setMaxStackSize(1);
        this.setMaxDamage(32);
        this.hasSubtypes = false;
        this.setFull3D();

        this.powerCapacity = powerCapacity;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines, final ITooltipFlag advancedTooltips) {
        final NBTTagCompound tag = stack.getTagCompound();
        double internalCurrentPower = 0;
        final double internalMaxPower = this.getAEMaxPower(stack);

        if (tag != null) {
            internalCurrentPower = tag.getDouble(CURRENT_POWER_NBT_KEY);
        }

        lines.add(
                Tooltips.energyStorageComponent(internalCurrentPower, internalMaxPower).getFormattedText());
    }

    @Override
    public boolean isDamageable() {
        return true;
    }

    @Override
    protected void getCheckedSubItems(final CreativeTabs creativeTab, final NonNullList<ItemStack> itemStacks) {
        super.getCheckedSubItems(creativeTab, itemStacks);

        final ItemStack charged = new ItemStack(this, 1);
        final NBTTagCompound tag = Platform.openNbtData(charged);
        tag.setDouble(CURRENT_POWER_NBT_KEY, this.getAEMaxPower(charged));
        tag.setDouble(MAX_POWER_NBT_KEY, this.getAEMaxPower(charged));

        itemStacks.add(charged);
    }

    @Override
    public boolean isRepairable() {
        return false;
    }

    @Override
    public double getDurabilityForDisplay(final ItemStack is) {
        return 1 - this.getAECurrentPower(is) / this.getAEMaxPower(is);
    }

    @Override
    public boolean isDamaged(final ItemStack stack) {
        return true;
    }

    @Override
    public void setDamage(final ItemStack stack, final int damage) {

    }

    @Override
    public double injectAEPower(final ItemStack is, final double amount, Actionable mode) {
        final double maxStorage = this.getAEMaxPower(is);
        final double currentStorage = this.getAECurrentPower(is);
        final double required = maxStorage - currentStorage;
        final double overflow = amount - required;

        if (mode == Actionable.MODULATE) {
            final NBTTagCompound data = Platform.openNbtData(is);
            final double toAdd = Math.min(amount, required);

            data.setDouble(CURRENT_POWER_NBT_KEY, currentStorage + toAdd);
        }

        return Math.max(0, overflow);
    }

    @Override
    public double extractAEPower(final ItemStack is, final double amount, Actionable mode) {
        final double currentStorage = this.getAECurrentPower(is);
        final double fulfillable = Math.min(amount, currentStorage);

        if (mode == Actionable.MODULATE) {
            final NBTTagCompound data = Platform.openNbtData(is);

            data.setDouble(CURRENT_POWER_NBT_KEY, currentStorage - fulfillable);
        }

        return fulfillable;
    }

    @Override
    public double getAEMaxPower(final ItemStack is) {
        final NBTTagCompound tag = is.getTagCompound();

        return tag != null && tag.hasKey(MAX_POWER_NBT_KEY) ? tag.getDouble(MAX_POWER_NBT_KEY)
                : this.powerCapacity;
    }

    /**
     * Sets what this one stack can hold, for a tool whose battery grows with the cards in it. Written to the
     * stack rather than worked out on demand, because the durability bar asks for the maximum on every frame
     * and reading an upgrade inventory means deserialising one.
     *
     * <p>Charge above the new maximum is lost: pulling a card out of a full tool leaves the tool full.</p>
     */
    protected final void setAEMaxPower(final ItemStack is, final double maxPower) {
        final NBTTagCompound data = Platform.openNbtData(is);

        if (maxPower == this.powerCapacity) {
            data.removeTag(MAX_POWER_NBT_KEY);
        } else {
            data.setDouble(MAX_POWER_NBT_KEY, maxPower);
        }

        if (data.getDouble(CURRENT_POWER_NBT_KEY) > maxPower) {
            data.setDouble(CURRENT_POWER_NBT_KEY, maxPower);
        }
    }

    /**
     * @param multiplier how many times the tool's default battery it now holds
     */
    protected final void setAEMaxPowerMultiplier(final ItemStack is, final int multiplier) {
        this.setAEMaxPower(is, multiplier * this.powerCapacity);
    }

    /**
     * An upgrade inventory that resizes this stack's battery as energy cards come and go.
     *
     * @param perCard how many times the tool's own battery one card is worth. Eight for a tool built
     *                around a plain energy cell, because the card is crafted with a dense one.
     */
    protected final IItemHandler upgradesWithEnergyCards(final ItemStack is, final int slots,
            final int perCard) {
        return new CellUpgrades(is, slots, upgrades -> this.setAEMaxPowerMultiplier(is,
                1 + upgrades.getInstalledPoints(CardTraits.ENERGY) * perCard));
    }

    @Override
    public double getAECurrentPower(final ItemStack is) {
        final NBTTagCompound data = Platform.openNbtData(is);

        return data.getDouble(CURRENT_POWER_NBT_KEY);
    }

    @Override
    public AccessRestriction getPowerFlow(final ItemStack is) {
        return AccessRestriction.WRITE;
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new PoweredItemCapabilities(stack, this);
    }
}
