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

package appeng.client.gui.implementations;


import appeng.api.config.ActionItems;
import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.upgrades.UpgradeCards;
import appeng.api.config.YesNo;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerIOBus;
import appeng.container.slot.SlotFake;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.parts.automation.PartExportBus;
import appeng.parts.automation.PartImportBus;
import appeng.parts.automation.PartSharedItemBus;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * The import and export bus screen.
 *
 * @see ContainerIOBus
 */
public class GuiIOBus extends GuiUpgradeable {

    private static final int SPACING = 20;

    private final List<GuiImgButton> column = new ArrayList<>();

    private GuiImgButton clear;
    private GuiImgButton craftMode;
    private GuiImgButton schedulingMode;
    private GuiImgButton keyTypes;

    public GuiIOBus(final InventoryPlayer inventoryPlayer, final PartSharedItemBus te) {
        super(new ContainerIOBus(inventoryPlayer, te));
        this.ySize = 251;
    }

    @Override
    protected void addButtons() {
        this.clear = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.ACTIONS, ActionItems.CLOSE);
        this.schedulingMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);
        this.redstoneMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.fuzzyMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.craftMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.CRAFT_ONLY, YesNo.NO);

        // This order is the layout: the column is packed top-down from it, skipping whatever is hidden.
        this.column.clear();
        this.column.add(this.clear);
        // Only the import bus has a say here: what an export bus moves is named in its filter.
        if (this.bc instanceof KeyTypeSelectionHost) {
            this.keyTypes = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.ACTIONS,
                    ActionItems.CONFIGURE_IMPORTED_TYPES);
            this.column.add(this.keyTypes);
        }
        this.column.add(this.schedulingMode);
        this.column.add(this.redstoneMode);
        this.column.add(this.fuzzyMode);
        this.column.add(this.craftMode);

        this.buttonList.addAll(this.column);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        final ContainerIOBus container = (ContainerIOBus) this.cvb;

        if (this.craftMode != null) {
            this.craftMode.set(container.getCraftingMode());
        }

        if (this.schedulingMode != null) {
            this.schedulingMode.set(container.getSchedulingMode());
        }
    }

    /**
     * A filter slot says what to move, not how much there is. The amount the network holds is the thing a
     * player actually wants while setting one up, so it goes on the tooltip.
     */
    @Override
    protected void renderToolTip(final ItemStack stack, final int x, final int y) {
        final Slot slot = this.getSlot(x, y);

        if (slot instanceof SlotFake && !stack.isEmpty()) {
            final GenericStack configured = GenericStack.resolveItemStack(slot.getStack());
            final Long amount = configured == null
                    ? null
                    : ((ContainerIOBus) this.cvb).getStoredAmounts().get(slot.getSlotIndex());

            if (amount != null) {
                final ITooltipFlag.TooltipFlags flags = this.mc.gameSettings.advancedItemTooltips
                        ? ITooltipFlag.TooltipFlags.ADVANCED
                        : ITooltipFlag.TooltipFlags.NORMAL;
                // Shift asks for the exact number in the base unit - 1,040mB where the normal reading
                // rounds to 1B. Same rule the terminal uses.
                final AmountFormat format = isShiftKeyDown() ? AmountFormat.FULL_BASE : AmountFormat.FULL;

                final List<String> lines = stack.getTooltip(this.mc.player, flags);
                lines.add(GuiText.Stored.getLocal() + ": " + configured.what().formatAmount(amount, format));

                this.drawTooltip(x, y, lines);
                return;
            }
        }

        super.renderToolTip(stack, x, y);
    }

    @Override
    protected void handleButtonVisibility() {
        super.handleButtonVisibility();

        if (this.craftMode != null) {
            this.craftMode.setVisibility(this.bc.getInstalledUpgrades(UpgradeCards.crafting()) > 0);
        }
        if (this.schedulingMode != null) {
            // No capacity card in the condition: it used to stand in for "this bus has more than one
            // slot", which stopped being true once two rows are free.
            this.schedulingMode.setVisibility(this.bc instanceof PartExportBus);
        }

        this.layoutColumn();
    }

    /**
     * Packs the visible buttons down the left column with no gaps, the way upstream's
     * {@code VerticalButtonBar} does. Runs every frame because a card can appear or leave at any moment.
     */
    private void layoutColumn() {
        int y = this.guiTop + 8;
        for (final GuiImgButton button : this.column) {
            if (!button.isVisible()) {
                continue;
            }
            button.y = y;
            y += SPACING;
        }
    }

    @Override
    protected String getBackground() {
        return "guis/bus.png";
    }

    @Override
    protected GuiText getName() {
        return this.bc instanceof PartImportBus ? GuiText.ImportBus : GuiText.ExportBus;
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.clear) {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("Filter.Clear", ""));
        }

        if (btn == this.keyTypes) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_KEY_TYPES));
        }

        if (btn == this.craftMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.craftMode.getSetting(), backwards));
        }

        if (btn == this.schedulingMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.schedulingMode.getSetting(), backwards));
        }
    }
}
