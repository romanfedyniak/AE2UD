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
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.KeySearchTarget;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTooltipTextField;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.SlotDisconnected;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.container.implementations.ContainerWirelessInterfaceTerminal;
import appeng.container.slot.AppEngSlot;
import appeng.core.AEClientConfig;
import appeng.core.AppEng;
import appeng.core.localization.ButtonToolTips;
import appeng.client.me.search.RepoSearch;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKey;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.DualityInterface;
import appeng.helpers.PatternHelper;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.util.BlockPosUtils;
import appeng.util.Platform;
import com.google.common.collect.HashMultimap;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

import static appeng.client.render.BlockPosHighlighter.hilightBlock;
import static appeng.client.render.BlockPosHighlighter.turnPlayerTowards;
import static appeng.helpers.ItemStackHelper.stackFromNBT;

public class GuiInterfaceTerminal extends AEBaseGui {

    protected static final int OFFSET_X = 21;
    protected final GuiText guiTitle;
    private static final int MAGIC_HEIGHT_NUMBER = 52 + 99;
    private static final String MOLECULAR_ASSEMBLER = "tile.appliedenergistics2.molecular_assembler";

    private final boolean jeiEnabled;
    private final int jeiButtonPadding;

    private final HashMap<Long, ClientDCInternalInv> byId = new HashMap<>();
    private final HashMultimap<String, ClientDCInternalInv> byName = HashMultimap.create();
    private final HashMap<ClientDCInternalInv, BlockPos> blockPosHashMap = new HashMap<>();
    private final HashMap<GuiButton, ClientDCInternalInv> guiButtonHashMap = new HashMap<>();
    private final Map<ClientDCInternalInv, Integer> numUpgradesMap = new HashMap<>();
    private final Set<ClientDCInternalInv> fakeCrafting = new HashSet<>();
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<Object> lines = new ArrayList<>();
    private final Set<Object> matchedStacks = new HashSet<>();
    private final Map<ClientDCInternalInv, Integer> dimHashMap = new HashMap<>();

    private final MEGuiTooltipTextField searchFieldOutputs;
    private final MEGuiTooltipTextField searchFieldInputs;
    private final MEGuiTooltipTextField searchFieldNames;

    /** The two boxes that ask about items read the terminals' grammar; the third is a plain name. */
    private final RepoSearch inputSearch = new RepoSearch();
    private final RepoSearch outputSearch = new RepoSearch();

    private GuiImgButton searchKeepBtn;

    /** Where the three searches survive closing the screen, while the keep setting says they should. */
    private static String[] memoryText = { "", "", "" };

    private final GuiImgButton guiButtonHideFull;
    private final GuiImgButton guiButtonAssemblersOnly;
    private final GuiImgButton guiButtonBrokenRecipes;
    private final GuiImgButton terminalStyleBox;

    private boolean refreshList = false;

    /* These are worded so that the intended default is false */
    private boolean onlyShowWithSpace = false;
    private boolean onlyMolecularAssemblers = false;
    private boolean onlyBrokenRecipes = false;
    private int rows = 6;

    public GuiInterfaceTerminal(final InventoryPlayer inventoryPlayer, final PartInterfaceTerminal te) {
        super(new ContainerInterfaceTerminal(inventoryPlayer, te));

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
        this.xSize = 208;
        this.ySize = 255;
        this.jeiEnabled = Platform.isModLoaded("jei");
        this.jeiButtonPadding = jeiEnabled ? 22 : 0;

        searchFieldInputs = createTextField(86, 12,
                () -> RepoSearch.syntaxTooltip(ButtonToolTips.SearchFieldInputs.getLocal()));
        searchFieldOutputs = createTextField(86, 12,
                () -> RepoSearch.syntaxTooltip(ButtonToolTips.SearchFieldOutputs.getLocal()));
        searchFieldNames = createTextField(71, 12, () -> ButtonToolTips.SearchFieldNames.getLocal());
        searchFieldNames.setFocused(true);

        guiButtonAssemblersOnly = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonHideFull = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonBrokenRecipes = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        terminalStyleBox = new GuiImgButton(0, 0, Settings.TERMINAL_STYLE, null);
        searchKeepBtn = new GuiImgButton(0, 0, Settings.SEARCH_KEEP,
                AEClientConfig.instance().getConfigManager().getSetting(Settings.SEARCH_KEEP));
        restoreSearch();
        guiTitle = GuiText.InterfaceTerminal;
    }

    public GuiInterfaceTerminal(final InventoryPlayer inventoryPlayer, final WirelessTerminalGuiObject guiObject) {
        super(new ContainerWirelessInterfaceTerminal(inventoryPlayer, guiObject));

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
        this.xSize = 208;
        this.ySize = 255;
        this.jeiEnabled = Platform.isModLoaded("jei");
        this.jeiButtonPadding = jeiEnabled ? 22 : 0;

        searchFieldInputs = createTextField(86, 12,
                () -> RepoSearch.syntaxTooltip(ButtonToolTips.SearchFieldInputs.getLocal()));
        searchFieldOutputs = createTextField(86, 12,
                () -> RepoSearch.syntaxTooltip(ButtonToolTips.SearchFieldOutputs.getLocal()));
        searchFieldNames = createTextField(71, 12, () -> ButtonToolTips.SearchFieldNames.getLocal());
        searchFieldNames.setFocused(true);

        guiButtonAssemblersOnly = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonHideFull = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonBrokenRecipes = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        terminalStyleBox = new GuiImgButton(0, 0, Settings.TERMINAL_STYLE, null);
        searchKeepBtn = new GuiImgButton(0, 0, Settings.SEARCH_KEEP,
                AEClientConfig.instance().getConfigManager().getSetting(Settings.SEARCH_KEEP));
        restoreSearch();
        guiTitle = GuiText.WirelessTerminal;
    }

    /** The three searches, as they were left last time, when the setting says to bring them back. */
    private void restoreSearch() {
        if (!AEClientConfig.instance().keepsSearch()) {
            return;
        }
        this.searchFieldInputs.setText(memoryText[0], true);
        this.searchFieldOutputs.setText(memoryText[1], true);
        this.searchFieldNames.setText(memoryText[2], true);
    }

    @Override
    public void onGuiClosed() {
        final boolean keep = AEClientConfig.instance().keepsSearch();
        memoryText[0] = keep ? this.searchFieldInputs.getText() : "";
        memoryText[1] = keep ? this.searchFieldOutputs.getText() : "";
        memoryText[2] = keep ? this.searchFieldNames.getText() : "";
        super.onGuiClosed();
    }

    private MEGuiTooltipTextField createTextField(final int width, final int height,
            final Supplier<String> tooltip) {
        MEGuiTooltipTextField textField = new MEGuiTooltipTextField(width, height, tooltip) {
            @Override
            public void onTextChange(String oldText) {
                refreshList();
            }
        };
        textField.setEnableBackgroundDrawing(false);
        textField.setMaxStringLength(100);
        textField.setTextColor(0xFFFFFF);
        textField.setCursorPositionZero();
        return textField;
    }

    private void setScrollBar() {
        this.getScrollBar().setTop(52).setLeft(189).setHeight(this.rows * 18 - 2);
        this.getScrollBar().setRange(0, this.lines.size() - 1, 1);
    }

    private int calculateRowsCount() {
        final int jeiPadding = jeiEnabled ? 22 + 18 : 0;
        final int extraSpace = this.height - MAGIC_HEIGHT_NUMBER - jeiPadding;
        final int availableRows = extraSpace / 18;
        final TerminalStyle style = (TerminalStyle) AEClientConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);

        return Math.max(6, style.getRows(availableRows));
    }

    @Override
    public void initGui() {
        this.rows = calculateRowsCount();

        super.initGui();

        this.ySize = MAGIC_HEIGHT_NUMBER + this.rows * 18;
        final int unusedSpace = this.height - this.ySize;
        this.guiTop = (int) Math.floor(unusedSpace / (unusedSpace < 0 ? 3.8f : 2.0f));

        searchFieldInputs.x = guiLeft + 32;
        searchFieldInputs.y = guiTop + 25;
        searchFieldOutputs.x = guiLeft + 32;
        searchFieldOutputs.y = guiTop + 38;
        searchFieldNames.x = guiLeft + 32 + 99;
        searchFieldNames.y = guiTop + 38;

        terminalStyleBox.x = guiLeft - 18;
        terminalStyleBox.y = guiTop + 8 + jeiButtonPadding;
        guiButtonBrokenRecipes.x = guiLeft - 18;
        guiButtonBrokenRecipes.y = terminalStyleBox.y + 20;
        guiButtonHideFull.x = guiLeft - 18;
        guiButtonHideFull.y = guiButtonBrokenRecipes.y + 20;
        guiButtonAssemblersOnly.x = guiLeft - 18;
        guiButtonAssemblersOnly.y = guiButtonHideFull.y + 20;
        searchKeepBtn.x = guiLeft - 18;
        searchKeepBtn.y = guiButtonAssemblersOnly.y + 20;

        this.setScrollBar();
        this.repositionSlots();
    }

    protected void repositionSlots() {
        for (final Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot slot) {
                slot.yPos = this.ySize + slot.getY() - 78 - 7;
                slot.xPos = slot.getX() + 14;
            }
        }
    }

    /**
     * The two boxes that ask about items. The third asks about an interface's name, which is not a key and
     * has nothing to gain from being handed one.
     */
    @Override
    public List<KeySearchTarget> getKeySearchTargets() {
        final List<KeySearchTarget> boxes = new ArrayList<>(2);
        boxes.add(searchBox(this.searchFieldInputs));
        boxes.add(searchBox(this.searchFieldOutputs));
        return boxes;
    }

    private static KeySearchTarget searchBox(final MEGuiTooltipTextField field) {
        return new KeySearchTarget(new Rectangle(field.x, field.y, field.w, field.h),
                what -> field.setText(RepoSearch.termFor(what)));
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> area = new ArrayList<>(4);
        addButtonArea(area, this.terminalStyleBox);
        addButtonArea(area, this.guiButtonBrokenRecipes);
        addButtonArea(area, this.guiButtonHideFull);
        addButtonArea(area, this.guiButtonAssemblersOnly);
        addButtonArea(area, this.searchKeepBtn);
        return area;
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(guiTitle.getLocal()), OFFSET_X + 2, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), OFFSET_X + 2, this.ySize - 96, 4210752);

        final int currentScroll = this.getScrollBar().getCurrentScroll();

        int offset = 51;
        int linesDraw = 0;
        for (int x = 0; x < rows && linesDraw < rows && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {

                final int extraLines = numUpgradesMap.get(inv);
                final boolean fake = this.fakeCrafting.contains(inv);
                for (int row = 0; row < 1 + extraLines && linesDraw < rows; ++row) {
                    // The card sits in the interface, not in any one pattern, so the whole row carries it.
                    if (fake) {
                        drawRect(22, offset, 22 + 9 * 18, offset + 18, 0x30FF9000);
                    }
                    for (int z = 0; z < 9; z++) {
                        if (this.matchedStacks.contains(inv.getInventory().getStackInSlot(z + (row * 9)))) {
                            drawRect(z * 18 + 22, 1 + offset, z * 18 + 22 + 16, 1 + offset + 16, 0x2A00FF00);
                        }
                    }
                    linesDraw++;
                    offset += 18;
                }
            } else if (lineObj instanceof String name) {
                final int rows = this.byName.get(name).size();
                final ItemStack icon = this.byName.get(name).stream()
                        .map(ClientDCInternalInv::getIcon)
                        .filter(stack -> !stack.isEmpty())
                        .findFirst()
                        .orElse(ItemStack.EMPTY);
                if (rows > 1) {
                    name = name + " (" + rows + ')';
                }

                final int nameX = OFFSET_X + 3 + (icon.isEmpty() ? 0 : 18);

                while (name.length() > 2 && this.fontRenderer.getStringWidth(name) > 158 - (nameX - OFFSET_X - 3)) {
                    name = name.substring(0, name.length() - 1);
                }

                if (!icon.isEmpty()) {
                    this.drawItem(OFFSET_X + 3, 1 + offset, icon);
                }
                this.fontRenderer.drawString(name, nameX, 6 + offset, 4210752);
                linesDraw++;
                offset += 18;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        buttonList.clear();
        guiButtonHashMap.clear();
        inventorySlots.inventorySlots.removeIf(slot -> slot instanceof SlotDisconnected);

        guiButtonAssemblersOnly.set(onlyMolecularAssemblers ? ActionItems.MOLECULAR_ASSEMBLERS_ON : ActionItems.MOLECULAR_ASSEMBLERS_OFF);
        guiButtonHideFull.set(onlyShowWithSpace ? ActionItems.TOGGLE_SHOW_FULL_INTERFACES_OFF : ActionItems.TOGGLE_SHOW_FULL_INTERFACES_ON);
        guiButtonBrokenRecipes.set(onlyBrokenRecipes ? ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_ON : ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_OFF);
        terminalStyleBox.set(AEClientConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE));

        buttonList.add(guiButtonAssemblersOnly);
        buttonList.add(guiButtonHideFull);
        buttonList.add(guiButtonBrokenRecipes);
        buttonList.add(terminalStyleBox);
        buttonList.add(searchKeepBtn);

        this.addExtraButtons();

        int offset = 51;
        final int currentScroll = this.getScrollBar().getCurrentScroll();
        int linesDraw = 0;

        for (int x = 0; x < rows && linesDraw < rows && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {

                GuiButton guiButton = new GuiImgButton(guiLeft + 4, guiTop + offset + 1, Settings.ACTIONS, ActionItems.HIGHLIGHT_INTERFACE);
                guiButtonHashMap.put(guiButton, inv);
                this.buttonList.add(guiButton);

                final int extraLines = numUpgradesMap.get(inv);
                for (int row = 0; row < 1 + extraLines && linesDraw < rows; ++row) {
                    for (int z = 0; z < 9; z++) {
                        this.inventorySlots.inventorySlots.add(new SlotDisconnected(inv, z + (row * 9), z * 18 + 22, 1+ offset));
                    }
                    linesDraw++;
                    offset += 18;
                }

            } else if (lineObj instanceof String) {
                linesDraw++;
                offset += 18;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        drawTooltip(searchFieldInputs, mouseX, mouseY);
        drawTooltip(searchFieldOutputs, mouseX, mouseY);
        drawTooltip(searchFieldNames, mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        this.searchFieldInputs.mouseClicked(xCoord, yCoord, btn);
        this.searchFieldOutputs.mouseClicked(xCoord, yCoord, btn);
        this.searchFieldNames.mouseClicked(xCoord, yCoord, btn);

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (this.toggleSearchKeep(btn, this.searchKeepBtn)) {
            return;
        }

        if (guiButtonHashMap.containsKey(btn)) {
            BlockPos blockPos = blockPosHashMap.get(guiButtonHashMap.get(this.selectedButton));
            BlockPos blockPos2 = mc.player.getPosition();
            int playerDim = mc.world.provider.getDimension();
            int interfaceDim = dimHashMap.get(guiButtonHashMap.get(this.selectedButton));
            if (playerDim != interfaceDim) {
                try {
                    mc.player.sendStatusMessage(PlayerMessages.InterfaceInOtherDimParam.get(interfaceDim, DimensionManager.getWorld(interfaceDim).provider.getDimensionType().getName()), false);
                } catch (Exception e) {
                    mc.player.sendStatusMessage(PlayerMessages.InterfaceInOtherDim.get(), false);
                }
            } else {
                hilightBlock(blockPos, System.currentTimeMillis() + 500 * BlockPosUtils.getDistance(blockPos, blockPos2), playerDim);
                turnPlayerTowards(blockPos);
                mc.player.sendStatusMessage(PlayerMessages.InterfaceHighlighted.get(blockPos.getX(), blockPos.getY(), blockPos.getZ()), false);
            }
            mc.player.closeScreen();
        } else if (btn == guiButtonHideFull) {
            onlyShowWithSpace = !onlyShowWithSpace;
            this.refreshList();
        } else if (btn == guiButtonAssemblersOnly) {
            onlyMolecularAssemblers = !onlyMolecularAssemblers;
            this.refreshList();
        } else if (btn == guiButtonBrokenRecipes) {
            onlyBrokenRecipes = !onlyBrokenRecipes;
            this.refreshList();
        } else if (btn instanceof GuiImgButton iBtn) {
            if (iBtn.getSetting() != Settings.ACTIONS) {
                final Enum<?> cv = iBtn.getCurrentValue();
                final boolean backwards = Mouse.isButtonDown(1);
                final Enum<?> next = Platform.rotateEnum(cv, backwards, iBtn.getSetting().getPossibleValues());

                if (btn == this.terminalStyleBox) {
                    AEClientConfig.instance().getConfigManager().putSetting(iBtn.getSetting(), next);
                    this.reinitalize();
                }
                iBtn.set(next);
            }
        }
    }

    private void reinitalize() {
        this.buttonList.clear();
        this.initGui();
    }

    /**
     * Anything a subclass draws beside this window. The button list is emptied and refilled on every frame,
     * so whatever is added here has to be added again on each of them.
     */
    protected void addExtraButtons() {
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/newinterfaceterminal.png");

        // draw the top portion of the background, above the interface list
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, 53);

        for (int x = 0; x < this.rows; x++) {
            // draw the background of the rows in the interface list
            this.drawTexturedModalRect(offsetX, offsetY + 53 + x * 18, 0, 52, this.xSize, 18);
        }

        int offset = 51;
        final int ex = this.getScrollBar().getCurrentScroll();
        int linesDraw = 0;
        for (int x = 0; x < this.rows && linesDraw < rows && ex + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(ex + x);
            if (lineObj instanceof ClientDCInternalInv) {
                GlStateManager.color(1, 1, 1, 1);

                final int width = 9 * 18;
                final int extraLines = numUpgradesMap.get(lineObj);

                // draw the slot backgrounds
                for (int row = 0; row < 1 + extraLines && linesDraw < rows; ++row) {
                    this.drawTexturedModalRect(offsetX + 20, offsetY + offset, 20, 173, width, 18);

                    offset += 18;
                    linesDraw++;
                }
            } else {
                offset += 18;
                linesDraw++;
            }
        }

        // draw the background below the interface list
        this.drawTexturedModalRect(offsetX, offsetY + 50 + this.rows * 18, 0, 158, this.xSize, 99);

        // draw the text boxes
        // One list answers all three fields, so all three redden together - naming one of them as the
        // culprit would be a guess.
        final boolean matched = !this.lines.isEmpty()
                || (this.searchFieldInputs.getText().isEmpty()
                        && this.searchFieldOutputs.getText().isEmpty()
                        && this.searchFieldNames.getText().isEmpty());
        this.searchFieldInputs.setMatched(matched);
        this.searchFieldOutputs.setMatched(matched);
        this.searchFieldNames.setMatched(matched);
        this.searchFieldInputs.drawTextBox();
        this.searchFieldOutputs.drawTextBox();
        this.searchFieldNames.drawTextBox();
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (character == ' ') {
                if ((this.searchFieldInputs.getText().isEmpty() && this.searchFieldInputs.isFocused())
                        || (this.searchFieldOutputs.getText().isEmpty() && this.searchFieldOutputs.isFocused())
                        || (this.searchFieldNames.getText().isEmpty() && this.searchFieldNames.isFocused())) {
                    return;
                }
            } else if (character == '\t') {
                if (handleTab()) {
                    return;
                }
            }

            if (this.searchFieldInputs.textboxKeyTyped(character, key)
                    || this.searchFieldOutputs.textboxKeyTyped(character, key)
                    || this.searchFieldNames.textboxKeyTyped(character, key)) {
                this.refreshList();
            } else {
                super.keyTyped(character, key);
            }
        }
    }

    /** Cycle to the next search bar if tab is pressed, going in reverse if shift is held. */
    private boolean handleTab() {
        if (searchFieldInputs.isFocused()) {
            searchFieldInputs.setFocused(false);
            if (isShiftKeyDown()) searchFieldNames.setFocused(true);
            else searchFieldOutputs.setFocused(true);
            return true;
        } else if (searchFieldOutputs.isFocused()) {
            searchFieldOutputs.setFocused(false);
            if (isShiftKeyDown()) searchFieldInputs.setFocused(true);
            else searchFieldNames.setFocused(true);
            return true;
        } else if (searchFieldNames.isFocused()) {
            searchFieldNames.setFocused(false);
            if (isShiftKeyDown()) searchFieldOutputs.setFocused(true);
            else searchFieldInputs.setFocused(true);
            return true;
        }
        return false;
    }

    public void postUpdate(final NBTTagCompound in) {
        if (in.getBoolean("clear")) {
            this.byId.clear();
            this.refreshList = true;
        }

        for (final Object oKey : in.getKeySet()) {
            final String key = (String) oKey;
            if (key.startsWith("=")) {
                try {
                    final long id = Long.parseLong(key.substring(1), Character.MAX_RADIX);
                    final NBTTagCompound invData = in.getCompoundTag(key);
                    final ClientDCInternalInv current = this.getById(id, invData.getLong("sortBy"), invData.getString("un"));
                    current.setIcon(invData.hasKey("icon")
                            ? new ItemStack(invData.getCompoundTag("icon"))
                            : ItemStack.EMPTY);
                    blockPosHashMap.put(current, NBTUtil.getPosFromTag(invData.getCompoundTag("pos")));
                    dimHashMap.put(current, invData.getInteger("dim"));
                    numUpgradesMap.put(current, invData.getInteger("numUpgrades"));
                    if (invData.getBoolean("fake")) {
                        fakeCrafting.add(current);
                    } else {
                        fakeCrafting.remove(current);
                    }

                    for (int x = 0; x < current.getInventory().getSlots(); x++) {
                        final String which = Integer.toString(x);
                        if (invData.hasKey(which)) {
                            current.getInventory().setStackInSlot(x, stackFromNBT(invData.getCompoundTag(which)));
                        }
                    }
                } catch (final NumberFormatException ignored) {
                }
            }
        }

        if (this.refreshList) {
            this.refreshList = false;
            this.refreshList();
        }
    }

    /**
     * Rebuilds the list of interfaces.
     * <p>
     * Respects a search term if present (ignores case) and adding only matching patterns.
     */
    private void refreshList() {
        this.byName.clear();
        this.buttonList.clear();
        this.matchedStacks.clear();

        final String inputQuery = this.searchFieldInputs.getText();
        final String outputQuery = this.searchFieldOutputs.getText();
        final String nameQuery = this.searchFieldNames.getText().toLowerCase();

        this.inputSearch.setSearchString(inputQuery);
        this.inputSearch.refresh();
        this.outputSearch.setSearchString(outputQuery);
        this.outputSearch.refresh();

        for (final ClientDCInternalInv entry : this.byId.values()) {
            // Shortcut to skip any filter if search term is ""/empty

            boolean found = inputQuery.isEmpty() && outputQuery.isEmpty();
            boolean interfaceHasFreeSlots = false;
            boolean interfaceHasBrokenRecipes = false;

            // Search if the current inventory holds a pattern containing the search term.
            if (!found || onlyShowWithSpace || onlyBrokenRecipes) {
                int slot = 0;
                for (final ItemStack itemStack : entry.getInventory()) {
                    if (slot > 8 + numUpgradesMap.get(entry) * 9) {
                        break;
                    }

                    if (itemStack.isEmpty()) {
                        interfaceHasFreeSlots = true;
                    }

                    if (onlyBrokenRecipes && recipeIsBroken(itemStack)) {
                        interfaceHasBrokenRecipes = true;
                    }

                    final boolean byInput = !inputQuery.isEmpty()
                            && patternMatches(itemStack, this.inputSearch, "in", inputQuery);
                    final boolean byOutput = !outputQuery.isEmpty()
                            && patternMatches(itemStack, this.outputSearch, "out", outputQuery);

                    if (byInput || byOutput) {
                        found = true;
                        // A pattern that qualified only by lacking something is not a pattern to point at.
                        if ((byInput && this.inputSearch.hasPositiveTerms())
                                || (byOutput && this.outputSearch.hasPositiveTerms())) {
                            matchedStacks.add(itemStack);
                        }
                    }

                    slot++;
                }
            }

            // Exit if not found
            if (!found) {
                continue;
            }
            // Exit if the interface does not match the name search
            if (!entry.getName().toLowerCase().contains(nameQuery)) {
                continue;
            }
            // Exit if molecular assembler filter is on and this is not a molecular assembler
            // Forge documantation said unlocalized name shouldn't be use for logic, so we might need a better way......
            if (onlyMolecularAssemblers && !entry.getUnlocalizedName().equals(MOLECULAR_ASSEMBLER)) {
                continue;
            }
            // Exit if we are only showing interfaces with free slots and there are none free in this interface
            if (onlyShowWithSpace && !interfaceHasFreeSlots) {
                continue;
            }
            // Exit if we are only showing interfaces with broken patterns and there are no broken patterns in this interface
            if (onlyBrokenRecipes && !interfaceHasBrokenRecipes) {
                continue;
            }

            // Successful search
            this.byName.put(entry.getName(), entry);
        }

        this.names.clear();
        this.names.addAll(this.byName.keySet());
        Collections.sort(this.names);

        this.lines.clear();
        this.lines.ensureCapacity(this.names.size() + this.byId.size());

        for (final String n : this.names) {
            this.lines.add(n);
            final ArrayList<ClientDCInternalInv> clientInventories = new ArrayList<>(this.byName.get(n));
            Collections.sort(clientInventories);
            this.lines.addAll(clientInventories);
        }

        this.setScrollBar();
    }

    private boolean recipeIsBroken(final ItemStack stack) {
        if (stack == null) return false;
        if (stack.isEmpty()) return false;

        final NBTTagCompound encodedValue = stack.getTagCompound();
        if (encodedValue == null) return true;

        final World w = AppEng.proxy.getWorld();
        if (w == null) return false;

        try {
            new PatternHelper(stack, w);
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Whether one pattern answers a query on one side of it. The query is asked of the whole side at
     * once rather than of each ingredient: {@code -iron} means no ingredient is iron, which is not
     * something a single ingredient can answer.
     */
    private static boolean patternMatches(final ItemStack pattern, final RepoSearch search,
            final String side, final String query) {
        if (pattern.isEmpty()) {
            return false;
        }

        if (pattern.getTagCompound() == null) {
            // Nothing encoded to read, so it answers only to being named outright.
            return query.equalsIgnoreCase(GuiText.InvalidPattern.getLocal());
        }

        return search.matchesAny(ingredientsOf(pattern, side));
    }

    private static List<AEKey> ingredientsOf(final ItemStack pattern, final String side) {
        final NBTTagList tag = pattern.getTagCompound().getTagList(side, Constants.NBT.TAG_COMPOUND);
        final List<AEKey> keys = new ArrayList<>(tag.tagCount());

        for (int i = 0; i < tag.tagCount(); i++) {
            final GenericStack stack = GenericStack.resolveItemStack(new ItemStack(tag.getCompoundTagAt(i)));
            if (stack != null) {
                keys.add(stack.what());
            }
        }

        return keys;
    }

    private ClientDCInternalInv getById(final long id, final long sortBy, final String string) {
        ClientDCInternalInv o = this.byId.get(id);

        if (o == null) {
            this.byId.put(id, o = new ClientDCInternalInv(DualityInterface.NUMBER_OF_PATTERN_SLOTS, id, sortBy, string));
            this.refreshList = true;
        }

        return o;
    }
}
