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
import appeng.api.stacks.AEFluidKey;
import appeng.core.AELog;
import appeng.core.AEConfig;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.SlotDisconnected;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import javax.annotation.Nullable;
import appeng.container.implementations.ContainerInterfaceConfigurationTerminal;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.DualityInterface;
import appeng.helpers.InventoryAction;
import appeng.parts.reporting.PartInterfaceConfigurationTerminal;
import appeng.util.BlockPosUtils;
import appeng.util.Platform;
import com.google.common.collect.HashMultimap;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.DimensionManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

import static appeng.client.render.BlockPosHighlighter.hilightBlock;
import static appeng.helpers.ItemStackHelper.stackFromNBT;


public class GuiInterfaceConfigurationTerminal extends AEBaseGui implements IJEIGhostIngredients {

    private static final int MIN_ROWS = 6;
    private static final int ROW_HEIGHT = 18;
    private static final int FIXED_HEIGHT = 127;

    // TODO: copied from GuiMEMonitorable. It looks not changed, maybe unneeded?
    private final int offsetX = 21;

    private final HashMap<Long, ClientDCInternalInv> byId = new HashMap<>();
    private final HashMultimap<String, ClientDCInternalInv> byName = HashMultimap.create();
    private final HashMap<ClientDCInternalInv, BlockPos> blockPosHashMap = new HashMap<>();
    private final HashMap<GuiButton, ClientDCInternalInv> guiButtonHashMap = new HashMap<>();
    private final Map<ClientDCInternalInv, Integer> numUpgradesMap = new HashMap<>();
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<Object> lines = new ArrayList<>();
    private final Set<Object> matchedStacks = new HashSet<>();
    private final Set<ClientDCInternalInv> matchedInterfaces = new HashSet<>();

    private final Map<String, Set<Object>> cachedSearches = new WeakHashMap<>();

    private boolean refreshList = false;
    private int rows = MIN_ROWS;
    private MEGuiTextField searchFieldInputs;
    private GuiImgButton terminalStyleBox;
    private final PartInterfaceConfigurationTerminal partInterfaceTerminal;
    private final HashMap<ClientDCInternalInv, Integer> dimHashMap = new HashMap<>();
    public Map<IGhostIngredientHandler.Target<?>, Object> mapTargetSlot = new HashMap<>();

    public GuiInterfaceConfigurationTerminal(final InventoryPlayer inventoryPlayer, final PartInterfaceConfigurationTerminal te) {
        super(new ContainerInterfaceConfigurationTerminal(inventoryPlayer, te));

        this.partInterfaceTerminal = te;
        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
        this.xSize = 208;
        this.ySize = 235;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        final TerminalStyle style = (TerminalStyle) AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
        this.rows = Math.max(MIN_ROWS, style.getRows((this.height - FIXED_HEIGHT) / ROW_HEIGHT));
        this.ySize = FIXED_HEIGHT + this.rows * ROW_HEIGHT;
        super.initGui();

        this.getScrollBar().setLeft(189);
        this.getScrollBar().setHeight(this.rows * ROW_HEIGHT - 2);
        this.getScrollBar().setTop(31);

        this.terminalStyleBox = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8,
                Settings.TERMINAL_STYLE, style);

        for (final Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof appeng.container.slot.AppEngSlot slot) {
                slot.yPos = slot.getY() + this.ySize - 235;
            }
        }

        this.searchFieldInputs = new MEGuiTextField(this.fontRenderer, this.guiLeft + Math.max(32, this.offsetX), this.guiTop + 17, 65, 12);
        this.searchFieldInputs.setEnableBackgroundDrawing(false);
        this.searchFieldInputs.setMaxStringLength(25);
        this.searchFieldInputs.setTextColor(0xFFFFFF);
        this.searchFieldInputs.setVisible(true);
        this.searchFieldInputs.setFocused(false);

        this.searchFieldInputs.setText(partInterfaceTerminal.in);
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        if (this.terminalStyleBox == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new Rectangle(this.terminalStyleBox.x - 1,
                this.terminalStyleBox.y - 1, this.terminalStyleBox.width + 2,
                this.terminalStyleBox.height + 2));
    }

    @Override
    public void onGuiClosed() {
        partInterfaceTerminal.saveSearchStrings(this.searchFieldInputs.getText().toLowerCase());
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.buttonList.clear();
        this.terminalStyleBox.set(AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE));
        this.buttonList.add(this.terminalStyleBox);

        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.InterfaceConfigurationTerminal.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), this.offsetX + 2, this.ySize - 96 + 3, 4210752);

        final int currentScroll = this.getScrollBar().getCurrentScroll();

        this.inventorySlots.inventorySlots.removeIf(slot -> slot instanceof SlotDisconnected);

        int offset = 30;
        int linesDraw = 0;
        for (int x = 0; x < this.rows && linesDraw < this.rows && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv) {
                final ClientDCInternalInv inv = (ClientDCInternalInv) lineObj;

                GuiButton guiButton = new GuiImgButton(guiLeft + 4, guiTop + offset, Settings.ACTIONS, ActionItems.HIGHLIGHT_INTERFACE);
                guiButtonHashMap.put(guiButton, inv);
                this.buttonList.add(guiButton);
                int extraLines = numUpgradesMap.get(inv);

                for (int row = 0; row < 1 + extraLines && linesDraw < this.rows; ++row) {
                    for (int z = 0; z < 9; z++) {
                        this.inventorySlots.inventorySlots.add(new SlotDisconnected(inv, z + (row * 9), (z * 18 + 22), offset));
                        if (this.matchedStacks.contains(inv.getInventory().getStackInSlot(z + (row * 9)))) {
                            drawRect(z * 18 + 22, offset, z * 18 + 22 + 16, offset + 16, 0x8A00FF00);
                        } else if (!matchedInterfaces.contains(inv)) {
                            drawRect(z * 18 + 22, offset, z * 18 + 22 + 16, offset + 16, 0x6A000000);
                        }
                    }
                    linesDraw++;
                    offset += 18;
                }
            } else if (lineObj instanceof String) {
                String name = (String) lineObj;
                final int rows = this.byName.get(name).size();
                if (rows > 1) {
                    name = name + " (" + rows + ')';
                }

                while (name.length() > 2 && this.fontRenderer.getStringWidth(name) > 155) {
                    name = name.substring(0, name.length() - 1);
                }
                this.fontRenderer.drawString(name, this.offsetX + 2, 5 + offset, 4210752);
                linesDraw++;
                offset += 18;
            }
        }

        if (searchFieldInputs.isMouseIn(mouseX, mouseY)) {
            drawTooltip(Mouse.getEventX() * this.width / this.mc.displayWidth - offsetX, mouseY - guiTop, "Inputs OR names");
        }
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        this.searchFieldInputs.mouseClicked(xCoord, yCoord, btn);

        if (btn == 1 && this.searchFieldInputs.isMouseIn(xCoord, yCoord)) {
            this.searchFieldInputs.setText("");
            this.refreshList();
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (btn == this.terminalStyleBox) {
            final TerminalStyle current = (TerminalStyle) AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
            final TerminalStyle next = (TerminalStyle) Platform.rotateEnum(current, Mouse.isButtonDown(1),
                    Settings.TERMINAL_STYLE.getPossibleValues());
            final String search = this.searchFieldInputs.getText();
            AEConfig.instance().getConfigManager().putSetting(Settings.TERMINAL_STYLE, next);
            this.initGui();
            this.searchFieldInputs.setText(search);
        } else if (guiButtonHashMap.containsKey(btn)) {
            BlockPos blockPos = blockPosHashMap.get(guiButtonHashMap.get(this.selectedButton));
            BlockPos blockPos2 = mc.player.getPosition();
            int playerDim = mc.world.provider.getDimension();
            int interfaceDim = dimHashMap.get(guiButtonHashMap.get(this.selectedButton));
            if (playerDim != interfaceDim) {
                try {
                    mc.player.sendStatusMessage(new TextComponentString("Interface located at dimension: " + interfaceDim + " [" + DimensionManager.getWorld(interfaceDim).provider.getDimensionType().getName() + "] and cant be highlighted"), false);
                } catch (Exception e) {
                    mc.player.sendStatusMessage(new TextComponentString("Interface is located in another dimension and cannot be highlighted"), false);
                }
            } else {
                hilightBlock(blockPos, System.currentTimeMillis() + 500 * BlockPosUtils.getDistance(blockPos, blockPos2), playerDim);
                mc.player.sendStatusMessage(new TextComponentString("The interface is now highlighted at " + "X: " + blockPos.getX() + " Y: " + blockPos.getY() + " Z: " + blockPos.getZ()), false);
            }
            mc.player.closeScreen();
        }
    }

    @Override
    protected void mouseWheelEvent(final int x, final int y, final int wheel) {
        final Slot slot = this.getSlot(x, y);
        if (slot instanceof SlotDisconnected) {
            final ItemStack stack = slot.getStack();
            if (stack != ItemStack.EMPTY) {
                final PacketInventoryAction p;
                if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                    InventoryAction direction = wheel > 0 ? InventoryAction.DOUBLE : InventoryAction.HALVE;
                    p = new PacketInventoryAction(direction, slot.getSlotIndex(), ((SlotDisconnected) slot).getSlot().getId());
                } else {
                    InventoryAction direction = wheel > 0 ? InventoryAction.PLACE_SINGLE : InventoryAction.PICKUP_SINGLE;
                    p = new PacketInventoryAction(direction, slot.getSlotIndex(), ((SlotDisconnected) slot).getSlot().getId());
                }
                NetworkHandler.instance().sendToServer(p);
            }
        } else {
            super.mouseWheelEvent(x, y, wheel);
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/interfaceconfigurationterminal.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, 29);
        for (int row = 0; row < this.rows; row++) {
            this.drawTexturedModalRect(offsetX, offsetY + 29 + row * ROW_HEIGHT, 0, 29,
                    188, ROW_HEIGHT);
            this.drawTexturedModalRect(offsetX + 188, offsetY + 29 + row * ROW_HEIGHT, 188, 31,
                    this.xSize - 188, ROW_HEIGHT);
        }
        this.drawTexturedModalRect(offsetX, offsetY + 29 + this.rows * ROW_HEIGHT, 0, 137,
                this.xSize, 98);

        int offset = 29;
        final int ex = this.getScrollBar().getCurrentScroll();
        int linesDraw = 0;
        for (int x = 0; x < this.rows && linesDraw < this.rows && ex + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(ex + x);
            if (lineObj instanceof ClientDCInternalInv) {
                GlStateManager.color(1, 1, 1, 1);
                final int width = 9 * 18;

                int extraLines = numUpgradesMap.get(lineObj);

                for (int row = 0; row < 1 + extraLines && linesDraw < this.rows; ++row) {
                    this.drawTexturedModalRect(offsetX + 20, offsetY + offset, 20, 170, width, 18);
                    offset += 18;
                    linesDraw++;
                }
            } else {
                offset += 18;
                linesDraw++;
            }
        }

        if (this.searchFieldInputs != null) {
            this.searchFieldInputs.drawTextBox();
        }
    }

    @Override
    public boolean isTextFieldFocused() {
        return this.searchFieldInputs != null && this.searchFieldInputs.isFocused();
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (character == ' ' && this.searchFieldInputs.getText().isEmpty() && this.searchFieldInputs.isFocused()) {
                return;
            }

            if (this.searchFieldInputs.textboxKeyTyped(character, key)) {
                this.refreshList();
            } else {
                super.keyTyped(character, key);
            }
        }
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
                    blockPosHashMap.put(current, NBTUtil.getPosFromTag(invData.getCompoundTag("pos")));
                    dimHashMap.put(current, invData.getInteger("dim"));
                    numUpgradesMap.put(current, invData.getInteger("numUpgrades"));

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
            // invalid caches on refresh
            this.cachedSearches.clear();
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
        this.matchedInterfaces.clear();

        final String searchFieldInputs = this.searchFieldInputs.getText().toLowerCase();

        final Set<Object> cachedSearch = this.getCacheForSearchTerm(searchFieldInputs);
        final boolean rebuild = cachedSearch.isEmpty();

        for (final ClientDCInternalInv entry : this.byId.values()) {
            // ignore inventory if not doing a full rebuild and cache already marks it as miss.
            if (!rebuild && !cachedSearch.contains(entry)) {
                continue;
            }

            // Shortcut to skip any filter if search term is ""/empty

            boolean found = searchFieldInputs.isEmpty();

            // Search if the current inventory holds a pattern containing the search term.
            if (!found) {
                int slot = 0;
                for (final ItemStack itemStack : entry.getInventory()) {
                    if (slot > 8 + numUpgradesMap.get(entry) * 9) {
                        break;
                    }
                    if (this.itemStackMatchesSearchTerm(itemStack, searchFieldInputs)) {
                        found = true;
                        matchedStacks.add(itemStack);
                    }
                    slot++;
                }
            }
            // if found, filter skipped or machine name matching the search term, add it
            if (searchFieldInputs.isEmpty() || entry.getName().toLowerCase().contains(searchFieldInputs)) {
                this.matchedInterfaces.add(entry);
                found = true;
            }
            if (found) {
                this.byName.put(entry.getName(), entry);
                cachedSearch.add(entry);
            } else {
                cachedSearch.remove(entry);
            }
        }

        this.names.clear();
        this.names.addAll(this.byName.keySet());

        Collections.sort(this.names);

        this.lines.clear();
        this.lines.ensureCapacity(this.getMaxRows());

        for (final String n : this.names) {
            this.lines.add(n);

            final ArrayList<ClientDCInternalInv> clientInventories = new ArrayList<>();
            clientInventories.addAll(this.byName.get(n));

            Collections.sort(clientInventories);
            this.lines.addAll(clientInventories);
        }

        this.getScrollBar().setRange(0, this.lines.size() - 1, 1);
    }

    private boolean itemStackMatchesSearchTerm(final ItemStack itemStack, final String searchTerm) {
        if (itemStack.isEmpty()) {
            return false;
        }

        boolean foundMatchingItemStack = false;

        final String displayName = Platform.getItemDisplayName(itemStack).toLowerCase();

        for (String term : searchTerm.split(" ")) {
            if (term.length() > 1 && (term.startsWith("-") || term.startsWith("!"))) {
                term = term.substring(1);
                if (displayName.contains(term)) {
                    return false;
                }
            } else if (displayName.contains(term)) {
                foundMatchingItemStack = true;
            } else {
                return false;
            }
        }
        return foundMatchingItemStack;
    }

    /**
     * Tries to retrieve a cache for a with search term as keyword.
     * <p>
     * If this cache should be empty, it will populate it with an earlier cache if available or at least the cache for
     * the empty string.
     *
     * @param searchTerm the corresponding search
     * @return a Set matching a superset of the search term
     */
    private Set<Object> getCacheForSearchTerm(final String searchTerm) {
        if (!this.cachedSearches.containsKey(searchTerm)) {
            this.cachedSearches.put(searchTerm, new HashSet<>());
        }

        final Set<Object> cache = this.cachedSearches.get(searchTerm);

        if (cache.isEmpty() && searchTerm.length() > 1) {
            cache.addAll(this.getCacheForSearchTerm(searchTerm.substring(0, searchTerm.length() - 1)));
            return cache;
        }

        return cache;
    }

    /**
     * The max amount of unique names and each inv row. Not affected by the filtering.
     *
     * @return max amount of unique names and each inv row
     */
    private int getMaxRows() {
        return this.names.size() + this.byId.size();
    }

    /**
     * What HEI handed over, as a key: an ordinary item, the fluid inside a dragged container, or a fluid
     * dragged straight from the ingredient list.
     */
    @Nullable
    private static GenericStack asGenericStack(final Object ingredient) {
        if (ingredient instanceof FluidStack fluidStack) {
            // A dragged fluid has no container to fall back to, so the button does not come into it.
            return new GenericStack(AEFluidKey.of(fluidStack), fluidStack.amount);
        }
        if (ingredient instanceof ItemStack itemStack && !itemStack.isEmpty()) {
            if (!dropsContainerItself()) {
                final FluidStack contained = FluidUtil.getFluidContained(itemStack);
                if (contained != null && contained.amount > 0) {
                    return new GenericStack(AEFluidKey.of(contained), contained.amount);
                }
            }
            return GenericStack.resolveItemStack(itemStack);
        }
        return null;
    }

    /**
     * @return true if the drop being handled right now came from the right mouse button, meaning the
     *         container item itself is wanted rather than its contents - the same rule as clicking a config
     *         slot by hand, and the same one {@code GuiUpgradeable} follows for filter slots.
     *         <p>
     *         HEI hands a {@code Target} no button, so the live mouse state is the only thing left to read.
     *         It is accurate here because {@code accept} runs synchronously inside the click that ends the
     *         drag, while that button is still down.
     */
    private static boolean dropsContainerItself() {
        return Mouse.getEventButton() == 1 || Mouse.isButtonDown(1);
    }

    private ClientDCInternalInv getById(final long id, final long sortBy, final String string) {
        ClientDCInternalInv o = this.byId.get(id);

        if (o == null) {
            this.byId.put(id, o = new ClientDCInternalInv(DualityInterface.NUMBER_OF_CONFIG_SLOTS, id, sortBy, string, 512));
            this.refreshList = true;
        }

        return o;
    }

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        // An interface's config holds any key type, so a fluid dragged out of HEI is a valid target here -
        // it just arrives as a FluidStack rather than an ItemStack. Wrapped into a placeholder, the rest of
        // the path is unchanged: the packet already carries a GenericStack and the config unwraps it.
        //
        // Deliberately not resolved here. HEI asks for targets when the drag *starts* and calls accept when
        // it ends, and which of a container's two readings the player wants is decided by the button held at
        // the end - so resolving at this point pinned the answer before the question was asked, and a
        // right-drag still produced the contents.
        if (!(ingredient instanceof ItemStack) && !(ingredient instanceof FluidStack)) {
            return Collections.emptyList();
        }
        if (ingredient instanceof ItemStack stack && stack.isEmpty()) {
            return Collections.emptyList();
        }
        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof SlotDisconnected) {
                IGhostIngredientHandler.Target<Object> target = new IGhostIngredientHandler.Target<Object>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(getGuiLeft() + slot.xPos, getGuiTop() + slot.yPos, 16, 16);
                    }

                    @Override
                    public void accept(Object ingredient) {
                        final GenericStack dragged = asGenericStack(ingredient);
                        if (dragged == null) {
                            return;
                        }
                        try {
                            NetworkHandler.instance().sendToServer(
                                    new PacketInventoryAction(InventoryAction.PLACE_JEI_GHOST_ITEM, (SlotDisconnected) slot, dragged));
                        } catch (IOException e) {
                            AELog.debug(e);
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
    public Map<IGhostIngredientHandler.Target<?>, Object> getFakeSlotTargetMap() {
        return IJEIGhostIngredients.super.getFakeSlotTargetMap();
    }
}
