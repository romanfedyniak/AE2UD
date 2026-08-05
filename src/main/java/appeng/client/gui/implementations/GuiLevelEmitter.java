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
import appeng.client.gui.AmountEntry;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiNumberBox;
import appeng.container.implementations.ContainerLevelEmitter;
import appeng.container.slot.SlotFakeTypeOnly;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.parts.automation.PartLevelEmitter;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class GuiLevelEmitter extends GuiUpgradeable {

    /**
     * Prefix a type's base unit carries when it is measured in a larger one - millibuckets.
     */
    private static final String BASE_UNIT_PREFIX = "m";

    private static final int FIELD_X = 24;
    private static final int FIELD_Y = 43;
    private static final int FIELD_WIDTH = 79;

    private GuiNumberBox level;

    /** Last threshold seen from the server, and the last one this screen sent, so echoes are ignored. */
    private long lastSynced = Long.MIN_VALUE;
    private long lastSent = Long.MIN_VALUE;

    /** The scale the field is currently showing, so a change to it can be re-rendered exactly once. */
    private int shownScale = 1;

    private GuiButton unitToggle;

    private GuiButton plus1;
    private GuiButton plus10;
    private GuiButton plus100;
    private GuiButton plus1000;
    private GuiButton minus1;
    private GuiButton minus10;
    private GuiButton minus100;
    private GuiButton minus1000;

    private GuiImgButton levelMode;
    private GuiImgButton craftingMode;

    public GuiLevelEmitter(final InventoryPlayer inventoryPlayer, final PartLevelEmitter te) {
        super(new ContainerLevelEmitter(inventoryPlayer, te));
    }

    @Override
    public void initGui() {
        // Without this a held digit or backspace fires once.
        Keyboard.enableRepeatEvents(true);

        super.initGui();

        // Double, not Long: in unit entry the field legitimately holds a decimal point.
        this.level = new GuiNumberBox(this.fontRenderer, this.guiLeft + FIELD_X, this.guiTop + FIELD_Y, FIELD_WIDTH, this.fontRenderer.FONT_HEIGHT, Double.class);
        this.level.setEnableBackgroundDrawing(false);
        this.level.setMaxStringLength(16);
        this.level.setTextColor(0xFFFFFF);
        this.level.setVisible(true);
        this.level.setFocused(true);
    }

    @Override
    protected void addButtons() {
        this.levelMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.LEVEL_TYPE, LevelType.ITEM_LEVEL);
        this.redstoneMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.REDSTONE_EMITTER, RedstoneMode.LOW_SIGNAL);
        this.fuzzyMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 48, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.craftingMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 48, Settings.CRAFT_VIA_REDSTONE, YesNo.NO);

        final int a = AEConfig.instance().levelByStackAmounts(0);
        final int b = AEConfig.instance().levelByStackAmounts(1);
        final int c = AEConfig.instance().levelByStackAmounts(2);
        final int d = AEConfig.instance().levelByStackAmounts(3);

        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 17, 22, 20, "+" + a));
        this.buttonList.add(this.plus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 17, 28, 20, "+" + b));
        this.buttonList.add(this.plus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 17, 32, 20, "+" + c));
        this.buttonList.add(this.plus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 17, 38, 20, "+" + d));

        this.buttonList.add(this.minus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 59, 22, 20, "-" + a));
        this.buttonList.add(this.minus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 59, 28, 20, "-" + b));
        this.buttonList.add(this.minus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 59, 32, 20, "-" + c));
        this.buttonList.add(this.minus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 59, 38, 20, "-" + d));

        this.buttonList.add(this.levelMode);
        this.buttonList.add(this.redstoneMode);
        this.buttonList.add(this.fuzzyMode);
        this.buttonList.add(this.craftingMode);

        // Sits below the other side controls, where there is room for a label rather than an icon. Starts
        // hidden: drawFG decides, and it runs after the buttons are drawn, so the first frame is blank.
        this.buttonList.add(this.unitToggle = new GuiButton(0, this.guiLeft - 24, this.guiTop + 68, 22, 20, ""));
        this.unitToggle.visible = false;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final boolean notCraftingMode = this.bc.getInstalledUpgrades(UpgradeCards.crafting()) == 0;

        // configure enabled status...
        this.level.setEnabled(notCraftingMode);
        this.plus1.enabled = notCraftingMode;
        this.plus10.enabled = notCraftingMode;
        this.plus100.enabled = notCraftingMode;
        this.plus1000.enabled = notCraftingMode;
        this.minus1.enabled = notCraftingMode;
        this.minus10.enabled = notCraftingMode;
        this.minus100.enabled = notCraftingMode;
        this.minus1000.enabled = notCraftingMode;
        this.levelMode.enabled = notCraftingMode;
        this.redstoneMode.enabled = notCraftingMode;

        final AEKey what = this.getFilteredKey();
        final int available = AmountEntry.unitOf(what);
        this.unitToggle.visible = available > 1;
        this.unitToggle.enabled = available > 1 && notCraftingMode;
        if (this.unitToggle.visible) {
            final String symbol = what.getUnitSymbol();
            this.unitToggle.displayString = AmountEntry.unitsEnabled() ? symbol : BASE_UNIT_PREFIX + symbol;
        }

        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        if (this.craftingMode != null) {
            this.craftingMode.set(((ContainerLevelEmitter) this.cvb).getCraftingMode());
        }

        if (this.levelMode != null) {
            this.levelMode.set(((ContainerLevelEmitter) this.cvb).getLevelMode());
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);

        this.syncField();

        final String symbol = AmountEntry.symbol(this.getFilteredKey(), this.unitScale());
        this.level.width = FIELD_WIDTH - AmountEntry.reservedWidth(this.fontRenderer, symbol);
        this.level.drawTextBox();
        AmountEntry.drawSymbol(this.fontRenderer, this.level, symbol);
    }

    /**
     * Keeps the field in step with the threshold without fighting whoever is typing into it.
     * <p>
     * Every keystroke is sent to the server, which echoes the value straight back; re-rendering on that
     * echo would wipe a half-typed "1." before its decimals could be entered. Only a value this screen
     * did not send is written into the field.
     */
    private void syncField() {
        final int scale = this.unitScale();
        if (scale != this.shownScale) {
            // The filter arrived, or the unit was toggled. Re-read in the old scale, write in the new.
            final long amount = AmountEntry.parse(this.level.getText(), this.shownScale);
            this.shownScale = scale;
            this.level.setText(AmountEntry.format(amount, scale));
        }

        final long synced = ((ContainerLevelEmitter) this.cvb).EmitterValue;
        if (synced != this.lastSynced) {
            this.lastSynced = synced;
            if (synced != this.lastSent) {
                this.level.setText(AmountEntry.format(synced, scale));
            }
        }
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> exclusionArea = new ArrayList<>(super.getJEIExclusionArea());
        if (this.unitToggle.visible) {
            exclusionArea.add(new Rectangle(this.unitToggle.x - 1, this.unitToggle.y - 1,
                    this.unitToggle.width + 2, this.unitToggle.height + 2));
        }
        return exclusionArea;
    }

    @Override
    protected void handleButtonVisibility() {
        this.craftingMode.setVisibility(this.bc.getInstalledUpgrades(UpgradeCards.crafting()) > 0);
        this.fuzzyMode.setVisibility(this.bc.getInstalledUpgrades(UpgradeCards.fuzzy()) > 0);
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

        if (btn == this.unitToggle) {
            // Read in the old scale, write back in the new one, so the threshold itself does not move.
            final long amount = AmountEntry.parse(this.level.getText(), this.unitScale());
            AmountEntry.toggleUnits();
            this.shownScale = this.unitScale();
            this.level.setText(AmountEntry.format(amount, this.shownScale));
            return;
        }

        final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
        final boolean isMinus = btn == this.minus1 || btn == this.minus10 || btn == this.minus100 || btn == this.minus1000;

        if (isPlus || isMinus) {
            this.addQty((long) this.getQty(btn) * this.unitScale());
        }
    }

    private void addQty(final long i) {
        final int scale = this.unitScale();

        long result = AmountEntry.parse(this.level.getText(), scale) + i;
        if (result < 0) {
            result = 0;
        }

        this.level.setText(AmountEntry.format(result, scale));
        this.sendLevel(result);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (this.checkHotbarKeys(key)) {
            return;
        }

        final boolean accepted = key == 211 || key == 205 || key == 203 || key == 14
                || Character.isDigit(character) || this.acceptsPoint(character);

        if (accepted && this.level.textboxKeyTyped(character, key)) {
            this.sendLevel(AmountEntry.parse(this.level.getText(), this.unitScale()));
        } else {
            super.keyTyped(character, key);
        }
    }

    /** A decimal point belongs in the field only while it reads in units, and only one of them. */
    private boolean acceptsPoint(final char character) {
        return character == '.' && this.unitScale() > 1 && !this.level.getText().contains(".");
    }

    /**
     * Sends the threshold in the base unit, whatever unit the field happens to be showing. The value is
     * remembered so {@link #syncField()} can tell the server's echo apart from a real change.
     */
    private void sendLevel(final long value) {
        this.lastSent = value;

        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("LevelEmitter.Value", Long.toString(value)));
        } catch (final IOException e) {
            AELog.debug(e);
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

    private int unitScale() {
        return AmountEntry.scaleOf(this.getFilteredKey());
    }
}
