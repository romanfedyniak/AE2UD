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
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ITerminalHost;
import appeng.container.me.GridInventoryEntry;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.GuiImageExport;
import appeng.client.gui.IKeyUnderMouse;
import appeng.client.gui.KeySearchTarget;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiCraftErrorPanel;
import appeng.client.gui.widgets.GuiCraftPriorityButton;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.client.gui.widgets.GuiIconButton;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.search.RepoSearch;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.core.AELog;
import appeng.core.AEClientConfig;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.Platform;
import com.google.common.base.Joiner;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class GuiCraftConfirm extends AEBaseGui implements IKeyUnderMouse {

    /** The Start button hangs off this edge and grows leftwards, so its right side never moves. */
    private static final int START_RIGHT = 212;
    private static final int START_WIDTH = 50;
    private static final int PRIORITY_GAP = 4;

    private static final int MIN_ROWS = 5;

    private static final int TITLE_LEFT = 8;
    private static final int TITLE_TOP = 7;
    /** The right end of the header strip; the title has what is left of it. */
    private static final int SEARCH_LEFT = 92;
    private static final int SEARCH_TOP = 4;
    private static final int SEARCH_WIDTH = 118;
    private static final int SEARCH_HEIGHT = 12;
    private static final int FOOTER_BUTTON_TOP = 25;
    private static final int SWITCH_VIEW_ICON = 13 * 16 + 3;
    private static final int SAVE_IMAGE_ICON = 8 * 16 + 4;
    private static final float IMAGE_SCALE = 2.0f;
    private static final int IMAGE_PADDING = 4;
    private static final float[] IMAGE_BACKGROUND = { 0.776F, 0.776F, 0.776F, 1.0F };
    /** The three cells of one row in the window texture, which is what the screen itself draws them from. */
    private static final int ROW_TEXTURE_X = 9;
    private static final int ROW_TEXTURE_WIDTH = 203;
    /** A cell starts this far above the item in it - the same four pixels the "missing" tint uses. */
    private static final int CELL_TOP_INSET = 4;
    private static final int ROW_HEIGHT = 23;
    private static final int TEXTURE_TOP_HEIGHT = 41;
    private static final int TEXTURE_BOTTOM_Y = 110;
    /**
     * The last row's own strip, drawn from {@link #TEXTURE_BOTTOM_Y} down to where the blank footer panel
     * starts in the texture.
     */
    private static final int TEXTURE_LAST_ROW_HEIGHT = 24;
    private static final int TEXTURE_FOOTER_Y = TEXTURE_BOTTOM_Y + TEXTURE_LAST_ROW_HEIGHT;
    private static final int TEXTURE_HEIGHT = 206;
    /**
     * The foot is taken in three pieces so that it can be shorter - or taller - than the texture draws it
     * without losing either end. Between them sat the strip the "Crafting CPU:" button used to occupy, and
     * the line repeating the selected CPU's storage and co-processors, both of which the CPU table on the
     * left has taken over; those rows are what the repeated body row replaces.
     * <p>
     * The head has to reach past the step where the window's right edge comes in. Sampling the foot from
     * below that step, which is how those rows used to be dropped, left the corner cut off.
     */
    private static final int FOOTER_HEAD = 14;
    private static final int FOOTER_BODY_ROW = 190;
    private static final int FOOTER_TAIL = 8;
    private static final int FOOTER_HEIGHT = 46;
    /** Six pixels clear of the buttons, which sit twenty-five up from the bottom. */
    private static final int FOOTER_TEXT_TOP = 39;
    private static final int FIXED_HEIGHT = TEXTURE_TOP_HEIGHT + TEXTURE_LAST_ROW_HEIGHT + FOOTER_HEIGHT
            - 2 * ROW_HEIGHT;

    private final ContainerCraftConfirm ccc;

    private int rows = MIN_ROWS;

    private final KeyCounter storage = new KeyCounter();
    private final KeyCounter pending = new KeyCounter();
    private final KeyCounter missing = new KeyCounter();
    private final KeyCounter craftingSteps = new KeyCounter();
    private final Map<AEKey, Long> usedPercentages = new HashMap<>();

    private static final int USED_PERCENT_25_COLOR = 0x1C4CA6;
    private static final int USED_PERCENT_50_COLOR = 0x1A751E;
    private static final int USED_PERCENT_75_COLOR = 0xE3940B;
    private static final int USED_PERCENT_100_COLOR = 0x660F0F;

    private final List<AEKey> visual = new ArrayList<>();
    /**
     * What the screen shows: {@link #visual} without the rows the search hides. The saved picture and the
     * Start button still answer for the whole plan, which is why the two lists are kept apart.
     */
    private final List<AEKey> displayed = new ArrayList<>();

    private GuiBridge OriginalGui;
    private GuiButton cancel;
    private GuiButton start;
    private GuiCraftPriorityButton priority;
    private GuiTabButton showTree;
    private GuiImgButton terminalStyleBox;
    private GuiIconButton saveImage;
    private GuiImgButton searchKeepBtn;
    private MEGuiTextField searchField;
    private final RepoSearch search = new RepoSearch();

    /** Where the search survives closing the screen, while the keep setting says it should. */
    private static String memoryText = "";
    private final GuiCraftingCPUTable cpuTable;
    private final GuiCraftErrorPanel errorPanel;
    private int tooltip = -1;

    public GuiCraftConfirm(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftConfirm(inventoryPlayer, te));
        this.xSize = 238;
        this.ySize = 206;

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);

        this.ccc = (ContainerCraftConfirm) this.inventorySlots;
        this.ccc.setGui(this);
        this.cpuTable = new GuiCraftingCPUTable(this, this.ccc);
        this.errorPanel = new GuiCraftErrorPanel(this, this.ccc);

        this.OriginalGui = GuiBridge.terminalFor(te);
    }

    boolean isAutoStart() {
        return ((ContainerCraftConfirm) this.inventorySlots).isAutoStart();
    }

    /**
     * Drawn over this screen rather than in its place: the plan cost a whole crafting calculation to work
     * out, and switching containers to type one number would throw it away.
     */
    protected void openPriority() {
        this.mc.displayGuiScreen(new GuiCraftPriority(this, this.mc.player.inventory,
                AEApi.instance().definitions().blocks().craftingUnit().maybeStack(1).orElse(ItemStack.EMPTY),
                this.ccc));
    }

    @Override
    public void initGui() {
        final TerminalStyle style = (TerminalStyle) AEClientConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
        final int availableRows = (this.height - 64 - FIXED_HEIGHT) / ROW_HEIGHT;
        this.rows = Math.max(MIN_ROWS, style.getRows(availableRows));
        this.ySize = FIXED_HEIGHT + this.rows * ROW_HEIGHT;
        super.initGui();

        this.start = new GuiButton(0, this.guiLeft + START_RIGHT - START_WIDTH, this.guiTop + this.ySize - 25,
                START_WIDTH, 20, GuiText.Start.getLocal());
        this.start.enabled = false;
        this.buttonList.add(this.start);

        this.priority = new GuiCraftPriorityButton(0, this.guiTop + this.ySize - 23);
        this.buttonList.add(this.priority);

        this.cpuTable.initGui(this.rows, this.buttonList);

        this.showTree = new GuiTabButton(this.guiLeft + this.xSize - 25, this.guiTop - 4, SWITCH_VIEW_ICON,
                GuiText.CraftingTree.getLocal(), this.itemRender);
        this.showTree.setHideEdge(1);
        this.buttonList.add(this.showTree);

        // Only when there is a screen to go back to. The add used to sit outside the branch, so a terminal
        // host this constructor has no GuiBridge for put a null in buttonList and GuiScreen.drawScreen
        // dereferenced it on the very first frame. Pre-existing, and unreachable until a host that offers no
        // way back could reach this screen at all.
        if (this.OriginalGui != null) {
            this.cancel = new GuiButton(0, this.guiLeft + 6, this.guiTop + this.ySize - 25, 50, 20, GuiText.Cancel.getLocal());
            this.buttonList.add(this.cancel);
        }

        this.saveImage = new GuiIconButton(this.guiLeft + this.xSize, this.guiTop + 28, SAVE_IMAGE_ICON,
                GuiText.SaveAsImage.getLocal());
        this.buttonList.add(this.saveImage);

        // On the far side from the CPU table, which now owns the space to the left of this screen.
        this.terminalStyleBox = new GuiImgButton(this.guiLeft + this.xSize, this.guiTop + 8,
                Settings.TERMINAL_STYLE, style);
        this.buttonList.add(this.terminalStyleBox);

        this.searchKeepBtn = new GuiImgButton(this.guiLeft + this.xSize, this.guiTop + 48,
                Settings.SEARCH_KEEP, AEClientConfig.instance().getConfigManager().getSetting(Settings.SEARCH_KEEP));
        this.buttonList.add(this.searchKeepBtn);

        this.searchField = new MEGuiTextField(this.fontRenderer, this.guiLeft + SEARCH_LEFT,
                this.guiTop + SEARCH_TOP, SEARCH_WIDTH, SEARCH_HEIGHT);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setMaxStringLength(100);
        this.searchField.setTextColor(MEGuiTextField.TEXT_COLOR);
        this.searchField.setVisible(true);

        if (AEClientConfig.instance().keepsSearch() && !memoryText.isEmpty()) {
            this.searchField.setText(memoryText, true);
        }

        // Over the plan list, leaving the header and the row of buttons below it visible.
        this.errorPanel.initGui(6, 18, this.xSize - 12, this.ySize - FOOTER_HEIGHT - 15, this.buttonList);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.cpuTable.updateScrollRange();
        this.errorPanel.update();

        // A plan that came up short can still be started, on the understanding that the cpu will sit waiting
        // until the missing ingredients are put into the network. The button says so rather than going dead.
        final boolean force = this.isSimulation() && !this.ccc.hasNoCPU();
        this.start.enabled = !this.ccc.hasNoCPU();
        this.start.displayString = (force ? GuiText.ForceStart : GuiText.Start).getLocal();
        // Grows leftwards for the longer label, keeping its right edge where the eye expects it. Measured
        // rather than nudged by a fixed amount, so a translation longer than either English word still fits.
        this.start.width = Math.max(START_WIDTH, this.fontRenderer.getStringWidth(this.start.displayString) + 12);
        this.start.x = this.guiLeft + START_RIGHT - this.start.width;
        // Follows Start leftwards, so the two stay together whichever label Start is wearing.
        this.priority.setPriority(this.ccc.getCraftPriority());
        this.priority.x = this.start.x - PRIORITY_GAP - this.priority.width;
        // Nothing to draw a tree or a picture of until the job has been worked out.
        this.showTree.enabled = this.ccc.getUsedBytes() > 0;
        this.saveImage.enabled = !this.visual.isEmpty();

        final int gx = (this.width - this.xSize) / 2;
        final int gy = (this.height - this.ySize) / 2;

        this.tooltip = -1;

        final int offY = 23;
        int y = 0;
        int x = 0;
        for (int z = 0; z < 3 * this.rows; z++) {
            final int minX = gx + 9 + x * 67;
            final int minY = gy + 22 + y * offY;

            if (minX < mouseX && minX + 67 > mouseX) {
                if (minY < mouseY && minY + offY - 2 > mouseY) {
                    this.tooltip = z;
                    break;
                }
            }

            x++;

            if (x > 2) {
                y++;
                x = 0;
            }
        }

        super.drawScreen(mouseX, mouseY, btn);
    }

    private boolean isSimulation() {
        return ((ContainerCraftConfirm) this.inventorySlots).isSimulation();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.cpuTable.drawFG(mouseX, mouseY);

        final long BytesUsed = this.ccc.getUsedBytes();
        // The word stands in for the title rather than beside it: the two together run past the window,
        // and when a plan is incomplete that is the first thing to say about it. Only once there is a plan
        // to call incomplete, though - the field reads as a simulation until the server has said otherwise.
        final String plan = (BytesUsed > 0 && this.isSimulation() ? GuiText.Simulation : GuiText.CraftingPlan).getLocal();
        this.fontRenderer.drawString(plan, TITLE_LEFT, TITLE_TOP, 4210752);

        // On the strip the footer leaves above its buttons, across the whole window: the count and the
        // wait message are both wider than anything the header has room for beside a search box.
        final String byteUsed = NumberFormat.getInstance().format(BytesUsed);
        final String bytes = BytesUsed > 0
                ? byteUsed + ' ' + GuiText.BytesUsed.getLocal()
                : GuiText.CalculatingWait.getLocal();
        this.fontRenderer.drawString(bytes, (this.xSize - this.fontRenderer.getStringWidth(bytes)) / 2,
                this.ySize - FOOTER_TEXT_TOP, 4210752);

        // The panel stands in for the list, and is drawn in the background layer so its own buttons stay on
        // top of it. Nothing of the list is drawn underneath.
        if (this.errorPanel.isShowing()) {
            return;
        }

        final int sectionLength = 67;

        int x = 0;
        int y = 0;
        final int xo = 9;
        final int yo = 22;
        final int viewStart = this.getScrollBar().getCurrentScroll() * 3;
        final int viewEnd = viewStart + 3 * this.rows;

        String dspToolTip = "";
        final List<String> lineList = new ArrayList<>();
        int toolPosX = 0;
        int toolPosY = 0;

        final int offY = 23;

        for (int z = viewStart; z < Math.min(viewEnd, this.displayed.size()); z++) {
            final AEKey refKey = this.displayed.get(z);
            if (refKey == null) {
                continue;
            }

            final boolean hovered = this.tooltip == z - viewStart;
            this.drawPlanEntry(refKey, x, y, hovered ? lineList : null);

            if (hovered) {
                dspToolTip = Platform.getItemDisplayName(refKey);

                if (lineList.size() > 0) {
                    dspToolTip = dspToolTip + '\n' + Joiner.on("\n").join(lineList);
                }

                toolPosX = x * (1 + sectionLength) + xo + sectionLength - 8;
                toolPosY = y * offY + yo;
            }

            x++;

            if (x > 2) {
                y++;
                x = 0;
            }
        }

        if (this.tooltip >= 0 && !dspToolTip.isEmpty()) {
            this.drawTooltip(toolPosX, toolPosY + 10, dspToolTip);
            return;
        }

        final String cpuTooltip = this.cpuTable.getTooltip(mouseX, mouseY);
        if (cpuTooltip != null) {
            this.drawTooltip(mouseX - offsetX, mouseY - offsetY, cpuTooltip);
            return;
        }

        if (this.searchField != null && this.searchField.isMouseIn(mouseX, mouseY)) {
            this.drawTooltip(mouseX - offsetX, mouseY - offsetY, RepoSearch.syntaxTooltip());
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.cpuTable.drawBG(offsetX, offsetY);
        this.setScrollBar();
        this.bindTexture("guis/craftingreport.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, TEXTURE_TOP_HEIGHT);
        int y = TEXTURE_TOP_HEIGHT;
        for (int row = 1; row < this.rows - 1; row++) {
            this.drawTexturedModalRect(offsetX, offsetY + y, 0, TEXTURE_TOP_HEIGHT,
                    this.xSize, ROW_HEIGHT);
            y += ROW_HEIGHT;
        }
        this.drawTexturedModalRect(offsetX, offsetY + y, 0, TEXTURE_BOTTOM_Y,
                this.xSize, TEXTURE_LAST_ROW_HEIGHT);
        this.drawFooter(offsetX, offsetY + y + TEXTURE_LAST_ROW_HEIGHT);

        this.errorPanel.drawBG(offsetX, offsetY);

        if (this.searchField != null) {
            drawWell(offsetX + SEARCH_LEFT, offsetY + SEARCH_TOP, SEARCH_WIDTH, SEARCH_HEIGHT);
            this.searchField.setMatched(!this.displayed.isEmpty() || this.searchField.getText().isEmpty());
            this.searchField.drawTextBox();
        }
    }

    /** See {@link #FOOTER_HEAD}: the step in the right edge, one plain row repeated, then the border. */
    private void drawFooter(final int x, final int y) {
        this.drawTexturedModalRect(x, y, 0, TEXTURE_FOOTER_Y, this.xSize, FOOTER_HEAD);

        for (int row = FOOTER_HEAD; row < FOOTER_HEIGHT - FOOTER_TAIL; row++) {
            this.drawTexturedModalRect(x, y + row, 0, FOOTER_BODY_ROW, this.xSize, 1);
        }

        this.drawTexturedModalRect(x, y + FOOTER_HEIGHT - FOOTER_TAIL, 0, TEXTURE_HEIGHT - FOOTER_TAIL,
                this.xSize, FOOTER_TAIL);
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> area = new ArrayList<>();
        area.add(this.cpuTable.getExclusionArea());
        addButtonArea(area, this.terminalStyleBox);
        addButtonArea(area, this.saveImage);
        addButtonArea(area, this.searchKeepBtn);
        addButtonArea(area, this.showTree);
        return area;
    }

    @Override
    public List<KeySearchTarget> getKeySearchTargets() {
        if (this.searchField == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new KeySearchTarget(this.searchField.getArea(), what -> {
            this.searchField.setText(RepoSearch.termFor(what), true);
            this.setScrollBar();
        }));
    }

    @Override
    public boolean isTextFieldFocused() {
        return this.searchField != null && this.searchField.isFocused();
    }

    @Override
    public void onGuiClosed() {
        memoryText = this.searchField != null && AEClientConfig.instance().keepsSearch()
                ? this.searchField.getText() : "";
        super.onGuiClosed();
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        if (this.searchField != null) {
            this.searchField.mouseClicked(xCoord, yCoord, btn);

            if (btn == 1 && this.searchField.isMouseIn(xCoord, yCoord)) {
                this.searchField.setText("", true);
                this.setScrollBar();
            }
        }

        super.mouseClicked(xCoord, yCoord, btn);

        this.cpuTable.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int c, final long d) {
        super.mouseClickMove(x, y, c, d);
        this.cpuTable.mouseClickMove(x, y);
    }

    @Override
    public void handleMouseInput() throws IOException {
        final int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
        final int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (this.cpuTable.handleMouseWheel(x, y, Mouse.getEventDWheel())) {
            return;
        }

        super.handleMouseInput();
    }

    /** {@link #visual} without what the query leaves out. */
    private void rebuildDisplayed() {
        this.search.setSearchString(this.searchField == null ? "" : this.searchField.getText());
        this.search.refresh();

        this.displayed.clear();
        for (final AEKey what : this.visual) {
            if (this.search.matches(what)) {
                this.displayed.add(what);
            }
        }
    }

    private void setScrollBar() {
        this.rebuildDisplayed();

        final int size = this.displayed.size();

        this.getScrollBar().setTop(19).setLeft(218).setHeight(this.rows * ROW_HEIGHT - 1);
        this.getScrollBar().setRange(0, (size + 2) / 3 - this.rows, 1);
    }

    public void postUpdate(final List<GridInventoryEntry> list, final byte ref) {
        switch (ref) {
            case 0:
                for (final GridInventoryEntry l : list) {
                    this.handleInput(this.storage, l);
                    this.updateMetadata(this.usedPercentages, l);
                }
                break;

            case 1:
                for (final GridInventoryEntry l : list) {
                    this.handleInput(this.pending, l);
                    this.craftingSteps.set(l.getWhat(), l.getRequestableAmount());
                }
                break;

            case 2:
                for (final GridInventoryEntry l : list) {
                    this.handleInput(this.missing, l);
                }
                break;
        }

        for (final GridInventoryEntry l : list) {
            final long amt = this.getTotal(l.getWhat());

            if (amt <= 0) {
                this.visual.remove(l.getWhat());
            } else if (!this.visual.contains(l.getWhat())) {
                this.visual.add(l.getWhat());
            }
        }

        this.setScrollBar();
    }

    private void handleInput(final KeyCounter s, final GridInventoryEntry l) {
        // Each of the three KeyCounters is keyed by AEKey, whose equals() is already the size-insensitive
        // identity the old findPrecise(IAEItemStack) dance existed to provide - a plain set() replaces it.
        s.set(l.getWhat(), l.getStoredAmount());
    }

    private void updateMetadata(final Map<AEKey, Long> target, final GridInventoryEntry entry) {
        if (entry.getStoredAmount() > 0 && entry.getRequestableAmount() > 0) {
            target.put(entry.getWhat(), entry.getRequestableAmount());
        } else {
            target.remove(entry.getWhat());
        }
    }

    private static String formatUsedPercent(final long fixedPercentage, final int maximumFractionDigits) {
        final NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(maximumFractionDigits);
        return format.format((double) fixedPercentage / ContainerCraftConfirm.USED_PERCENT_SCALE);
    }

    private static int getUsedPercentColor(final long fixedPercentage) {
        if (fixedPercentage <= 25 * ContainerCraftConfirm.USED_PERCENT_SCALE) {
            return USED_PERCENT_25_COLOR;
        }
        if (fixedPercentage <= 50 * ContainerCraftConfirm.USED_PERCENT_SCALE) {
            return USED_PERCENT_50_COLOR;
        }
        if (fixedPercentage <= 75 * ContainerCraftConfirm.USED_PERCENT_SCALE) {
            return USED_PERCENT_75_COLOR;
        }
        return USED_PERCENT_100_COLOR;
    }

    private long getTotal(final AEKey what) {
        return this.storage.get(what) + this.pending.get(what) + this.missing.get(what);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (this.cpuTable.keyTyped(character, key)) {
            return;
        }

        if (this.searchField != null && this.searchField.isFocused()
                && this.searchField.textboxKeyTyped(character, key)) {
            this.setScrollBar();
            return;
        }

        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.actionPerformed(this.start);
            }
            super.keyTyped(character, key);
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (this.toggleSearchKeep(btn, this.searchKeepBtn)) {
            return;
        }

        if (this.cpuTable.actionPerformed(btn)) {
            return;
        }

        if (this.errorPanel.actionPerformed(btn)) {
            return;
        }

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.terminalStyleBox) {
            final TerminalStyle current = (TerminalStyle) AEClientConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
            final TerminalStyle next = (TerminalStyle) Platform.rotateEnum(current, backwards,
                    Settings.TERMINAL_STYLE.getPossibleValues());
            AEClientConfig.instance().getConfigManager().putSetting(Settings.TERMINAL_STYLE, next);
            this.buttonList.clear();
            this.initGui();
            return;
        }

        if (btn == this.saveImage) {
            GuiImageExport.save(this.createPlanImage(), "-crafting-plan");
        }

        if (btn == this.showTree) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_CRAFTING_TREE));
        }

        if (btn == this.cancel) {
            if (this.ccc.hasAmountScreen) {
                // Back to the order, not out of it. The amount screen carries its own way back to the
                // terminal, so the way out is one button further rather than gone.
                NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_CRAFTING_AMOUNT));
            } else if (this.OriginalGui != null) {
                // No amount screen preceded this job (e.g. a Ctrl+Move Items HEI transfer), so there is
                // nothing to step back to - go straight to the terminal instead of an empty amount screen.
                NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.OriginalGui));
            }
        }

        if (btn == this.priority) {
            this.openPriority();
            return;
        }

        if (btn == this.start) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("Terminal.Start",
                        this.isSimulation() ? "Force" : "Start"));
            } catch (final Throwable e) {
                AELog.debug(e);
            }
        }
    }

    /**
     * One entry of the plan, drawn wherever it is asked for - the screen puts them in its own grid, the
     * picture puts every one of them in a grid of its own.
     *
     * @param tooltipLines collects the entry's lines when it is the one under the cursor, otherwise null
     */
    private void drawPlanEntry(final AEKey refKey, final int x, final int y, @Nullable final List<String> tooltipLines) {
        final int sectionLength = 67;
        final int xo = 9;
        final int yo = 22;
        final int offY = 23;

        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5, 0.5, 0.5);

        final long stored = this.storage.get(refKey);
        final long pendingAmount = this.pending.get(refKey);
        final long missingAmount = this.missing.get(refKey);
        final long steps = this.craftingSteps.get(refKey);
        final long usedPercent = this.usedPercentages.getOrDefault(refKey, 0L);

        int lines = 0;

        if (stored > 0) {
            lines++;
            if (usedPercent > 0) {
                lines++;
            }
        }
        if (missingAmount > 0) {
            lines++;
        }
        if (pendingAmount > 0) {
            lines++;
            if (steps > 0) {
                lines++;
            }
        }

        final int negY = ((lines - 1) * 5) / 2;
        int downY = 0;

        if (stored > 0) {
            // Through the key's formatter, so a fluid row reads "16B" rather than "16k".
            String str = GuiText.FromStorage.getLocal() + ": " + refKey.formatAmount(stored, AmountFormat.PREVIEW_LARGE);
            final int w = 4 + this.fontRenderer.getStringWidth(str);
            this.fontRenderer.drawString(str, (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                    (y * offY + yo + 6 - negY + downY) * 2, 4210752);

            if (tooltipLines != null) {
                tooltipLines.add(GuiText.FromStorage.getLocal() + ": " + refKey.formatAmount(stored, AmountFormat.FULL));
            }

            downY += 5;

            if (usedPercent > 0) {
                final String percentage = formatUsedPercent(usedPercent, 2);
                str = GuiText.FromStoragePercent.getLocal() + ": " + percentage + "%";
                final int percentWidth = 4 + this.fontRenderer.getStringWidth(str);
                this.fontRenderer.drawString(
                        str,
                        (int) ((x * (1 + sectionLength) + xo + sectionLength - 19
                                - (percentWidth * 0.5)) * 2),
                        (y * offY + yo + 6 - negY + downY) * 2,
                        getUsedPercentColor(usedPercent));

                if (tooltipLines != null) {
                    tooltipLines.add(GuiText.FromStoragePercent.getLocal() + ": "
                            + formatUsedPercent(usedPercent, 4) + "%");
                }

                downY += 5;
            }
        }

        boolean red = false;
        if (missingAmount > 0) {
            String str = GuiText.Missing.getLocal() + ": " + refKey.formatAmount(missingAmount, AmountFormat.PREVIEW_LARGE);
            final int w = 4 + this.fontRenderer.getStringWidth(str);
            this.fontRenderer.drawString(str, (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                    (y * offY + yo + 6 - negY + downY) * 2, 4210752);

            if (tooltipLines != null) {
                tooltipLines.add(GuiText.Missing.getLocal() + ": " + refKey.formatAmount(missingAmount, AmountFormat.FULL));
            }

            red = true;
            downY += 5;
        }

        if (pendingAmount > 0) {
            String str = GuiText.ToCraft.getLocal() + ": " + refKey.formatAmount(pendingAmount, AmountFormat.PREVIEW_LARGE);
            final int w = 4 + this.fontRenderer.getStringWidth(str);
            this.fontRenderer.drawString(str, (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                    (y * offY + yo + 6 - negY + downY) * 2, 4210752);

            if (tooltipLines != null) {
                tooltipLines.add(GuiText.ToCraft.getLocal() + ": " + refKey.formatAmount(pendingAmount, AmountFormat.FULL));
            }

            downY += 5;

            if (steps > 0) {
                str = GuiText.ToCraftRequests.getLocal() + ": "
                        + AEKeyType.items().formatAmount(steps, AmountFormat.SLOT);
                final int stepsWidth = 4 + this.fontRenderer.getStringWidth(str);
                this.fontRenderer.drawString(
                        str,
                        (int) ((x * (1 + sectionLength) + xo + sectionLength - 19
                                - (stepsWidth * 0.5)) * 2),
                        (y * offY + yo + 6 - negY + downY) * 2,
                        4210752);

                if (tooltipLines != null) {
                    tooltipLines.add(GuiText.ToCraftRequests.getLocal() + ": "
                            + NumberFormat.getInstance().format(steps));
                }
            }
        }

        GlStateManager.popMatrix();
        final int posX = x * (1 + sectionLength) + xo + sectionLength - 19;
        final int posY = y * offY + yo;

        this.drawItem(posX, posY, refKey.wrapForDisplayOrFilter());

        if (red) {
            final int startX = x * (1 + sectionLength) + xo;
            final int startY = posY - 4;
            drawRect(startX, startY, startX + sectionLength, startY + offY, 0x1AFF0000);
        }
    }

    /**
     * The whole plan as a picture, however many rows it runs to - the screen shows a window onto the list,
     * and a plan worth saving is usually longer than that.
     */
    @Nullable
    private BufferedImage createPlanImage() {
        final int entries = this.visual.size();
        if (entries == 0) {
            return null;
        }

        final int lines = (entries + 2) / 3;
        final int width = ROW_TEXTURE_WIDTH + 2 * IMAGE_PADDING;
        final int height = lines * ROW_HEIGHT + 2 * IMAGE_PADDING;

        return GuiImageExport.render(width, height, IMAGE_SCALE, IMAGE_BACKGROUND, () -> {
            // The screen's own row of cells, straight out of the window texture, so a saved plan looks like
            // the plan on screen rather than like a drawing of one.
            this.bindTexture("guis/craftingreport.png");
            for (int line = 0; line < lines; line++) {
                this.drawTexturedModalRect(IMAGE_PADDING, IMAGE_PADDING + line * ROW_HEIGHT,
                        ROW_TEXTURE_X, TEXTURE_TOP_HEIGHT, ROW_TEXTURE_WIDTH, ROW_HEIGHT);
            }

            GlStateManager.pushMatrix();
            // The entry draws itself where the screen's own grid would put it, so the offsets that grid
            // starts from have to be taken back out.
            GlStateManager.translate(IMAGE_PADDING - 9, IMAGE_PADDING + CELL_TOP_INSET - 22, 0);

            for (int i = 0; i < entries; i++) {
                this.drawPlanEntry(this.visual.get(i), i % 3, i / 3, null);
            }

            GlStateManager.popMatrix();
        });
    }

    @Nullable
    @Override
    public AEKey getKeyUnderMouse(final int mouseX, final int mouseY) {
        final int index = this.getListSlotUnderMouse(mouseX, mouseY, this.rows);
        return index >= 0 && index < this.displayed.size() ? this.displayed.get(index) : null;
    }

    public List<AEKey> getVisual() {
        return visual;
    }

    public int getDisplayedRows() {
        return this.rows;
    }
}
