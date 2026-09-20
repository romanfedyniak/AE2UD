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
import appeng.api.config.Settings;
import appeng.api.patterns.IPatternEncodingHost;
import appeng.api.patterns.PatternEncodingMode;
import appeng.api.patterns.PatternEncodingModes;
import appeng.api.patterns.client.IPatternTerminalScreen;
import appeng.api.patterns.client.PatternModePanel;
import appeng.api.patterns.client.PatternModePanels;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.IKeyUnderMouse;
import appeng.container.me.GridInventoryEntry;
import appeng.core.localization.ButtonToolTips;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.ITooltip;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerWirelessPatternTerminal;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
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
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
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


public class GuiPatternTerm extends GuiMEMonitorable implements IJEIGhostIngredients, IKeyUnderMouse, IPatternTerminalScreen {

    private static final String BACKGROUND_NO_PANEL = "guis/pattern.png";

    /** The width of the window texture, which is what a self-drawn panel covers. */
    private static final int TEXTURE_WIDTH = 197;

    /** Where the mode tab sits, the button below it, and the picker they open. */
    private static final int MODE_TAB_X = 173;
    private static final int MODE_TAB_Y_FROM_BOTTOM = 177;
    private static final int MODE_PICKER_Y_FROM_BOTTOM = 155;
    private static final int PICKER_CELL = 18;
    private static final int PICKER_PADDING = 4;
    /** What a slot is washed with under the cursor, which is the shade vanilla's own containers use. */
    private static final int HOVER_TINT = 0x80FFFFFF;

    private final ContainerPatternEncoder container;

    /** What the mode on screen looks like; rebuilt whenever the terminal changes mode. */
    private PatternModePanel panel;
    private ResourceLocation panelMode;
    private int seenPatternLoads = Integer.MIN_VALUE;

    /**
     * One tab per mode, all in the same place; only the active mode's is shown. Clicking it swaps crafting
     * and processing as it always has, and from an addon's mode it goes back to crafting.
     */
    private final Map<ResourceLocation, GuiTabButton> modeTabs = new LinkedHashMap<>();
    /** Shown only where an addon has registered a mode: the two built-in ones need no list. */
    private GuiTabButton modesBtn;
    private boolean pickerOpen;
    private GuiImgButton encodeBtn;
    private GuiImgButton uploadBtn;
    private GuiImgButton clearBtn;
    public Map<Target<?>, Object> mapTargetSlot = new HashMap<>();

    public GuiPatternTerm(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(inventoryPlayer, te, new ContainerPatternTerm(inventoryPlayer, te));
        this.container = (ContainerPatternTerm) this.inventorySlots;
        this.buildPanel();
    }

    public GuiPatternTerm(final InventoryPlayer inventoryPlayer, WirelessTerminalGuiObject te, final ContainerWirelessPatternTerminal wpt) {
        super(inventoryPlayer, te, wpt);
        this.container = (ContainerWirelessPatternTerminal) this.inventorySlots;
        this.buildPanel();
    }

    /**
     * Builds the panel of the mode the terminal is in. The panel decides how much room the screen sets aside for
     * it, so this runs before the screen is laid out, and again whenever the mode changes.
     */
    private void buildPanel() {
        this.panelMode = this.container.getEncodingMode();
        this.panel = PatternModePanels.create(this.container.getMode(), this);
        this.setReservedSpace(this.panel == null ? 0 : this.panel.getHeight());
        this.setViewCellColumnShown(this.panel == null || this.panel.showsViewCellColumn());
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {

            if (this.modesBtn == btn) {
                this.pickerOpen = !this.pickerOpen;
            }

            if (this.modeTabs.containsValue(btn)) {
                // The tab is the crafting/processing toggle it has always been, and nothing else: the list of
                // every mode has a button of its own, and a tab that opened it too would be the same button
                // twice. From an addon's mode the tab is the way back to the pair.
                this.switchTo(this.container.isCraftingMode()
                        ? PatternEncodingModes.PROCESSING
                        : PatternEncodingModes.CRAFTING);
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

            if (this.uploadBtn == btn) {
                NetworkHandler.instance().sendToServer(PacketPatternUpload.button(isShiftKeyDown()));
            }

            if (this.panel != null && this.panel.actionPerformed(btn)) {
                return;
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

        this.clearBtn = new GuiImgButton(this.guiLeft + 74, this.guiTop + this.ySize - 163, Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);

        this.encodeBtn = new GuiImgButton(this.guiLeft + 147, this.guiTop + this.ySize - 142, Settings.ACTIONS, ActionItems.ENCODE);
        this.buttonList.add(this.encodeBtn);

        // Beside encode, and half its size: the window's art ends at 176 and a full one there would hang
        // over the edge. The two are the pair of things done with a finished pattern.
        if (AEConfig.instance().isFeatureEnabled(AEFeature.PATTERN_UPLOAD)) {
            this.uploadBtn = new GuiImgButton(this.guiLeft + 164, this.guiTop + this.ySize - 138, Settings.ACTIONS, ActionItems.UPLOAD);
            this.uploadBtn.setHalfSize(true);
            this.buttonList.add(this.uploadBtn);
        }

        if (this.panel != null) {
            this.panel.addButtons(this.buttonList);
        }
    }

    /**
     * The panel's slots and buttons move with whatever it is showing, so their positions are re-derived every
     * frame before anything is drawn from them.
     */
    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (!this.container.getEncodingMode().equals(this.panelMode)) {
            this.buildPanel();
            this.refreshLayout();
        } else if (this.panel != null && this.panel.getHeight() != this.getReservedSpace()) {
            // A panel that grows with what the player puts in it - an addon's bench, which is five squares
            // across for one recipe and nine for another - reports a new height here rather than only when
            // the mode changes.
            this.setReservedSpace(this.panel.getHeight());
            this.refreshLayout();
        }

        if (this.seenPatternLoads != this.container.patternLoads) {
            // The first reading only records where the count already stood - the terminal opens on the
            // first page anyway, and the initial sync is not a pattern being laid out.
            if (this.seenPatternLoads != Integer.MIN_VALUE && this.panel != null) {
                this.panel.onPatternLoaded();
            }
            this.seenPatternLoads = this.container.patternLoads;
        }

        this.container.refreshOutputIfDirty();
        this.container.updateSlotVisibility();
        // Buttons are drawn between the background and the foreground layer, so which ones show and
        // where they sit have to be decided here; from drawFG they would always be a frame behind.
        this.handleButtonVisibility();

        if (this.panel != null) {
            // The panel gone before this one may have moved the terminal's own slots; they go home first,
            // so that a panel which leaves them alone gets the places the built-in modes use.
            this.container.getBlankPatternSlot().restoreHome();
            this.container.patternSlotOUT.restoreHome();
            // A view cell's home is written from the top of the window rather than from its bottom, so the
            // pass below leaves them alone and they are put back here themselves.
            for (final Slot cell : this.getViewCellSlots()) {
                if (cell instanceof AppEngSlot aeSlot) {
                    aeSlot.restoreHome();
                    aeSlot.xPos = aeSlot.getX();
                    aeSlot.yPos = aeSlot.getY();
                }
            }

            this.panel.layOut();
            this.panel.updateButtons();
        }

        for (final Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof AppEngSlot aeSlot && aeSlot.getX() < 197) {
                aeSlot.xPos = aeSlot.getX();
                this.repositionSlot(aeSlot);
            }
        }

        super.drawBG(offsetX, offsetY, mouseX, mouseY);

        if (this.panel != null) {
            // Translated the way the foreground layer already is, so that everything a panel draws - a plate,
            // a well, a texture of its own - is written in the same window coordinates as its slots.
            GlStateManager.pushMatrix();
            GlStateManager.translate(this.guiLeft, this.guiTop, 0.0F);

            // A panel that asks for no room above the inventory gets no plate there either; what it draws
            // of its own is all there is of it.
            if (this.drawsReservedSpace() && this.panel.getHeight() > 0) {
                drawPanel(0, this.getPanelTop(), Math.max(TEXTURE_WIDTH, this.panel.getWidth()),
                        this.panel.getHeight());
            }
            this.panel.drawBackground(mouseX - this.guiLeft, mouseY - this.guiTop);

            GlStateManager.popMatrix();
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        // A panel that paints its own plate has a frame where the title would otherwise sit, and one that
        // asks for no room at all has nowhere for it: the line under the list is the player's inventory.
        if (this.getReservedSpace() > 0) {
            this.fontRenderer.drawString(GuiText.PatternTerminal.getLocal(), 8,
                    this.getPanelTop() + (this.drawsReservedSpace() ? 4 : 2), 4210752);
        }

        if (this.panel != null) {
            this.panel.drawForeground(mouseX - this.guiLeft, mouseY - this.guiTop);
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

    private int pickerLeft() {
        final int right = this.modesBtn != null ? this.modesBtn.x - this.guiLeft : MODE_TAB_X;
        return right - this.pickerWidth();
    }

    private int pickerTop() {
        return this.modesBtn != null ? this.modesBtn.y - this.guiTop : this.ySize - MODE_PICKER_Y_FROM_BOTTOM;
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

    /**
     * A button under the open picker is not under the cursor, whatever its own rectangle says - the same
     * rule the slots below it already follow, and without it a button explains itself through the list.
     */
    @Override
    protected void drawTooltip(final ITooltip tooltip, final int mouseX, final int mouseY) {
        if (this.pickerOpen && this.inPicker(mouseX - this.guiLeft, mouseY - this.guiTop)) {
            return;
        }

        super.drawTooltip(tooltip, mouseX, mouseY);
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
                drawRect(x, y, x + 16, y + 16, HOVER_TINT);
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

    /** The terminal's own buttons; the panel decides where they sit and what else is on screen. */
    private void handleButtonVisibility() {
        final ResourceLocation mode = this.container.getEncodingMode();
        for (final Map.Entry<ResourceLocation, GuiTabButton> tab : this.modeTabs.entrySet()) {
            tab.getValue().visible = tab.getKey().equals(mode);
        }

        if (this.uploadBtn != null) {
            final boolean send = this.container.patternSlotOUT.getHasStack();
            // The same button both ways round: up while there is a pattern to send, down while the last one
            // sent can still be fetched back. Left on screen and dim when it is neither, so that a terminal
            // says the button is there before there is ever a pattern in the slot for it to act on.
            final boolean undo = !send && this.container.canUndoUpload;

            this.uploadBtn.enabled = send || undo;
            this.uploadBtn.set(undo ? ActionItems.UPLOAD_UNDO : ActionItems.UPLOAD);
        }
    }

    @Override
    public void placeButton(final TerminalButton which, final int x, final int y) {
        if (which == TerminalButton.MODE_TAB) {
            // Every mode's tab is in the same place, and only the active one is on screen.
            for (final GuiTabButton tab : this.modeTabs.values()) {
                tab.x = this.guiLeft + x;
                tab.y = this.guiTop + y;
            }
            return;
        }

        final GuiButton button = which == TerminalButton.MODES ? this.modesBtn : this.terminalButton(which);
        if (button != null) {
            button.x = this.guiLeft + x;
            button.y = this.guiTop + y;
        }
    }

    @Override
    public void setButtonVisible(final TerminalButton which, final boolean visible) {
        final GuiImgButton button = this.terminalButton(which);
        if (button != null) {
            button.visible = visible;
        }
    }

    @Nullable
    private GuiImgButton terminalButton(final TerminalButton which) {
        switch (which) {
            case ENCODE:
                return this.encodeBtn;
            case CLEAR:
                return this.clearBtn;
            case UPLOAD:
                return this.uploadBtn;
            default:
                return null;
        }
    }

    @Override
    protected String getBackground() {
        final String background = this.panel == null ? null : this.panel.getBackground();
        return background == null ? BACKGROUND_NO_PANEL : background;
    }

    /**
     * A panel that names no window texture is one that paints itself, and the crafting panel's texture behind
     * it would only show through. It gets a plain plate of its own height instead, which is also the only way
     * a panel taller than the texture allows can be drawn at all.
     */
    @Override
    protected boolean drawsReservedSpace() {
        return this.panel != null && this.panel.getBackground() == null;
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        if (this.pickerOpen) {
            final PatternEncodingMode picked = this.modeInPicker(xCoord - this.guiLeft, yCoord - this.guiTop);
            if (picked != null) {
                this.pickerOpen = false;
                if (!picked.getId().equals(this.container.getEncodingMode())) {
                    this.switchTo(picked.getId());
                }
                return;
            }
            // Anywhere else closes it, and still does what a click there does - unless it was the button that
            // opens it, which would open it again.
            if (this.modesBtn == null || !this.modesBtn.isMouseOver()) {
                this.pickerOpen = false;
            }
        }
        if (this.panel != null && this.panel.mouseClicked(xCoord - this.guiLeft, yCoord - this.guiTop, btn)) {
            return;
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int c, final long d) {
        super.mouseClickMove(x, y, c, d);
        if (this.panel != null) {
            this.panel.mouseDragged(x - this.guiLeft, y - this.guiTop, c);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        final int wheel = Mouse.getEventDWheel();

        if (wheel != 0 && this.panel != null) {
            final int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
            final int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

            if (this.panel.mouseWheel(x - this.guiLeft, y - this.guiTop, wheel)) {
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
        List<Target<?>> targets = new ArrayList<>();
        for (Slot slot : this.inventorySlots.inventorySlots) {
            // A slot scrolled off the page is still in the container; offering it as a drop target would
            // put the ingredient somewhere the screen cannot show.
            if (slot instanceof SlotFake && !((SlotFake) slot).isHidden()) {
                // Asked of the square rather than of the mode: a grid standing for a bench's slots is a
                // recipe's shape, and a dragged bucket belongs in it as a bucket.
                final boolean processing = !(slot instanceof SlotFakeCraftingMatrix);
                if (ghostPayloadOf(ingredient, processing) == null) {
                    continue;
                }
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

    @Override
    public IPatternEncodingHost getHost() {
        return this.container;
    }

    @Override
    public List<Slot> getGridSlots(final String grid) {
        return this.container.getGridSlots(grid);
    }

    @Override
    public int getGuiLeft() {
        return this.guiLeft;
    }

    @Override
    public int getGuiTop() {
        return this.guiTop;
    }

    @Override
    public List<Slot> getViewCellSlots() {
        final List<Slot> cells = new ArrayList<>();

        for (int index = 0; index < this.container.getViewCells().length; index++) {
            final Slot cell = this.container.getCellViewSlot(index);
            if (cell != null) {
                cells.add(cell);
            }
        }

        return cells;
    }

    @Override
    public int getXSize() {
        return this.xSize;
    }

    @Override
    public int getYSize() {
        return this.ySize;
    }

    @Override
    public int getPanelWidth() {
        return TEXTURE_WIDTH;
    }

    /** Half of whatever the panel draws beside the window, which is what the window moves over to make room. */
    @Override
    protected int getHorizontalShift() {
        if (this.panel == null) {
            return 0;
        }

        int left = 0;
        int right = this.xSize;

        for (final Rectangle area : this.panel.getOutsideAreas()) {
            left = Math.min(left, area.x);
            right = Math.max(right, area.x + area.width);
        }

        return (left + right - this.xSize) / 2;
    }

    @Override
    public int getPanelTop() {
        return this.ySize - 96 - this.getReservedSpace();
    }

    /**
     * The terminal's slots are written relative to its bottom edge - see {@link #repositionSlot} - so a panel
     * that thinks in window coordinates has its position translated here rather than in every panel.
     */
    @Override
    public void placeSlot(final Slot slot, final int x, final int y) {
        if (slot instanceof AppEngSlot aeSlot) {
            aeSlot.setX(x);
            aeSlot.setY(y - this.ySize + 81);
            // Put it there now as well as recording where it belongs. The pass that re-derives every slot's
            // place from its home runs after this one and would do the same, but a slot a panel has just
            // placed should be where it was put whether or not anything else runs.
            aeSlot.xPos = x;
            aeSlot.yPos = y;
        }
    }

    @Override
    public void placeSlot(final TerminalSlot which, final int x, final int y) {
        final Slot slot = which == TerminalSlot.BLANK_PATTERN
                ? this.container.getBlankPatternSlot()
                : this.container.patternSlotOUT;
        this.placeSlot(slot, x, y);
    }

    /**
     * The buttons a panel put outside the window, so HEI's item list keeps off them. The column to the left
     * is the terminal's own and its parent already reports it.
     */
    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> area = new ArrayList<>(super.getJEIExclusionArea());

        for (final GuiButton button : this.buttonList) {
            if (button.x + button.width > this.guiLeft + this.xSize) {
                addButtonArea(area, button);
            }
        }

        if (this.panel != null && this.panel.getWidth() > this.xSize) {
            area.add(new Rectangle(this.guiLeft + this.xSize, this.guiTop + this.getPanelTop(),
                    this.panel.getWidth() - this.xSize, this.panel.getHeight()));
        }

        if (this.panel != null) {
            for (final Rectangle rect : this.panel.getOutsideAreas()) {
                area.add(new Rectangle(this.guiLeft + rect.x, this.guiTop + rect.y, rect.width, rect.height));
            }
        }

        return area;
    }

    @Override
    public void sendModeAction(final String action) {
        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.ModeAction", action));
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    @Override
    public void refreshPanelLayout() {
        this.refreshLayout();
    }

    @Override
    public FontRenderer getFontRenderer() {
        return this.fontRenderer;
    }

    @Override
    public void drawPanelBackground(final int x, final int y, final int width, final int height) {
        drawPanel(x, y, width, height);
    }

    @Override
    public void drawSlotBackground(final int x, final int y) {
        drawSlotWell(x, y);
    }

    @Override
    public void drawWellBackground(final int x, final int y, final int width, final int height) {
        drawWell(x, y, width, height);
    }

    @Override
    public void drawRectangle(final int x, final int y, final int width, final int height, final int colour) {
        drawRect(x, y, x + width, y + height, colour);
        // drawRect leaves whatever colour it painted with set, and the next thing drawn is usually textured.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void drawSlotHighlight(final int x, final int y) {
        // Over the item the panel has just drawn, so lighting and depth go the way they do for the picker's
        // own cells; vanilla washes a hovered slot the same way, after everything in it.
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        drawRect(x, y, x + 16, y + 16, HOVER_TINT);
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void drawItemStack(final int x, final int y, final ItemStack stack) {
        this.drawItem(x, y, stack);
    }

    @Override
    public void drawText(final String text, final int x, final int y, final int colour) {
        this.fontRenderer.drawString(text, x, y, colour);
    }

    @Override
    public void drawTooltip(final List<String> lines, final int x, final int y) {
        this.drawHoveringText(lines, x, y, this.fontRenderer);
    }

    @Override
    public void bindTexture(final String modId, final String path) {
        this.mc.getTextureManager().bindTexture(new ResourceLocation(modId, "textures/" + path));
    }

    @Override
    public void drawTexture(final int x, final int y, final int u, final int v, final int width, final int height) {
        this.drawTexturedModalRect(x, y, u, v, width, height);
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
        if (this.getSlot(mouseX, mouseY) == this.container.getBlankPatternSlot() && this.blankPatternsInNetwork() != null) {
            return ContainerPatternEncoder.blankPatternKey();
        }

        return this.panel == null ? null
                : this.panel.getKeyUnderMouse(mouseX - this.guiLeft, mouseY - this.guiTop);
    }
}
