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

package appeng.container.implementations;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.IItemHandler;

import appeng.api.parts.IPart;
import appeng.api.upgrades.CardTraits;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotRestrictedInput;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.patternmultiplier.PatternEdits;
import appeng.helpers.patternmultiplier.PatternMultiplierHost;
import appeng.helpers.patternmultiplier.PatternMultipliers;
import appeng.helpers.patternmultiplier.PatternMultiplierTab;

/**
 * The pattern multi-tool's own screen. Used on an interface, it shows that interface's patterns too, and the
 * player switches between the two; both sets of slots stand in the same place and only the shown one is enabled.
 */
public class ContainerPatternMultiplier extends AEBaseContainer {

    public static final String ACTION = "PatternMultiplier.Action";
    public static final String TAB = "PatternMultiplier.Tab";
    public static final String VIEW = "PatternMultiplier.View";

    public static final int PATTERNS_X = 8;
    /** How much taller the window's header is than the texture's, so the tabs on its edge clear the slots. */
    public static final int HEADER_EXTRA = 6;
    public static final int PATTERNS_Y = 19 + HEADER_EXTRA;
    public static final int CARDS_X = 187;
    public static final int CARDS_Y = 8;
    public static final int SEARCH_X = 8;
    public static final int REPLACE_X = 54;
    public static final int SEARCH_Y = 95 + HEADER_EXTRA;

    private final PatternMultiplierHost host;
    @Nullable
    private final IInterfaceHost target;

    @GuiSync(0)
    public boolean viewingInterface;

    @GuiSync(1)
    public int tab;

    public ContainerPatternMultiplier(final InventoryPlayer ip, final PatternMultiplierHost host) {
        super(ip, tileOf(host.getTarget()), host.getTarget() instanceof IPart ? (IPart) host.getTarget() : null);
        this.host = host;
        this.target = host.getTarget();
        this.viewingInterface = this.target != null;
        this.tab = host.getTab().ordinal();

        if (ip.getCurrentItem() == host.getItemStack()) {
            this.lockPlayerInventorySlot(ip.currentItem);
        }

        final IOptionalSlotHost toolRows = row -> !this.viewingInterface && row < host.getRows();
        this.addPatternSlots(host.getPatterns(), toolRows, 64);
        for (int i = 0; i < PatternMultiplierHost.CARD_SLOTS; i++) {
            this.addSlotToContainer(new ToolCardSlot(host.getUpgrades(), i).setNotDraggable());
        }

        if (this.target != null) {
            final DualityInterface duality = this.target.getInterfaceDuality();
            final IOptionalSlotHost interfaceRows = row -> this.viewingInterface
                    && duality.getInstalledPoints(CardTraits.PATTERN_EXPANSION) >= row;
            this.addPatternSlots(duality.getPatterns(), interfaceRows, 1);
            final IItemHandler cards = this.target.getInventoryByName("upgrades");
            for (int i = 0; i < cards.getSlots(); i++) {
                this.addSlotToContainer(new InterfaceCardSlot(cards, i, duality).setNotDraggable());
            }
        }

        this.addSlotToContainer(new SearchSlot(host.getSearchReplace(), 0, SEARCH_X));
        this.addSlotToContainer(new SearchSlot(host.getSearchReplace(), 1, REPLACE_X));

        this.bindPlayerInventory(ip, 0, 125 + HEADER_EXTRA);
    }

    /**
     * Opened in the air there is nothing to stand near; on an interface, the tile it is in.
     */
    @Nullable
    private static TileEntity tileOf(@Nullable final IInterfaceHost target) {
        return target == null || target instanceof IPart ? null : target.getTileEntity();
    }

    private void addPatternSlots(final IItemHandler patterns, final IOptionalSlotHost rows, final int limit) {
        for (int row = 0; row < PatternMultiplierHost.ROWS; row++) {
            for (int column = 0; column < PatternMultiplierHost.COLUMNS; column++) {
                this.addSlotToContainer(new OptionalSlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN, patterns, rows,
                        row * PatternMultiplierHost.COLUMNS + column, PATTERNS_X + column * 18,
                        PATTERNS_Y + row * 18, row, this.getInventoryPlayer()).setStackLimit(limit));
            }
        }
    }

    public PatternMultiplierHost getHost() {
        return this.host;
    }

    public boolean hasTarget() {
        return this.target != null;
    }

    @Nullable
    public IInterfaceHost getTarget() {
        return this.target;
    }

    public PatternMultiplierTab getTab() {
        return PatternMultiplierTab.of(this.tab);
    }

    /**
     * The patterns shown, the tool's own or the interface's: one row, and one for each card that allows one.
     */
    public IItemHandler getShownPatterns() {
        return this.viewingInterface && this.target != null ? this.target.getInterfaceDuality().getPatterns()
                : this.host.getPatterns();
    }

    public int getShownRows() {
        if (this.viewingInterface && this.target != null) {
            return Math.min(PatternMultiplierHost.ROWS,
                    1 + this.target.getInterfaceDuality().getInstalledPoints(CardTraits.PATTERN_EXPANSION));
        }
        return this.host.getRows();
    }

    public void switchTab(final PatternMultiplierTab tab) {
        this.host.setTab(tab);
        this.tab = tab.ordinal();
        this.detectAndSendChanges();
    }

    public void switchView() {
        this.viewingInterface = this.target != null && !this.viewingInterface;
        this.detectAndSendChanges();
    }

    /**
     * One of the buttons, on every pattern shown.
     */
    public void act(final String action) {
        PatternEdits.applyAll(this.getShownPatterns(), this.getShownRows() * PatternMultiplierHost.COLUMNS, action,
                this.host.getSearchReplace().getStackInSlot(0), this.host.getSearchReplace().getStackInSlot(1),
                this.getPlayerInv().player.world);
        this.host.saveChanges();
        this.detectAndSendChanges();
    }

    @Override
    public void detectAndSendChanges() {
        if (!this.isStillCarried()) {
            this.setValidContainer(false);
        } else if (this.target != null) {
            final TileEntity tile = this.target.getTileEntity();
            if (tile == null || tile.isInvalid() || tile.getWorld().getTileEntity(tile.getPos()) != tile) {
                this.setValidContainer(false);
            }
        }
        super.detectAndSendChanges();
    }

    /**
     * The tool must still be where it was opened from: the hand, another slot or a bauble slot.
     */
    private boolean isStillCarried() {
        final InventoryPlayer inventory = this.getPlayerInv();
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (inventory.getStackInSlot(i) == this.host.getItemStack()) {
                return true;
            }
        }
        return PatternMultipliers.inBaubles(inventory.player, this.host.getItemStack());
    }

    @Override
    public void onSlotChange(final Slot slot) {
        this.host.saveChanges();
        super.onSlotChange(slot);
    }

    /** The tool's own card slots, shown while its patterns are. */
    private final class ToolCardSlot extends SlotRestrictedInput {
        private ToolCardSlot(final IItemHandler cards, final int index) {
            super(PlacableItemType.UPGRADES, cards, index, CARDS_X, CARDS_Y + index * 18,
                    ContainerPatternMultiplier.this.getInventoryPlayer());
        }

        @Override
        public boolean isSlotEnabled() {
            return !ContainerPatternMultiplier.this.viewingInterface;
        }

        /** A capacity card does not come out from under the row of patterns it opens. */
        @Override
        public boolean canTakeStack(final EntityPlayer player) {
            if (!super.canTakeStack(player)) {
                return false;
            }
            final PatternMultiplierHost host = ContainerPatternMultiplier.this.host;
            final int rows = Math.min(PatternMultiplierHost.ROWS,
                    1 + host.getUpgrades().getInstalledPointsWithout(CardTraits.CAPACITY, this.getSlotIndex()));
            final IItemHandler patterns = host.getPatterns();
            for (int slot = rows * PatternMultiplierHost.COLUMNS; slot < patterns.getSlots(); slot++) {
                if (!patterns.getStackInSlot(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }

    /** The interface's card slots; a pattern expansion card does not come out from under its patterns. */
    private final class InterfaceCardSlot extends SlotRestrictedInput {
        private final DualityInterface duality;

        private InterfaceCardSlot(final IItemHandler cards, final int index, final DualityInterface duality) {
            super(PlacableItemType.UPGRADES, cards, index, CARDS_X, CARDS_Y + index * 18,
                    ContainerPatternMultiplier.this.getInventoryPlayer());
            this.duality = duality;
        }

        @Override
        public boolean isSlotEnabled() {
            return ContainerPatternMultiplier.this.viewingInterface;
        }

        @Override
        public boolean canTakeStack(final EntityPlayer player) {
            return super.canTakeStack(player) && this.duality.canRemoveUpgrade(this.getSlotIndex());
        }
    }

    /** What to find and what to put in its place, shown on the replace page. */
    private final class SearchSlot extends SlotFake {
        private SearchSlot(final IItemHandler inventory, final int index, final int x) {
            super(inventory, index, x, SEARCH_Y);
        }

        @Override
        public boolean isSlotEnabled() {
            return ContainerPatternMultiplier.this.getTab() == PatternMultiplierTab.REPLACE;
        }
    }
}
