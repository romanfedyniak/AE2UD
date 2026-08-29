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

import appeng.api.upgrades.UpgradeCards;


import appeng.api.config.*;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerLevelEmitter;
import appeng.container.slot.SlotFakeTypeOnly;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.parts.automation.PartLevelEmitter;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;


/**
 * What the emitter watches and how it answers. The number it watches for is typed on the amount screen,
 * which every other amount in the mod is typed on too - this screen only shows it and opens that one.
 */
public class GuiLevelEmitter extends GuiUpgradeable {

    /** The window the texture was drawn for, before the threshold moved to a screen of its own. */
    private static final int TEXTURE_HEIGHT = 184;
    /** Where rows are left out: everything the threshold field, its well and its buttons stood in. */
    private static final int TEXTURE_CUT = 39;
    /** How many, which is what is left of the band the field and its eight buttons stood in. */
    private static final int TEXTURE_TRIM = TEXTURE_HEIGHT - ContainerLevelEmitter.HEIGHT;

    /** The window's own width, out of a texture that also carries the upgrade plate and the toolbox. */
    private static final int PANEL_WIDTH = 177;

    private GuiImgButton levelMode;
    private GuiImgButton craftingMode;

    public GuiLevelEmitter(final InventoryPlayer inventoryPlayer, final PartLevelEmitter te) {
        super(new ContainerLevelEmitter(inventoryPlayer, te));

        this.ySize = ContainerLevelEmitter.HEIGHT;
    }

    @Override
    protected void addButtons() {
        this.levelMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.LEVEL_TYPE, LevelType.ITEM_LEVEL);
        this.redstoneMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.REDSTONE_EMITTER, RedstoneMode.LOW_SIGNAL);
        this.fuzzyMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 48, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.craftingMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 48, Settings.CRAFT_VIA_REDSTONE, YesNo.NO);

        this.buttonList.add(this.levelMode);
        this.buttonList.add(this.redstoneMode);
        this.buttonList.add(this.fuzzyMode);
        this.buttonList.add(this.craftingMode);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final boolean notCraftingMode = this.canSetLevel();

        this.levelMode.enabled = notCraftingMode;
        this.redstoneMode.enabled = notCraftingMode;

        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        if (this.craftingMode != null) {
            this.craftingMode.set(((ContainerLevelEmitter) this.cvb).getCraftingMode());
        }

        if (this.levelMode != null) {
            this.levelMode.set(((ContainerLevelEmitter) this.cvb).getLevelMode());
        }

        this.drawThreshold();
    }

    /**
     * The threshold, drawn on the filter the way any slot wears its amount - it is an amount of that filter
     * that the emitter is watching for. An amount of nothing reads as nothing, so an empty slot is left
     * bare; the threshold is still kept, and setting one before the filter is a way round to the same place.
     */
    private void drawThreshold() {
        final long value = ((ContainerLevelEmitter) this.cvb).EmitterValue;
        final AEKey what = this.getFilteredKey();

        if (value < 0 || what == null) {
            return;
        }

        this.stackSizeRenderer.renderAmount(this.fontRenderer, what.formatAmount(value, AmountFormat.SLOT), what,
                ContainerLevelEmitter.FILTER_X, ContainerLevelEmitter.FILTER_Y);
    }

    /**
     * A crafting card makes the emitter answer whether the network is asking for its item, and the
     * threshold governs nothing while one is in.
     */
    private boolean canSetLevel() {
        return this.bc.getInstalledUpgrades(UpgradeCards.crafting()) == 0;
    }

    @Override
    protected boolean allowsTypedAmount(final Slot slot) {
        return this.canSetLevel();
    }

    /**
     * Drawn in two strips with a band left out, so the window can end where its contents do without the
     * texture being redrawn for it.
     */
    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.handleButtonVisibility();

        this.bindTexture(this.getBackground());
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, PANEL_WIDTH, TEXTURE_CUT);
        this.drawTexturedModalRect(offsetX, offsetY + TEXTURE_CUT, 0, TEXTURE_CUT + TEXTURE_TRIM, PANEL_WIDTH,
                TEXTURE_HEIGHT - TEXTURE_CUT - TEXTURE_TRIM);

        drawSlotWell(offsetX + ContainerLevelEmitter.FILTER_X, offsetY + ContainerLevelEmitter.FILTER_Y);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (this.drawUpgrades()) {
            this.drawTexturedModalRect(offsetX + PANEL_WIDTH, offsetY, PANEL_WIDTH, 0, 35,
                    14 + this.cvb.availableUpgrades() * 18);
        }

        if (this.hasToolbox()) {
            this.drawTexturedModalRect(offsetX + 178, offsetY + this.ySize - 90, 178, TEXTURE_HEIGHT - 90, 68, 68);
        }
    }

    @Override
    protected void handleButtonVisibility() {
        final boolean crafting = this.bc.getInstalledUpgrades(UpgradeCards.crafting()) > 0;

        // These two share a slot in the column, and a level emitter takes a fuzzy card and a crafting card
        // at once, so both were drawn one on top of the other. The crafting one wins because the other
        // governs nothing while it is there: isLevelEmitterOn answers with isRequesting and returns before
        // it ever reaches the level comparison that fuzzy matching applies to.
        this.craftingMode.setVisibility(crafting);
        this.fuzzyMode.setVisibility(!crafting && this.bc.getInstalledUpgrades(UpgradeCards.fuzzy()) > 0);
    }

    @Override
    protected String getBackground() {
        return "guis/lvlemitter.png";
    }

    @Override
    protected GuiText getName() {
        return GuiText.LevelEmitter;
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.craftingMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.craftingMode.getSetting(), backwards));
        }

        if (btn == this.levelMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.levelMode.getSetting(), backwards));
        }
    }

    /**
     * The type the emitter is watching, or null when it is watching energy or has no filter yet - in
     * neither case is there a unit to read the threshold in.
     */
    @Nullable
    private AEKey getFilteredKey() {
        if (((ContainerLevelEmitter) this.cvb).getLevelMode() == LevelType.ENERGY_LEVEL) {
            return null;
        }

        for (final Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof SlotFakeTypeOnly) {
                final GenericStack stack = GenericStack.resolveItemStack(slot.getStack());
                return stack == null ? null : stack.what();
            }
        }
        return null;
    }
}
