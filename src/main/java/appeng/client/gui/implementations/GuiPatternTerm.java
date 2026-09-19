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


import appeng.api.AEApi;
import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.integrations.hei.IngredientConverters;
import appeng.api.config.ActionItems;
import appeng.api.config.FluidSubstitution;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.api.patterns.PatternEncodingMode;
import appeng.api.patterns.PatternEncodingModes;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.IKeyUnderMouse;
import appeng.container.me.GridInventoryEntry;
import appeng.core.localization.ButtonToolTips;
import appeng.api.config.PatternSlotConfig;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.AppEng;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerWirelessPatternTerminal;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.ContainerNull;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.helpers.PatternHelper;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketPatternUpload;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiObject;
import mezz.jei.api.gui.IGhostIngredientHandler.Target;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import java.text.NumberFormat;
import java.util.Locale;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;


public class GuiPatternTerm extends GuiMEMonitorable implements IJEIGhostIngredients, IKeyUnderMouse {

    private static final String BACKGROUND_CRAFTING_MODE = "guis/pattern.png";
    private static final String BACKGROUND_PROCESSING_MODE = "guis/pattern3.png";
    private static final String BACKGROUND_PROCESSING_INVERTED_MODE = "guis/pattern4.png";

    private static final String SUBSITUTION_DISABLE = "0";
    private static final String SUBSITUTION_ENABLE = "1";

    private static final int FABRICATED_SLOT_TINT = 0x8032CD32;

    /** Where the mode tab sits, the button below it, and the picker they open. */
    private static final int MODE_TAB_X = 173;
    private static final int MODE_TAB_Y_FROM_BOTTOM = 177;
    private static final int MODE_PICKER_Y_FROM_BOTTOM = 155;
    private static final int PICKER_CELL = 18;
    private static final int PICKER_PADDING = 4;
    private static final int PICKER_HOVER_TINT = 0x80FFFFFF;

    /**
     * The button cluster lives in the gap between the two processing grids, and inverting moves that gap
     * three slots to the left.
     */
    private static final int BUTTONS_LEFT = 88;
    private static final int BUTTONS_INVERTED_SHIFT = -18 * 3;

    private final ContainerPatternEncoder container;

    private final GuiScrollbar pageScrollBar = new GuiScrollbar();
    private GuiImgButton invertBtn;
    private int seenPatternLoads = Integer.MIN_VALUE;

    /**
     * One tab per mode, all in the same place; only the active mode's is shown. The two built-in tabs swap
     * between themselves as they always have; an addon mode's tab opens the picker, which is the way back.
     */
    private final Map<ResourceLocation, GuiTabButton> modeTabs = new LinkedHashMap<>();
    /** Shown only where an addon has registered a mode: the two built-in ones need no list. */
    private GuiTabButton modesBtn;
    private boolean pickerOpen;
    private GuiImgButton substitutionsEnabledBtn;
    private GuiImgButton substitutionsDisabledBtn;
    private GuiImgButton fluidSubstitutionsEnabledBtn;
    private GuiImgButton fluidSubstitutionsDisabledBtn;
    private List<Slot> craftingGrid;
    private GenericStack[] fabricatedSlots;
    private int fabricatedFrom;
    private GuiImgButton encodeBtn;
    private GuiImgButton uploadBtn;
    private GuiImgButton clearBtn;
    private GuiImgButton x2Btn;
    private GuiImgButton x3Btn;
    private GuiImgButton plusOneBtn;
    private GuiImgButton divTwoBtn;
    private GuiImgButton divThreeBtn;
    private GuiImgButton minusOneBtn;
    public Map<Target<?>, Object> mapTargetSlot = new HashMap<>();

    public GuiPatternTerm(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(inventoryPlayer, te, new ContainerPatternTerm(inventoryPlayer, te));
        this.container = (ContainerPatternTerm) this.inventorySlots;
        this.setUpPages();
    }

    public GuiPatternTerm(final InventoryPlayer inventoryPlayer, WirelessTerminalGuiObject te, final ContainerWirelessPatternTerminal wpt) {
        super(inventoryPlayer, te, wpt);
        this.container = (ContainerWirelessPatternTerminal) this.inventorySlots;
        this.setUpPages();
    }

    /**
     * Sends the grid back to its first page. A recipe always fills the grid from the start, so arriving on
     * the second page shows empty slots and reads as a transfer that did nothing.
     */
    public void showFirstPage() {
        this.pageScrollBar.setCurrentScroll(0);
    }

    private void setUpPages() {
        this.setReservedSpace(81);
        this.pageScrollBar.setLeft(6).setWidth(7).setHeight(18 * PatternHelper.PROCESSING_GRID_DIMENSION - 2);
        this.pageScrollBar.setRange(0, PatternHelper.PROCESSING_PAGES - 1, 1);
        this.pageScrollBar.setTexture(AppEng.MOD_ID, BACKGROUND_PROCESSING_MODE, 243, 0);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {

            if (this.modesBtn == btn) {
                this.pickerOpen = !this.pickerOpen;
            }

            if (this.modeTabs.containsValue(btn)) {
                // The two built-in tabs are each other's other half; anything else has a list to go back through.
                if (this.container.isCraftingMode()) {
                    this.switchTo(PatternEncodingModes.PROCESSING);
                } else if (this.isProcessingMode()) {
                    this.switchTo(PatternEncodingModes.CRAFTING);
                } else {
                    this.pickerOpen = !this.pickerOpen;
                }
            }

            if (this.invertBtn == btn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.Invert", this.container.isInverted() ? "0" : "1"));
            }

            if (this.encodeBtn == btn) {
                if (isShiftKeyDown()) {
                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Encode", "2"));
                } else {
                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Encode", "1"));
                }
            }

            if (this.clearBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Clear", "1"));
            }

            if (this.x2Btn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.MultiplyByTwo", "1"));
            }

            if (this.x3Btn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.MultiplyByThree", "1"));
            }

            if (this.divTwoBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.DivideByTwo", "1"));
            }

            if (this.divThreeBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.DivideByThree", "1"));
            }

            if (this.uploadBtn == btn) {
                NetworkHandler.instance().sendToServer(PacketPatternUpload.button(isShiftKeyDown()));
            }

            if (this.plusOneBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.IncreaseByOne", "1"));
            }

            if (this.minusOneBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.DecreaseByOne", "1"));
            }

            if (this.substitutionsEnabledBtn == btn || this.substitutionsDisabledBtn == btn) {
                NetworkHandler.instance()
                        .sendToServer(
                                new PacketValueConfig("PatternTerminal.Substitute", this.substitutionsEnabledBtn == btn ? SUBSITUTION_DISABLE : SUBSITUTION_ENABLE));
            }

            if (this.fluidSubstitutionsEnabledBtn == btn || this.fluidSubstitutionsDisabledBtn == btn) {
                NetworkHandler.instance()
                        .sendToServer(
                                new PacketValueConfig("PatternTerminal.SubstituteFluids", this.fluidSubstitutionsEnabledBtn == btn ? SUBSITUTION_DISABLE : SUBSITUTION_ENABLE));
            }
        } catch (final IOException e) {
            AELog.error(e);
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        this.modeTabs.clear();
        this.pickerOpen = false;
        for (final PatternEncodingMode mode : PatternEncodingModes.getAll()) {
            final GuiTabButton tab = new GuiTabButton(this.guiLeft + MODE_TAB_X, this.guiTop + this.ySize - MODE_TAB_Y_FROM_BOTTOM,
                    mode.getIcon(), I18n.format(mode.getTranslationKey()), this.itemRender);
            this.modeTabs.put(mode.getId(), tab);
            this.buttonList.add(tab);
        }

        if (this.modeTabs.size() > 2) {
            this.modesBtn = new GuiTabButton(this.guiLeft + MODE_TAB_X, this.guiTop + this.ySize - MODE_PICKER_Y_FROM_BOTTOM,
                    AEApi.instance().definitions().materials().blankPattern().maybeStack(1).orElse(ItemStack.EMPTY),
                    GuiText.PatternModes.getLocal(), this.itemRender);
            this.buttonList.add(this.modesBtn);
        }

        this.substitutionsEnabledBtn = new GuiImgButton(this.guiLeft + 84, this.guiTop + this.ySize - 163, Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(this.guiLeft + 84, this.guiTop + this.ySize - 163, Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsDisabledBtn);

        this.fluidSubstitutionsEnabledBtn = new GuiImgButton(this.guiLeft + 94, this.guiTop + this.ySize - 163, Settings.ACTIONS, FluidSubstitution.ENABLED);
        this.fluidSubstitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.fluidSubstitutionsEnabledBtn);

        this.fluidSubstitutionsDisabledBtn = new GuiImgButton(this.guiLeft + 94, this.guiTop + this.ySize - 163, Settings.ACTIONS, FluidSubstitution.DISABLED);
        this.fluidSubstitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.fluidSubstitutionsDisabledBtn);

        this.clearBtn = new GuiImgButton(this.guiLeft + 74, this.guiTop + this.ySize - 163, Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);

        this.x3Btn = new GuiImgButton(this.guiLeft + 128, this.guiTop + this.ySize - 158, Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        this.buttonList.add(this.x3Btn);

        this.x2Btn = new GuiImgButton(this.guiLeft + 128, this.guiTop + this.ySize - 148, Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        this.buttonList.add(this.x2Btn);

        this.plusOneBtn = new GuiImgButton(this.guiLeft + 128, this.guiTop + this.ySize - 138, Settings.ACTIONS, ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        this.buttonList.add(this.plusOneBtn);

        this.divThreeBtn = new GuiImgButton(this.guiLeft + 100, this.guiTop + this.ySize - 158, Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        this.buttonList.add(this.divThreeBtn);

        this.divTwoBtn = new GuiImgButton(this.guiLeft + 100, this.guiTop + this.ySize - 148, Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        this.buttonList.add(this.divTwoBtn);

        this.minusOneBtn = new GuiImgButton(this.guiLeft + 100, this.guiTop + this.ySize - 138, Settings.ACTIONS, ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        this.buttonList.add(this.minusOneBtn);

        this.encodeBtn = new GuiImgButton(this.guiLeft + 147, this.guiTop + this.ySize - 142, Settings.ACTIONS, ActionItems.ENCODE);
        this.buttonList.add(this.encodeBtn);

        // Beside encode, and half its size: the window's art ends at 176 and a full one there would hang
        // over the edge. The two are the pair of things done with a finished pattern.
        if (AEConfig.instance().isFeatureEnabled(AEFeature.PATTERN_UPLOAD)) {
            this.uploadBtn = new GuiImgButton(this.guiLeft + 164, this.guiTop + this.ySize - 138, Settings.ACTIONS, ActionItems.UPLOAD);
            this.uploadBtn.setHalfSize(true);
            this.buttonList.add(this.uploadBtn);
        }

        this.invertBtn = new GuiImgButton(this.guiLeft + BUTTONS_LEFT, this.guiTop + this.ySize - 165, Settings.ACTIONS, PatternSlotConfig.C_32_8);
        this.invertBtn.setHalfSize(true);
        this.buttonList.add(this.invertBtn);

        this.pageScrollBar.setTop(this.ySize - 164);
    }

    /**
     * The processing grids move with the page and the orientation, so their positions are re-derived every
     * frame before anything is drawn from them. The container owns the arithmetic; the screen only feeds
     * it the page it is showing and reapplies its own offsets to whatever moved.
     */
    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (this.seenPatternLoads != this.container.patternLoads) {
            // The first reading only records where the count already stood - the terminal opens on the
            // first page anyway, and the initial sync is not a pattern being laid out.
            if (this.seenPatternLoads != Integer.MIN_VALUE) {
                this.showFirstPage();
            }
            this.seenPatternLoads = this.container.patternLoads;
        }

        this.container.setActivePage(this.pageScrollBar.getCurrentScroll());
        this.container.refreshOutputIfDirty();
        this.container.updateSlotVisibility();
        // Buttons are drawn between the background and the foreground layer, so which ones show and
        // where they sit have to be decided here; from drawFG they would always be a frame behind.
        this.handleButtonVisibility();
        this.layOutButtons();

        for (final Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof AppEngSlot aeSlot && aeSlot.getX() < 197) {
                aeSlot.xPos = aeSlot.getX();
                this.repositionSlot(aeSlot);
            }
        }

        super.drawBG(offsetX, offsetY, mouseX, mouseY);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        this.fontRenderer.drawString(GuiText.PatternTerminal.getLocal(), 8, this.ySize - 96 + 2 - this.getReservedSpace(), 4210752);
        this.drawFluidSubstitutionHint();

        if (this.isProcessingMode()) {
            this.pageScrollBar.draw(this);
        }

        if (this.pickerOpen) {
            this.drawModePicker(mouseX - this.guiLeft, mouseY - this.guiTop);
        }
    }

    private void switchTo(final ResourceLocation mode) {
        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Mode", mode.toString()));
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    private boolean isProcessingMode() {
        return PatternEncodingModes.PROCESSING.equals(this.container.getEncodingMode());
    }

    private int pickerLeft() {
        return MODE_TAB_X - this.pickerWidth();
    }

    private int pickerTop() {
        return this.ySize - MODE_TAB_Y_FROM_BOTTOM;
    }

    private int pickerWidth() {
        return PatternEncodingModes.getAll().size() * PICKER_CELL + 2 * PICKER_PADDING;
    }

    /** While the picker is open nothing beneath it is under the mouse: no slot lights up, none says its name. */
    @Override
    protected boolean isPointInRegion(final int rectX, final int rectY, final int rectWidth, final int rectHeight,
            final int pointX, final int pointY) {
        if (this.pickerOpen && this.inPicker(pointX - this.guiLeft, pointY - this.guiTop)) {
            return false;
        }
        return super.isPointInRegion(rectX, rectY, rectWidth, rectHeight, pointX, pointY);
    }

    private boolean inPicker(final int x, final int y) {
        return x >= this.pickerLeft() && x < this.pickerLeft() + this.pickerWidth()
                && y >= this.pickerTop() && y < this.pickerTop() + PICKER_CELL + 2 * PICKER_PADDING;
    }

    /** @return the mode whose cell is at that point, in coordinates relative to the window, or null. */
    @Nullable
    private PatternEncodingMode modeInPicker(final int x, final int y) {
        final int top = this.pickerTop() + PICKER_PADDING;
        final int left = this.pickerLeft() + PICKER_PADDING;
        if (y < top || y >= top + PICKER_CELL || x < left) {
            return null;
        }
        final int index = (x - left) / PICKER_CELL;
        final List<PatternEncodingMode> modes = PatternEncodingModes.getAll();
        return index < modes.size() ? modes.get(index) : null;
    }

    /** Drawn over the slots, so it is lifted above them the way a tooltip is. */
    private void drawModePicker(final int mouseX, final int mouseY) {
        final List<PatternEncodingMode> modes = PatternEncodingModes.getAll();
        final PatternEncodingMode hovered = this.modeInPicker(mouseX, mouseY);

        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 300);
        drawPanel(this.pickerLeft(), this.pickerTop(), this.pickerWidth(), PICKER_CELL + 2 * PICKER_PADDING);

        for (int i = 0; i < modes.size(); i++) {
            final int x = this.pickerLeft() + PICKER_PADDING + i * PICKER_CELL + 1;
            final int y = this.pickerTop() + PICKER_PADDING + 1;
            drawSlotWell(x, y);
            this.drawItem(x, y, modes.get(i).getIcon());
            if (modes.get(i) == hovered || modes.get(i).getId().equals(this.container.getEncodingMode())) {
                GlStateManager.disableLighting();
                GlStateManager.disableDepth();
                drawRect(x, y, x + 16, y + 16, PICKER_HOVER_TINT);
                GlStateManager.enableDepth();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        GlStateManager.popMatrix();

        if (hovered != null) {
            this.drawHoveringText(Collections.singletonList(I18n.format(hovered.getTranslationKey())), mouseX, mouseY,
                    this.fontRenderer);
        }
    }

    /** Which half of the button cluster belongs to the mode on screen. */
    private void handleButtonVisibility() {
        if (this.uploadBtn != null) {
            final boolean send = this.container.patternSlotOUT.getHasStack();
            // The same button both ways round: up while there is a pattern to send, down while the last one
            // sent can still be fetched back. Left on screen and dim when it is neither, so that a terminal
            // says the button is there before there is ever a pattern in the slot for it to act on.
            final boolean undo = !send && this.container.canUndoUpload;

            this.uploadBtn.enabled = send || undo;
            this.uploadBtn.set(undo ? ActionItems.UPLOAD_UNDO : ActionItems.UPLOAD);
        }

        final ResourceLocation mode = this.container.getEncodingMode();
        for (final Map.Entry<ResourceLocation, GuiTabButton> tab : this.modeTabs.entrySet()) {
            tab.getValue().visible = tab.getKey().equals(mode);
        }

        if (this.container.isCraftingMode()) {
            this.x2Btn.visible = false;
            this.x3Btn.visible = false;
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = false;
            this.minusOneBtn.visible = false;
            this.invertBtn.visible = false;

            if (this.container.substitute) {
                this.substitutionsEnabledBtn.visible = true;
                this.substitutionsDisabledBtn.visible = false;
            } else {
                this.substitutionsEnabledBtn.visible = false;
                this.substitutionsDisabledBtn.visible = true;
            }

            if (this.container.substituteFluids) {
                this.fluidSubstitutionsEnabledBtn.visible = true;
                this.fluidSubstitutionsDisabledBtn.visible = false;
            } else {
                this.fluidSubstitutionsEnabledBtn.visible = false;
                this.fluidSubstitutionsDisabledBtn.visible = true;
            }
        } else {
            final boolean processing = this.isProcessingMode();
            this.substitutionsEnabledBtn.visible = false;
            this.substitutionsDisabledBtn.visible = false;
            this.fluidSubstitutionsEnabledBtn.visible = false;
            this.fluidSubstitutionsDisabledBtn.visible = false;
            this.x2Btn.visible = processing;
            this.x3Btn.visible = processing;
            this.divTwoBtn.visible = processing;
            this.divThreeBtn.visible = processing;
            this.plusOneBtn.visible = processing;
            this.minusOneBtn.visible = processing;
            this.invertBtn.visible = processing;
        }
    }

    /**
     * Crafting mode keeps the row of toggles it always had, above a three-by-three grid. Processing mode
     * has no room there and stacks them in the gap between its two grids instead, which inverting moves.
     */
    private void layOutButtons() {
        if (this.container.isCraftingMode()) {
            this.clearBtn.x = this.guiLeft + 74;
            this.clearBtn.y = this.guiTop + this.ySize - 163;
            return;
        }

        final int left = this.guiLeft + BUTTONS_LEFT + (this.container.isInverted() ? BUTTONS_INVERTED_SHIFT : 0);
        final int right = left + 10;
        final int top = this.guiTop + this.ySize - 165;

        this.clearBtn.x = left;
        this.clearBtn.y = top;
        this.invertBtn.x = right;
        this.invertBtn.y = top;

        this.divTwoBtn.x = left;
        this.divTwoBtn.y = top + 10;
        this.x2Btn.x = right;
        this.x2Btn.y = top + 10;

        this.divThreeBtn.x = left;
        this.divThreeBtn.y = top + 20;
        this.x3Btn.x = right;
        this.x3Btn.y = top + 20;

        this.minusOneBtn.x = left;
        this.minusOneBtn.y = top + 30;
        this.plusOneBtn.x = right;
        this.plusOneBtn.y = top + 30;

        this.invertBtn.set(this.container.isInverted() ? PatternSlotConfig.C_8_32 : PatternSlotConfig.C_32_8);
    }

    /**
     * Tints the ingredients the network would fill in for, while the fluid-substitution button is under the
     * cursor. Decided by {@link PatternHelper#findFabricatedSlots}, the same rule the pattern itself will
     * use once encoded - so what lights up green here is exactly what the toggle will act on, and a
     * container the recipe does not simply empty stays dark instead of promising something.
     */
    private void drawFluidSubstitutionHint() {
        final GuiImgButton button = this.fluidSubstitutionsEnabledBtn.visible
                ? this.fluidSubstitutionsEnabledBtn
                : this.fluidSubstitutionsDisabledBtn;

        if (!button.visible || !button.isMouseOver()) {
            this.fabricatedSlots = null;
            return;
        }

        if (this.craftingGrid == null) {
            this.craftingGrid = new ArrayList<>(9);
            for (final Slot slot : this.inventorySlots.inventorySlots) {
                if (slot instanceof SlotFakeCraftingMatrix) {
                    this.craftingGrid.add(slot);
                }
            }
        }

        // Finding the recipe is a scan of every one registered, so it is redone only when the grid actually
        // changed - hovering a still grid costs nothing after the first frame.
        final int contents = this.gridContents();
        if (this.fabricatedSlots == null || contents != this.fabricatedFrom) {
            this.fabricatedSlots = this.findFabricated();
            this.fabricatedFrom = contents;
        }

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        for (int i = 0; i < this.craftingGrid.size() && i < this.fabricatedSlots.length; i++) {
            if (this.fabricatedSlots[i] != null) {
                final Slot slot = this.craftingGrid.get(i);
                drawRect(slot.xPos, slot.yPos, slot.xPos + 16, slot.yPos + 16, FABRICATED_SLOT_TINT);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private GenericStack[] findFabricated() {
        final InventoryCrafting grid = new InventoryCrafting(new ContainerNull(), 3, 3);

        for (int i = 0; i < this.craftingGrid.size() && i < 9; i++) {
            grid.setInventorySlotContents(i, this.craftingGrid.get(i).getStack().copy());
        }

        return PatternHelper.findFabricatedSlots(grid, CraftingManager.findMatchingRecipe(grid, this.mc.world));
    }

    private int gridContents() {
        int hash = 1;

        for (final Slot slot : this.craftingGrid) {
            final ItemStack is = slot.getStack();
            hash = hash * 31 + (is.isEmpty() ? 0 : Item.getIdFromItem(is.getItem()) * 31 + is.getItemDamage());
        }

        return hash;
    }

    @Override
    protected String getBackground() {
        if (this.container.isCraftingMode()) {
            return BACKGROUND_CRAFTING_MODE;
        }

        return this.container.isInverted() ? BACKGROUND_PROCESSING_INVERTED_MODE : BACKGROUND_PROCESSING_MODE;
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        if (this.pickerOpen) {
            final PatternEncodingMode picked = this.modeInPicker(xCoord - this.guiLeft, yCoord - this.guiTop);
            if (picked != null) {
                this.pickerOpen = false;
                if (!picked.getId().equals(this.container.getEncodingMode())) {
                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Mode", picked.getId().toString()));
                }
                return;
            }
            // Anywhere else closes it, and still does what a click there does - unless it was the button that
            // opens it, which would open it again.
            if (this.modesBtn == null || !this.modesBtn.isMouseOver()) {
                this.pickerOpen = false;
            }
        }
        if (this.isProcessingMode()) {
            this.pageScrollBar.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int c, final long d) {
        super.mouseClickMove(x, y, c, d);
        if (this.isProcessingMode()) {
            this.pageScrollBar.click(this, x - this.guiLeft, y - this.guiTop);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        final int wheel = Mouse.getEventDWheel();

        if (wheel != 0 && this.isProcessingMode()) {
            final int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
            final int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

            if (this.pageScrollBar.contains(x - this.guiLeft, y - this.guiTop)) {
                this.pageScrollBar.wheel(wheel);
                return;
            }
        }

        super.handleMouseInput();
    }

    @Override
    protected void repositionSlot(final AppEngSlot s) {
        final int offsetPlayerSide = s.isPlayerSide() ? 5 : 3;

        s.yPos = s.getY() + this.ySize - 78 - offsetPlayerSide;
    }

    /**
     * @return the key and amount a dragged HEI ingredient stands for, or null if this grid cannot hold it.
     * <p>
     * Only the processing grid can hold anything that is not an item. The crafting matrix is item-only - a
     * recipe there is matched against real items, and a wrapped key in it would encode an item that does not
     * exist - so a dragged fluid is refused rather than converted, which leaves the slot unhighlighted while
     * the drag is in flight.
     * <p>
     * Must be called while the drop is being handled, never while merely listing targets: which of the two
     * things a filled container stands for is read off the mouse button, and HEI asks for targets on hover,
     * with nothing held down yet.
     */
    @Nullable
    private static GenericStack ghostPayloadOf(final Object ingredient, final boolean processing) {
        if (!(ingredient instanceof ItemStack)) {
            final GenericStack dragged = IngredientConverters.toStack(ingredient);
            return processing ? dragged : null;
        }
        if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
            // Same rule as clicking a pattern slot with the container in hand: left button takes what it
            // HOLDS, right button takes the container itself. Dragging a bucket used to be the one way in
            // that ignored this and always left a bucket behind.
            if (processing && !dropsContainerItself()) {
                final GenericStack contained = ContainerItemStrategies.getContainedStack(stack);
                if (contained != null && contained.amount() > 0) {
                    return contained;
                }
            }
            return GenericStack.resolveItemStack(stack);
        }
        return null;
    }

    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        final boolean processing = !this.container.isCraftingMode();

        // Whether the grid can take this at all, which is all that can be settled now - the answer is null
        // or not-null the same way whichever button ends the drag, and only the value depends on it.
        if (ghostPayloadOf(ingredient, processing) == null) {
            return Collections.emptyList();
        }
        List<Target<?>> targets = new ArrayList<>();
        for (Slot slot : this.inventorySlots.inventorySlots) {
            // A slot scrolled off the page is still in the container; offering it as a drop target would
            // put the ingredient somewhere the screen cannot show.
            if (slot instanceof SlotFake && !((SlotFake) slot).isHidden()) {
                Target<Object> target = new Target<Object>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(getGuiLeft() + slot.xPos, getGuiTop() + slot.yPos, 16, 16);
                    }

                    @Override
                    public void accept(Object ingredient) {
                        final GenericStack payload = ghostPayloadOf(ingredient, processing);
                        if (payload == null) {
                            return;
                        }
                        try {
                            NetworkHandler.instance().sendToServer(
                                    new PacketInventoryAction(InventoryAction.PLACE_JEI_GHOST_ITEM, (SlotFake) slot, payload));

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
                targets.add(target);
                mapTargetSlot.putIfAbsent(target, slot);
            }
        }
        return targets;
    }

    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }

    /** The blank patterns the network holds, whatever the slot that spends them happens to hold. */
    @Nullable
    private GridInventoryEntry networkBlankPatterns() {
        final AEItemKey blank = ContainerPatternEncoder.blankPatternKey();

        if (blank == null) {
            return null;
        }

        final GridInventoryEntry entry = this.getRepo().getEntry(blank);
        return entry != null && entry.isMeaningful() ? entry : null;
    }

    /**
     * The same, but only while the slot that spends them stands empty. That slot is where encoding reaches
     * when it has no blank of its own, so while it is empty it draws what it would reach for in its own
     * frame, and answers a click on their behalf.
     */
    @Nullable
    private GridInventoryEntry blankPatternsInNetwork() {
        final Slot slot = this.container.getBlankPatternSlot();
        return slot == null || slot.getHasStack() ? null : this.networkBlankPatterns();
    }

    /**
     * The slot counts what can be encoded with, not what it is holding, so its own blanks and the network's
     * are one figure. Splitting them would be a distinction the rest of this screen has stopped making.
     */
    @Override
    protected GenericStack displayedStackOf(final Slot s, final GenericStack resolved) {
        if (s != this.container.getBlankPatternSlot() || resolved == null) {
            return resolved;
        }

        final GridInventoryEntry blanks = this.networkBlankPatterns();
        return blanks == null ? resolved : new GenericStack(resolved.what(), resolved.amount() + blanks.getStoredAmount());
    }

    /**
     * The network's blanks where a click on them should reach the network rather than the slot: the slot
     * empty, so there is nothing of its own to take, and the cursor empty, since a cursor with something on
     * it means a player putting blanks in, which the slot takes as it always has.
     */
    @Nullable
    private GridInventoryEntry blankPatternsForClick(final Slot slot) {
        if (slot != this.container.getBlankPatternSlot() || !this.mc.player.inventory.getItemStack().isEmpty()) {
            return null;
        }
        return this.blankPatternsInNetwork();
    }

    private void sendNetworkAction(final InventoryAction action, final GridInventoryEntry entry) {
        this.container.setTargetStack(entry.getWhat());
        NetworkHandler.instance().sendToServer(new PacketInventoryAction(action, this.inventorySlots.inventorySlots.size(), 0));
    }

    @Override
    public void drawSlot(final Slot s) {
        if (s == this.container.getBlankPatternSlot()) {
            final GridInventoryEntry blanks = this.blankPatternsInNetwork();
            if (blanks != null) {
                // Darkened on a network without power, the same way and in the same order as the rows of
                // the terminal above - what is shown here is that network as much as they are.
                this.zLevel = 100.0F;
                this.itemRender.zLevel = 100.0F;

                if (!this.isPowered()) {
                    drawRect(s.xPos, s.yPos, 16 + s.xPos, 16 + s.yPos, 0x66111111);
                }

                this.zLevel = 0.0F;
                this.itemRender.zLevel = 0.0F;

                this.drawItem(s.xPos, s.yPos, ContainerPatternEncoder.blankPatternKey().toStack(1));
                this.stackSizeRenderer.renderStackSize(this.fontRenderer, blanks, s.xPos, s.yPos);
                return;
            }
        }
        super.drawSlot(s);
    }

    @Override
    protected void handleMouseClick(final Slot slot, final int slotIdx, final int mouseButton, final ClickType clickType) {
        final GridInventoryEntry blanks = this.blankPatternsForClick(slot);

        if (blanks != null) {
            InventoryAction action = null;

            switch (clickType) {
                case PICKUP:
                    action = mouseButton == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN;
                    // Nothing stocked leaves only one thing a left click can mean, and Alt asks for it
                    // outright - the same reading a terminal row gives.
                    if (action == InventoryAction.PICKUP_OR_SET_DOWN && (blanks.getStoredAmount() == 0 || isAltKeyDown())) {
                        action = InventoryAction.AUTO_CRAFT;
                    }
                    break;
                case QUICK_MOVE:
                    action = mouseButton == 1 ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
                    break;
                case CLONE:
                    if (blanks.isCraftable()) {
                        action = InventoryAction.AUTO_CRAFT;
                    } else if (this.mc.player.capabilities.isCreativeMode) {
                        action = InventoryAction.CREATIVE_DUPLICATE;
                    }
                    break;
                default:
            }

            if (action != null) {
                this.sendNetworkAction(action, blanks);
                return;
            }
        }

        super.handleMouseClick(slot, slotIdx, mouseButton, clickType);
    }

    /**
     * The wheel only draws blanks out, never puts them back. Putting them back is what a click already
     * does, and the action behind a wheel-down takes whatever sits on the cursor rather than what the slot
     * is for - over a slot that accepts blank patterns alone, a wheel that pushed anything at all into
     * storage would be a trap.
     */
    @Override
    protected void mouseWheelEvent(final int x, final int y, final int wheel) {
        final GridInventoryEntry blanks = wheel < 0 && this.getSlot(x, y) == this.container.getBlankPatternSlot()
                ? this.blankPatternsInNetwork()
                : null;
        final ItemStack held = this.mc.player.inventory.getItemStack();

        // Onto an empty cursor, or onto more of the same, exactly as a terminal row allows.
        if (blanks != null && (held.isEmpty() || ContainerPatternEncoder.blankPatternKey().matches(held))) {
            for (int i = -wheel; i > 0; i--) {
                this.sendNetworkAction(InventoryAction.ROLL_UP, blanks);
            }
            return;
        }

        super.mouseWheelEvent(x, y, wheel);
    }

    @Override
    protected void renderHoveredToolTip(final int mouseX, final int mouseY) {
        final Slot slot = this.getSlot(mouseX, mouseY);
        final GridInventoryEntry blanks = slot == this.container.getBlankPatternSlot()
                && this.mc.player.inventory.getItemStack().isEmpty()
                ? this.networkBlankPatterns()
                : null;

        if (blanks == null) {
            super.renderHoveredToolTip(mouseX, mouseY);
            return;
        }

        final AEItemKey blank = ContainerPatternEncoder.blankPatternKey();
        final List<String> lines = this.getItemToolTip(blank.toStack(1));

        // The figure on the slot is the two sides added up, so the slot states its own the way any other AE
        // slot does - a bare number - and the network states its own below.
        if (slot.getHasStack() && slot.getStack().getCount() > 1) {
            lines.add(TextFormatting.GRAY + NumberFormat.getNumberInstance(Locale.US).format(slot.getStack().getCount()));
        }

        // A slot frame anywhere else means the item is in it and stays until taken. This one is a window on
        // storage, where an autocrafter can spend the last blank between two frames.
        lines.add(TextFormatting.GRAY + GuiText.BlankPatternInNetwork.getLocal());

        if (blanks.getStoredAmount() > 0) {
            lines.add(TextFormatting.GRAY + String.format(ButtonToolTips.ItemsStored.getLocal(),
                    blank.formatAmount(blanks.getStoredAmount(), AmountFormat.FULL)));
        }

        if (blanks.isCraftable()) {
            lines.add(TextFormatting.GRAY + ButtonToolTips.ItemsCraftable.getLocal());
        }

        this.drawHoveringText(lines, mouseX, mouseY, this.fontRenderer);
    }

    /**
     * HEI reads recipes off the slot under the cursor, and an empty slot holds nothing to read. The ghost
     * is this screen drawing on its own account, so this screen has to name it.
     */
    @Nullable
    @Override
    public AEKey getKeyUnderMouse(final int mouseX, final int mouseY) {
        return this.getSlot(mouseX, mouseY) == this.container.getBlankPatternSlot() && this.blankPatternsInNetwork() != null
                ? ContainerPatternEncoder.blankPatternKey()
                : null;
    }
}
