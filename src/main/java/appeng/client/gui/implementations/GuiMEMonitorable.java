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

package appeng.client.gui.implementations;


import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.config.ActionItems;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalPinHost;
import appeng.api.storage.IPlayerTerminalPins;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.behaviors.ContainerItemStrategies;
import appeng.container.me.GridInventoryEntry;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.client.ActionKey;
import appeng.client.gui.AEBaseMEGui;
import appeng.client.gui.widgets.*;
import appeng.client.me.InternalSlotME;
import appeng.client.me.ItemRepo;
import appeng.client.me.SlotME;
import appeng.client.me.PinSlotME;
import appeng.client.me.InternalPinSlotME;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.core.sync.packets.PacketTerminalPins;
import appeng.container.implementations.TerminalCraftingPin;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.integration.Integrations;
import appeng.parts.reporting.AbstractPartTerminal;
import appeng.tile.misc.TileSecurityStation;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;
import mezz.jei.api.gui.IGhostIngredientHandler;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import org.lwjgl.opengl.GL11;


public class GuiMEMonitorable extends AEBaseMEGui implements ISortSource, IConfigManagerHost {

    private static int craftingGridOffsetX;
    private static int craftingGridOffsetY;

    private static String memoryText = "";
    protected final ItemRepo repo;
    private final int offsetX = 9;
    private final int lowerTextureOffset = 0;
    private final IConfigManager configSrc;
    private final boolean viewCell;
    private final ItemStack[] myCurrentViewCells = new ItemStack[5];
    private final ContainerMEMonitorable monitorableContainer;
    private GuiTabButton craftingStatusBtn;
    private GuiImgButton keyTypesBtn;
    private MEGuiTextField searchField;
    private GuiText myName;
    private int perRow = 9;
    private int reservedSpace = 0;
    private boolean customSortOrder = true;
    private int rows = 0;
    private int maxRows = Integer.MAX_VALUE;
    private int standardSize;
    private GuiImgButton ViewBox;
    private GuiImgButton SortByBox;
    private GuiImgButton SortDirBox;
    private GuiImgButton searchBoxSettings;
    private GuiImgButton terminalStyleBox;
    private boolean isAutoFocus = false;
    private int currentMouseX = 0;
    private int currentMouseY = 0;
    private boolean delayedUpdate;
    private final boolean supportsKeyTypeSelection;
    private final boolean supportsTerminalPins;
    private GuiPinsButton pinsButton;
    private int craftingPinRows = 1;
    private int playerPinRows;
    private int visibleCraftingPinRows;
    private int visiblePlayerPinRows;
    private int normalRows;
    private int terminalPinSnapshotVersion;
    private final AEKey[] terminalPlayerPins = new AEKey[IPlayerTerminalPins.MAX_PINS];
    private List<TerminalCraftingPin> terminalCraftingPins = new ArrayList<>();

    protected int jeiOffset = Platform.isModLoaded("jei") ? 24 : 0;

    public GuiMEMonitorable(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        this(inventoryPlayer, te, new ContainerMEMonitorable(inventoryPlayer, te));
    }

    public GuiMEMonitorable(final InventoryPlayer inventoryPlayer, final ITerminalHost te, final ContainerMEMonitorable c) {

        super(c);

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
        this.repo = new ItemRepo(scrollbar, this);

        this.xSize = 185;
        this.ySize = 204;

        if (te instanceof IViewCellStorage) {
            this.xSize += 33;
        }

        this.standardSize = this.xSize;

        this.configSrc = ((IConfigurableObject) this.inventorySlots).getConfigManager();
        (this.monitorableContainer = (ContainerMEMonitorable) this.inventorySlots).setGui(this);

        this.viewCell = te instanceof IViewCellStorage;
        this.supportsKeyTypeSelection = te instanceof KeyTypeSelectionHost;
        this.supportsTerminalPins = te instanceof ITerminalPinHost;

        if (te instanceof TileSecurityStation) {
            this.myName = GuiText.Security;
        } else if (te instanceof WirelessTerminalGuiObject) {
            this.myName = GuiText.WirelessTerminal;
        } else if (te instanceof IPortableCell) {
            this.myName = GuiText.PortableCell;
        } else if (te instanceof IMEChest) {
            this.myName = GuiText.Chest;
        } else if (te instanceof AbstractPartTerminal) {
            this.myName = GuiText.Terminal;
        }
    }

    /**
     * The client-side inventory listing. The JEI integration reads it to decide which recipe ingredients
     * the network can supply; it used to read {@code ContainerMEMonitorable.items}, which no longer
     * exists because the listing now lives on the client only.
     */
    public ItemRepo getRepo() {
        return this.repo;
    }

    public void postUpdate(final List<GridInventoryEntry> list) {
        for (final GridInventoryEntry entry : list) {
            this.repo.postUpdate(entry);
        }

        if (isShiftKeyDown()) {
            for (Slot slot : this.inventorySlots.inventorySlots) {
                if (slot instanceof SlotME) {
                    if (this.isPointInRegion(slot.xPos, slot.yPos, 18, 18, currentMouseX, currentMouseY)) {
                        this.delayedUpdate = true;
                        break;
                    }
                }
            }
        }

        if (!this.delayedUpdate) {
            this.repo.updateView();
            this.setScrollBar();
        } else {
            // Shift is held over an ME slot: keep the rows where they are so a burst of shift-clicks stays
            // on one item, but let the counts move. Freezing both is what made a shift-extraction look like
            // it had not happened until the key was released.
            this.repo.refreshViewAmounts();
        }
    }

    private void setScrollBar() {
        int pinRows = this.visibleCraftingPinRows + this.visiblePlayerPinRows;
        this.getScrollBar().setTop(18 + pinRows * 18).setLeft(175).setHeight(this.normalRows * 18 - 2);
        this.getScrollBar().setRange(0, (this.repo.size() + this.perRow - 1) / this.perRow - this.normalRows,
                Math.max(1, this.normalRows / 6));
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        if (btn == this.craftingStatusBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_CRAFTING_STATUS));
        }

        if (btn == this.keyTypesBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_KEY_TYPES));
        }

        if (btn == this.pinsButton) {
            boolean backwards = Mouse.isButtonDown(1);
            boolean crafting = GuiScreen.isCtrlKeyDown();
            int nextCrafting = this.craftingPinRows;
            int nextPlayer = this.playerPinRows;
            int available = Math.max(0, this.rows - 1);
            if (crafting) {
                int visiblePlayer = AEConfig.instance().showPlayerPins() ? nextPlayer : 0;
                nextCrafting = Math.max(0, Math.min(IPlayerTerminalPins.MAX_ROWS,
                        nextCrafting + (backwards ? -1 : 1)));
                nextCrafting = Math.min(nextCrafting, Math.max(0, available - visiblePlayer));
            } else {
                int visibleCrafting = AEConfig.instance().showCraftingPins() ? nextCrafting : 0;
                nextPlayer = Math.max(0, Math.min(IPlayerTerminalPins.MAX_ROWS,
                        nextPlayer + (backwards ? -1 : 1)));
                nextPlayer = Math.min(nextPlayer, Math.max(0, available - visibleCrafting));
            }
            this.setPinRows(nextCrafting, nextPlayer, true);
        }

        if (btn instanceof GuiImgButton iBtn) {
            final boolean backwards = Mouse.isButtonDown(1);

            if (iBtn.getSetting() != Settings.ACTIONS) {
                final Enum cv = iBtn.getCurrentValue();
                final Enum next = Platform.rotateEnum(cv, backwards, iBtn.getSetting().getPossibleValues());

                if (btn == this.terminalStyleBox) {
                    AEConfig.instance().getConfigManager().putSetting(iBtn.getSetting(), next);
                } else if (btn == this.searchBoxSettings) {
                    AEConfig.instance().getConfigManager().putSetting(iBtn.getSetting(), next);
                } else {
                    try {
                        NetworkHandler.instance().sendToServer(new PacketValueConfig(iBtn.getSetting().name(), next.name()));
                    } catch (final IOException e) {
                        AELog.debug(e);
                    }
                }

                iBtn.set(next);

                if (next.getClass() == SearchBoxMode.class || next.getClass() == TerminalStyle.class) {
                    this.reinitalize();
                }
            }
        }
    }

    private void reinitalize() {
        this.buttonList.clear();
        this.initGui();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        PacketTerminalPins.applyPendingSnapshot(this.monitorableContainer);
        this.applyTerminalPinSnapshot(false);

        this.maxRows = this.getMaxRows();
        this.perRow = 9;

        final int magicNumber = 114 + 1;
        final int extraSpace = this.height - magicNumber - this.reservedSpace;

        final TerminalStyle terminalStyle = (TerminalStyle) AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
        this.rows = terminalStyle.getRows((int) Math.floor(extraSpace / 18));
        if (this.rows > this.maxRows) {
            this.rows = this.maxRows;
        }

        if (this.rows < 3) {
            this.rows = 3;
        }

        int maxPinRows = Math.max(0, this.rows - 1);
        this.visibleCraftingPinRows = this.getVisibleCraftingPinRows(maxPinRows);
        this.visiblePlayerPinRows = this.supportsTerminalPins && AEConfig.instance().showPlayerPins()
                ? Math.min(this.playerPinRows, maxPinRows - this.visibleCraftingPinRows) : 0;
        this.normalRows = Math.max(1, this.rows - this.visibleCraftingPinRows - this.visiblePlayerPinRows);

        this.getMeSlots().clear();
        for (int y = 0; y < this.visibleCraftingPinRows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                int index = x + y * this.perRow;
                this.getMeSlots().add(new InternalPinSlotME(this.repo, index, true,
                        this.offsetX + x * 18, 18 + y * 18));
            }
        }
        for (int y = 0; y < this.visiblePlayerPinRows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                int index = x + y * this.perRow;
                this.getMeSlots().add(new InternalPinSlotME(this.repo, index, false,
                        this.offsetX + x * 18, 18 + (this.visibleCraftingPinRows + y) * 18));
            }
        }
        int pinRows = this.visibleCraftingPinRows + this.visiblePlayerPinRows;
        for (int y = 0; y < this.normalRows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                this.getMeSlots().add(new InternalSlotME(this.repo, x + y * this.perRow,
                        this.offsetX + x * 18, 18 + (pinRows + y) * 18));
            }
        }

        this.xSize = this.standardSize;

        super.initGui();
        // full size : 204
        // extra slots : 72
        // slot 18

        this.ySize = magicNumber + this.rows * 18 + this.reservedSpace;
        // this.guiTop = top;
        final int unusedSpace = this.height - this.ySize;
        this.guiTop = (int) Math.floor(unusedSpace / (unusedSpace < 0 ? 3.8f : 2.0f));

        int offset = this.guiTop + 8 + jeiOffset;

        {
            if (this.customSortOrder) {
                this.buttonList
                        .add(this.SortByBox = new GuiImgButton(this.guiLeft - 18, offset, Settings.SORT_BY, this.configSrc.getSetting(Settings.SORT_BY)));
                offset += 20;
            }
        }

        if (this.viewCell || this instanceof GuiWirelessTerm) {
            this.buttonList
                    .add(this.ViewBox = new GuiImgButton(this.guiLeft - 18, offset, Settings.VIEW_MODE, this.configSrc.getSetting(Settings.VIEW_MODE)));
            offset += 20;
        }

        this.buttonList.add(this.SortDirBox = new GuiImgButton(this.guiLeft - 18, offset, Settings.SORT_DIRECTION, this.configSrc
                .getSetting(Settings.SORT_DIRECTION)));
        offset += 20;

        this.buttonList.add(
                this.searchBoxSettings = new GuiImgButton(this.guiLeft - 18, offset, Settings.SEARCH_MODE, AEConfig.instance()
                        .getConfigManager()
                        .getSetting(
                                Settings.SEARCH_MODE)));

        offset += 20;

        if (this.supportsTerminalStyle()) {
            this.buttonList.add(this.terminalStyleBox = new GuiImgButton(this.guiLeft - 18, offset, Settings.TERMINAL_STYLE, AEConfig.instance()
                    .getConfigManager()
                    .getSetting(Settings.TERMINAL_STYLE)));
            offset += 20;
        }

        if (this.supportsKeyTypeSelection) {
            this.buttonList.add(this.keyTypesBtn = new GuiImgButton(this.guiLeft - 18, offset, Settings.ACTIONS,
                    ActionItems.CONFIGURE_VISIBLE_TYPES));
        }


        if (this.supportsTerminalPins && (AEConfig.instance().showCraftingPins() || AEConfig.instance().showPlayerPins())) {
            offset += this.supportsKeyTypeSelection ? 20 : 0;
            this.buttonList.add(this.pinsButton = new GuiPinsButton(this.guiLeft - 18, offset));
            this.pinsButton.setRows(this.craftingPinRows, this.playerPinRows);
        }

        this.searchField = new MEGuiTextField(this.fontRenderer, this.guiLeft + Math.max(80, this.offsetX), this.guiTop + 4, 90, 12);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setMaxStringLength(25);
        this.searchField.setTextColor(0xFFFFFF);
        this.searchField.setSelectionColor(0xFF008000);
        this.searchField.setVisible(true);

        if (this.viewCell || this instanceof GuiWirelessTerm) {
            this.buttonList.add(this.craftingStatusBtn = new GuiTabButton(this.guiLeft + 170, this.guiTop - 4, 2 + 11 * 16, GuiText.CraftingStatus
                    .getLocal(), this.itemRender));
            this.craftingStatusBtn.setHideEdge(13);
        }

        final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);

        this.isAutoFocus = SearchBoxMode.AUTOSEARCH == searchModeSetting || SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting || SearchBoxMode.AUTOSEARCH_KEEP == searchModeSetting || SearchBoxMode.JEI_AUTOSEARCH_KEEP == searchModeSetting;
        final boolean isKeepFilter = SearchBoxMode.AUTOSEARCH_KEEP == searchModeSetting || SearchBoxMode.JEI_AUTOSEARCH_KEEP == searchModeSetting || SearchBoxMode.MANUAL_SEARCH_KEEP == searchModeSetting || SearchBoxMode.JEI_MANUAL_SEARCH_KEEP == searchModeSetting;
        final boolean isJEIEnabled = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;

        this.searchField.setFocused(this.isAutoFocus);

        if (isJEIEnabled) {
            memoryText = Integrations.jei().getSearchText();
        }

        if (isKeepFilter && memoryText != null && !memoryText.isEmpty()) {
            this.searchField.setText(memoryText);
            this.searchField.selectAll();
            this.repo.setSearchString(memoryText);
            this.setScrollBar();
        }

        craftingGridOffsetX = Integer.MAX_VALUE;
        craftingGridOffsetY = Integer.MAX_VALUE;

        for (final Object s : this.inventorySlots.inventorySlots) {
            if (s instanceof AppEngSlot) {
                if (((Slot) s).xPos < 197) {
                    this.repositionSlot((AppEngSlot) s);
                }
            }

            if (s instanceof SlotCraftingMatrix || s instanceof SlotFakeCraftingMatrix) {
                final Slot g = (Slot) s;
                if (g.xPos > 0 && g.yPos > 0) {
                    craftingGridOffsetX = Math.min(craftingGridOffsetX, g.xPos);
                    craftingGridOffsetY = Math.min(craftingGridOffsetY, g.yPos);
                }
            }
        }

        craftingGridOffsetX -= 25;
        craftingGridOffsetY -= 6;

        this.repo.setPins(this.terminalPlayerPins, this.terminalCraftingPins,
                this.visibleCraftingPinRows, this.visiblePlayerPinRows);
    }

    public void applyTerminalPinSnapshot(boolean reinitializeLayout) {
        int version = this.monitorableContainer.getTerminalPinSnapshotVersion();
        if (version == 0 || version == this.terminalPinSnapshotVersion) {
            return;
        }
        this.terminalPinSnapshotVersion = version;
        int craftingRows = this.monitorableContainer.getClientCraftingPinRows();
        int playerRows = this.monitorableContainer.getClientPlayerPinRows();
        AEKey[] playerPins = this.monitorableContainer.getClientPlayerPins();
        List<TerminalCraftingPin> craftingPins = this.monitorableContainer.getClientCraftingPins();
        int oldVisibleCraftingRows = this.visibleCraftingPinRows;
        int oldVisiblePlayerRows = this.visiblePlayerPinRows;
        boolean layoutChanged = this.craftingPinRows != craftingRows || this.playerPinRows != playerRows;
        this.craftingPinRows = craftingRows;
        this.playerPinRows = playerRows;
        Arrays.fill(this.terminalPlayerPins, null);
        System.arraycopy(playerPins, 0, this.terminalPlayerPins, 0,
                Math.min(playerPins.length, this.terminalPlayerPins.length));
        this.terminalCraftingPins = new ArrayList<>(craftingPins);
        int maxPinRows = Math.max(0, this.rows - 1);
        int newVisibleCraftingRows = this.getVisibleCraftingPinRows(maxPinRows);
        int newVisiblePlayerRows = this.supportsTerminalPins && AEConfig.instance().showPlayerPins()
                ? Math.min(this.playerPinRows, maxPinRows - newVisibleCraftingRows) : 0;
        layoutChanged |= oldVisibleCraftingRows != newVisibleCraftingRows
                || oldVisiblePlayerRows != newVisiblePlayerRows;
        if (layoutChanged && reinitializeLayout) {
            this.reinitalize();
        } else if (reinitializeLayout) {
            this.repo.setPins(this.terminalPlayerPins, this.terminalCraftingPins,
                    this.visibleCraftingPinRows, this.visiblePlayerPinRows);
            if (this.pinsButton != null) {
                this.pinsButton.setRows(craftingRows, playerRows);
            }
            this.repo.updateView();
            this.setScrollBar();
        }
    }

    private int getVisibleCraftingPinRows(int maxPinRows) {
        if (!this.supportsTerminalPins || !AEConfig.instance().showCraftingPins()
                || this.terminalCraftingPins.isEmpty()) {
            return 0;
        }
        int rowsNeeded = (this.terminalCraftingPins.size() + this.perRow - 1) / this.perRow;
        return Math.min(Math.min(this.craftingPinRows, maxPinRows), rowsNeeded);
    }

    private void setPinRows(int craftingRows, int playerRows, boolean send) {
        if (this.craftingPinRows == craftingRows && this.playerPinRows == playerRows) {
            return;
        }
        this.craftingPinRows = craftingRows;
        this.playerPinRows = playerRows;
        if (send) {
            NetworkHandler.instance().sendToServer(PacketTerminalPins.setRows(craftingRows, playerRows));
        }
        this.reinitalize();
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> exclusionArea = new ArrayList<>();

        int yOffset = guiTop + 8 + jeiOffset;

        int visibleButtons = (int) this.buttonList.stream().filter(v -> v.enabled && v.x < guiLeft).count();
        Rectangle sortDir = new Rectangle(guiLeft - 18, yOffset, 20, visibleButtons * 20 + visibleButtons - 2);
        exclusionArea.add(sortDir);

        if (this.viewCell) {
            Rectangle viewMode = new Rectangle(guiLeft + 205, yOffset - 4, 24, 19 * monitorableContainer.getViewCells().length);
            exclusionArea.add(viewMode);
        }


        int pinRows = this.visibleCraftingPinRows + this.visiblePlayerPinRows;
        if (pinRows > 0) {
            exclusionArea.add(new Rectangle(guiLeft + this.offsetX - 1, guiTop + 17,
                    this.perRow * 18, pinRows * 18 + 1));
        }

        return exclusionArea;
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(this.myName.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);

        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        this.searchField.mouseClicked(xCoord, yCoord, btn);

        if (btn == 1 && this.searchField.isMouseIn(xCoord, yCoord)) {
            this.searchField.setText("");
            this.repo.setSearchString("");
            this.setScrollBar();
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotIdx, int mouseButton, ClickType clickType) {
        if (slot instanceof PinSlotME && !((PinSlotME) slot).isCraftingPin()) {
            PinSlotME pinSlot = (PinSlotME) slot;
            if (isShiftKeyDown() && mouseButton == 1) {
                this.sendPlayerPin(pinSlot.getPinIndex(), null);
                return;
            }
            GenericStack carried = ContainerItemStrategies.getContainedStack(this.mc.player.inventory.getItemStack());
            if (carried == null) {
                carried = GenericStack.resolveItemStack(this.mc.player.inventory.getItemStack());
            }
            if (carried != null) {
                this.sendPlayerPin(pinSlot.getPinIndex(), carried.what());
                return;
            }
            if (!pinSlot.getHasStack()) {
                return;
            }
        } else if (slot instanceof SlotME && !(slot instanceof PinSlotME)
                && clickType == ClickType.CLONE && isShiftKeyDown()
                && this.supportsTerminalPins && AEConfig.instance().showPlayerPins()) {
            GridInventoryEntry entry = ((SlotME) slot).getEntry();
            if (entry != null) {
                this.togglePlayerPin(entry.getWhat());
                return;
            }
        }
        super.handleMouseClick(slot, slotIdx, mouseButton, clickType);
    }

    private void togglePlayerPin(AEKey key) {
        for (int i = 0; i < this.terminalPlayerPins.length; i++) {
            if (key.equals(this.terminalPlayerPins[i])) {
                this.sendPlayerPin(i, null);
                return;
            }
        }

        int rows = this.playerPinRows;
        int availableRows = Math.max(0, this.rows - 1 - this.visibleCraftingPinRows);
        if (rows == 0) {
            if (availableRows > 0) {
                rows = 1;
                this.setPinRows(this.craftingPinRows, rows, true);
            }
        }
        int capacity = Math.min(this.terminalPlayerPins.length,
                this.visiblePlayerPinRows * IPlayerTerminalPins.SLOTS_PER_ROW);
        for (int i = 0; i < capacity; i++) {
            if (this.terminalPlayerPins[i] == null) {
                this.sendPlayerPin(i, key);
                return;
            }
        }

        if (rows < IPlayerTerminalPins.MAX_ROWS && this.visiblePlayerPinRows == rows && rows < availableRows) {
            rows++;
            this.setPinRows(this.craftingPinRows, rows, true);
            this.sendPlayerPin((rows - 1) * IPlayerTerminalPins.SLOTS_PER_ROW, key);
            return;
        }

        this.mc.player.sendStatusMessage(new TextComponentTranslation("gui.appliedenergistics2.playerPinSectionFull"), true);
        this.mc.world.playSound(this.mc.player, this.mc.player.getPosition(), SoundEvents.BLOCK_NOTE_BASS,
                SoundCategory.PLAYERS, 0.25F, 0.7F);
    }

    private void sendPlayerPin(int slot, AEKey key) {
        try {
            this.terminalPlayerPins[slot] = key;
            this.repo.setPins(this.terminalPlayerPins, this.terminalCraftingPins,
                    this.visibleCraftingPinRows, this.visiblePlayerPinRows);
            NetworkHandler.instance().sendToServer(PacketTerminalPins.setPin(slot, key));
        } catch (IOException e) {
            AELog.debug(e);
        }
    }

    public List<IGhostIngredientHandler.Target<?>> getPinGhostTargets(Object ingredient) {
        final AEKey key;
        if (ingredient instanceof ItemStack) {
            GenericStack stack = GenericStack.resolveItemStack((ItemStack) ingredient);
            key = stack == null ? null : stack.what();
        } else if (ingredient instanceof FluidStack && ((FluidStack) ingredient).amount > 0) {
            key = AEFluidKey.of((FluidStack) ingredient);
        } else {
            key = null;
        }
        if (key == null || this.visiblePlayerPinRows == 0) {
            return java.util.Collections.emptyList();
        }

        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        int count = this.visiblePlayerPinRows * this.perRow;
        for (int i = 0; i < count; i++) {
            final int pinIndex = i;
            final int x = this.guiLeft + this.offsetX + (i % this.perRow) * 18;
            final int y = this.guiTop + 18 + (this.visibleCraftingPinRows + i / this.perRow) * 18;
            targets.add(new IGhostIngredientHandler.Target<Object>() {
                @Override
                public Rectangle getArea() {
                    return new Rectangle(x, y, 16, 16);
                }

                @Override
                public void accept(Object ignored) {
                    sendPlayerPin(pinIndex, key);
                }
            });
        }
        return targets;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        memoryText = this.searchField.getText();
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {

        this.bindTexture(this.getBackground());
        final int x_width = 197;
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, x_width, 18);

        if (this.viewCell || (this instanceof GuiSecurityStation)) {
            this.drawTexturedModalRect(offsetX + x_width, offsetY + jeiOffset, x_width, 0, 46, 128);
        }

        for (int x = 0; x < this.rows; x++) {
            this.drawTexturedModalRect(offsetX, offsetY + 18 + x * 18, 0, 18, x_width, 18);
        }

        this.drawTexturedModalRect(offsetX, offsetY + 16 + this.rows * 18 + this.lowerTextureOffset, 0, 106 - 18 - 18, x_width,
                99 + this.reservedSpace - this.lowerTextureOffset);

        this.drawPinDecorations(offsetX, offsetY);

        if (this.viewCell) {
            boolean update = false;

            for (int i = 0; i < 5; i++) {
                if (this.myCurrentViewCells[i] != this.monitorableContainer.getCellViewSlot(i).getStack()) {
                    update = true;
                    this.myCurrentViewCells[i] = this.monitorableContainer.getCellViewSlot(i).getStack();
                }
            }

            if (update) {
                this.repo.setViewCell(this.myCurrentViewCells);
            }
        }

        if (this.searchField != null) {
            this.searchField.drawTextBox();
        }
    }

    private void drawPinDecorations(int offsetX, int offsetY) {
        int craftingSlots = this.visibleCraftingPinRows * this.perRow;
        int playerSlots = this.visiblePlayerPinRows * this.perRow;
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.enableBlend();
        this.bindTexture("guis/states.png");
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.4F);
        for (int i = 0; i < craftingSlots + playerSlots; i++) {
            int row = i / this.perRow;
            int x = offsetX + this.offsetX + (i % this.perRow) * 18;
            int y = offsetY + 18 + row * 18;
            this.drawTexturedModalRect(x, y, 14 * 16, 5 * 16, 16, 16);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        for (int i = 0; i < craftingSlots; i++) {
            TerminalCraftingPin status = this.repo.getCraftingPinStatus(i);
            if (status != null && status.isActive()) {
                int x = offsetX + this.offsetX + (i % this.perRow) * 18 - 1;
                int y = offsetY + 18 + (i / this.perRow) * 18 - 1;
                this.drawCraftingAnimation(x, y);
            }
        }
        if (!blendWasEnabled) {
            GlStateManager.disableBlend();
        }
    }

    private void drawCraftingAnimation(int x, int y) {
        TextureAtlasSprite sprite = this.mc.getTextureMapBlocks()
                .getAtlasSprite(AppEng.MOD_ID + ":blocks/molecular_assembler_lights");
        this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        double minU = sprite.getInterpolatedU(2);
        double maxU = sprite.getInterpolatedU(14);
        double minV = sprite.getInterpolatedV(2);
        double maxV = sprite.getInterpolatedV(14);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + 18, this.zLevel).tex(minU, maxV).endVertex();
        buffer.pos(x + 18, y + 18, this.zLevel).tex(maxU, maxV).endVertex();
        buffer.pos(x + 18, y, this.zLevel).tex(maxU, minV).endVertex();
        buffer.pos(x, y, this.zLevel).tex(minU, minV).endVertex();
        Tessellator.getInstance().draw();
    }

    protected String getBackground() {
        return "guis/terminal.png";
    }

    @Override
    protected boolean isPowered() {
        return this.repo.hasPower();
    }

    protected int getMaxRows() {
        return Integer.MAX_VALUE;
    }

    protected boolean supportsTerminalStyle() {
        return true;
    }

    protected void repositionSlot(final AppEngSlot s) {
        s.yPos = s.getY() + this.ySize - 78 - 5;
    }

    @Override
    public boolean isTextFieldFocused() {
        return this.searchField != null && this.searchField.isFocused();
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {

        if (!this.checkHotbarKeys(key)) {
            if (AppEng.proxy.isActionKey(ActionKey.TOGGLE_FOCUS, key)) {
                this.searchField.setFocused(!this.searchField.isFocused());
                return;
            }

            if (this.searchField.isFocused() && key == Keyboard.KEY_RETURN) {
                this.searchField.setFocused(false);
                return;
            }

            if (character == ' ' && this.searchField.getText().isEmpty()) {
                return;
            }

            final boolean mouseInGui = this.isPointInRegion(0, 0, this.xSize, this.ySize, this.currentMouseX, this.currentMouseY);
            final boolean wasSearchFieldFocused = this.searchField.isFocused();

            if (this.isAutoFocus && !this.searchField.isFocused() && mouseInGui) {
                this.searchField.setFocused(true);
            }

            if (this.searchField.textboxKeyTyped(character, key)) {
                this.repo.setSearchString(this.searchField.getText());
                this.setScrollBar();
                // tell forge the key event is handled and should not be sent out
                this.keyHandled = mouseInGui;
            } else {
                if (!wasSearchFieldFocused) {
                    // prevent unhandled keys (like shift) from focusing the search field
                    searchField.setFocused(false);
                }
                super.keyTyped(character, key);
            }
        }
    }

    @Override
    public void updateScreen() {
        this.repo.setPower(this.monitorableContainer.isPowered());
        if (this.delayedUpdate) {
            if (isShiftKeyDown()) {
                this.delayedUpdate = false;
                for (Slot slot : this.inventorySlots.inventorySlots) {
                    if (slot instanceof SlotME) {
                        if (this.isPointInRegion(slot.xPos, slot.yPos, 18, 18, currentMouseX, currentMouseY)) {
                            this.delayedUpdate = true;
                            break;
                        }
                    }
                }
            } else {
                this.delayedUpdate = false;
            }
        }
        if (!this.delayedUpdate) {
            this.repo.updateView();
            this.setScrollBar();
        } else {
            this.repo.refreshViewAmounts();
        }
        super.updateScreen();
    }

    @Override
    public Enum getSortBy() {
        return this.configSrc.getSetting(Settings.SORT_BY);
    }

    @Override
    public Enum getSortDir() {
        return this.configSrc.getSetting(Settings.SORT_DIRECTION);
    }

    @Override
    public Enum getSortDisplay() {
        return this.configSrc.getSetting(Settings.VIEW_MODE);
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (this.SortByBox != null) {
            this.SortByBox.set(this.configSrc.getSetting(Settings.SORT_BY));
        }

        if (this.SortDirBox != null) {
            this.SortDirBox.set(this.configSrc.getSetting(Settings.SORT_DIRECTION));
        }

        if (this.ViewBox != null) {
            this.ViewBox.set(this.configSrc.getSetting(Settings.VIEW_MODE));
        }

        this.repo.updateView();
    }

    int getReservedSpace() {
        return this.reservedSpace;
    }

    void setReservedSpace(final int reservedSpace) {
        this.reservedSpace = reservedSpace;
    }

    public boolean isCustomSortOrder() {
        return this.customSortOrder;
    }

    void setCustomSortOrder(final boolean customSortOrder) {
        this.customSortOrder = customSortOrder;
    }

    public int getStandardSize() {
        return this.standardSize;
    }

    void setStandardSize(final int standardSize) {
        this.standardSize = standardSize;
    }
}
