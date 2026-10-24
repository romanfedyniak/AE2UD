/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's pattern multi-tool (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.patternmultiplier;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.upgrades.CardTraits;
import appeng.helpers.IInterfaceHost;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.inv.filter.IAEItemFilter;

/**
 * What a pattern multi-tool carries, kept on its stack under the names NAE2 used, so that one from a world it
 * was made in reads the same: {@code inv}, {@code upgrades}, {@code srInv} and {@code tab}.
 */
public class PatternMultiplierHost implements IGuiItemObject, IAEAppEngInventory {

    public static final int ROWS = 4;
    public static final int COLUMNS = 9;
    public static final int CARD_SLOTS = 3;

    private final ItemStack stack;
    @Nullable
    private final IInterfaceHost target;
    private final AppEngInternalInventory patterns;
    private final AppEngInternalInventory searchReplace;
    private final UpgradeInventory upgrades;
    private PatternMultiplierTab tab = PatternMultiplierTab.MULTIPLY;

    public PatternMultiplierHost(final ItemStack stack, @Nullable final IInterfaceHost target) {
        this.stack = stack;
        this.target = target;
        this.patterns = new AppEngInternalInventory(this, ROWS * COLUMNS, 64, new PatternsOnly());
        this.searchReplace = new AppEngInternalInventory(this, 2, 1);
        this.upgrades = new StackUpgradeInventory(stack, this, CARD_SLOTS);

        final NBTTagCompound data = stack.getTagCompound();
        if (data != null) {
            this.patterns.readFromNBT(data, "inv");
            this.upgrades.readFromNBT(data, "upgrades");
            this.searchReplace.readFromNBT(data, "srInv");
            this.tab = PatternMultiplierTab.of(data.getInteger("tab"));
        }
    }

    @Override
    public ItemStack getItemStack() {
        return this.stack;
    }

    /**
     * The interface it was used on, or null when it was opened on its own.
     */
    @Nullable
    public IInterfaceHost getTarget() {
        return this.target;
    }

    public IItemHandler getPatterns() {
        return this.patterns;
    }

    public IItemHandler getSearchReplace() {
        return this.searchReplace;
    }

    public UpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    /**
     * One row of patterns, and one more for each capacity card.
     */
    public int getRows() {
        return Math.min(ROWS, 1 + this.upgrades.getInstalledPoints(CardTraits.CAPACITY));
    }

    public PatternMultiplierTab getTab() {
        return this.tab;
    }

    public void setTab(final PatternMultiplierTab tab) {
        this.tab = tab;
        this.saveChanges();
    }

    @Override
    public void saveChanges() {
        final NBTTagCompound data = Platform.openNbtData(this.stack);
        this.patterns.writeToNBT(data, "inv");
        this.upgrades.writeToNBT(data, "upgrades");
        this.searchReplace.writeToNBT(data, "srInv");
        data.setInteger("tab", this.tab.ordinal());
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation operation,
            final ItemStack removed, final ItemStack added) {
    }

    private static final class PatternsOnly implements IAEItemFilter {
        @Override
        public boolean allowExtract(final IItemHandler inv, final int slot, final int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(final IItemHandler inv, final int slot, final ItemStack stack) {
            return stack.getItem() instanceof ICraftingPatternItem;
        }
    }
}
