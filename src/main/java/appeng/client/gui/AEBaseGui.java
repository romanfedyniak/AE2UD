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

package appeng.client.gui;


import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.me.InternalSlotME;
import appeng.client.me.SlotDisconnected;
import appeng.client.me.SlotME;
import appeng.client.render.StackSizeRenderer;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerInterface;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.me.GridInventoryEntry;
import appeng.container.slot.*;
import appeng.container.slot.AppEngSlot.hasCalculatedValidness;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketSwapSlots;
import appeng.helpers.InventoryAction;
import appeng.items.misc.ItemEncodedPattern;
import appeng.util.Platform;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Optional;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import yalter.mousetweaks.api.IMTModGuiContainer2;

import java.awt.*;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static appeng.integration.modules.jei.JEIPlugin.aeGuiHandler;
import static appeng.integration.modules.jei.JEIPlugin.runtime;

@Optional.Interface(iface = "yalter.mousetweaks.api.IMTModGuiContainer2", modid = "mousetweaks")
public abstract class AEBaseGui extends GuiContainer implements IMTModGuiContainer2 {
    private final List<InternalSlotME> meSlots = new ArrayList<>();
    // drag y
    private final Set<Slot> drag_click = new HashSet<>();
    private final StackSizeRenderer stackSizeRenderer = new StackSizeRenderer();
    private GuiScrollbar myScrollBar = null;
    private boolean disableShiftClick = false;
    private Stopwatch dbl_clickTimer = Stopwatch.createStarted();
    private ItemStack dbl_whichItem = ItemStack.EMPTY;
    private Slot bl_clicked;
    private boolean haltDragging = false;

    public List<GuiCustomSlot> getGuiSlots() {
        return guiSlots;
    }

    protected final List<GuiCustomSlot> guiSlots = new ArrayList<>();

    public AEBaseGui(final Container container) {
        super(container);
    }

    protected static String join(final Collection<String> toolTip, final String delimiter) {
        final Joiner joiner = Joiner.on(delimiter);

        return joiner.join(toolTip);
    }

    protected int getQty(final GuiButton btn) {
        try {
            final DecimalFormat df = new DecimalFormat("+#;-#");
            return df.parse(btn.displayString).intValue();
        } catch (final ParseException e) {
            return 0;
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        final List<Slot> slots = this.getInventorySlots();
        final Iterator<Slot> i = slots.iterator();
        while (i.hasNext()) {
            if (i.next() instanceof SlotME) {
                i.remove();
            }
        }

        for (final InternalSlotME me : this.meSlots) {
            slots.add(me.createSlot());
        }
    }

    /**
     * Whether a text field on this screen currently has the keyboard.
     * <p>
     * Anything that reads a raw key press before the screen does has to ask this first, or typing a letter
     * into a search box triggers whatever that letter is bound to. HEI's own handler makes the same check
     * for its own field; a screen with a field of ours has to answer for it.
     */
    public boolean isTextFieldFocused() {
        return false;
    }

    /**
     * Whether a middle click on this fake slot should open the amount screen, which is only where a
     * configured amount means something. A filter slot matches on the key alone, and a crafting-mode pattern
     * slot is one item of the recipe, so neither has an amount to set.
     */
    protected boolean allowsTypedAmount(final Slot slot) {
        if (this.inventorySlots instanceof ContainerPatternEncoder) {
            return !((ContainerPatternEncoder) this.inventorySlots).isCraftingMode();
        }

        // An ME Interface stocks its config up to the slot's capacity - 512 items, or 32 buckets.
        return this.inventorySlots instanceof ContainerInterface;
    }

    private List<Slot> getInventorySlots() {
        return this.inventorySlots.inventorySlots;
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        GlStateManager.pushMatrix();
        GlStateManager.translate(this.guiLeft, this.guiTop, 0.0F);
        GlStateManager.enableDepth();
        for (final GuiCustomSlot c : this.guiSlots) {
            this.drawGuiSlot(c, mouseX, mouseY, partialTicks);
        }
        GlStateManager.disableDepth();
        for (final GuiCustomSlot c : this.guiSlots) {
            this.drawTooltip(c, mouseX - this.guiLeft, mouseY - this.guiTop);
        }
        GlStateManager.popMatrix();

        this.renderHoveredToolTip(mouseX, mouseY);

        for (final Object c : this.buttonList) {
            if (c instanceof ITooltip) {
                this.drawTooltip((ITooltip) c, mouseX, mouseY);
            }
        }

        for (final Object o : this.labelList) {
            if (o instanceof ITooltip) {
                this.drawTooltip((ITooltip) o, mouseX, mouseY);
            }
        }
        GlStateManager.enableDepth();
        GlStateManager.disableDepth();
    }

    public List<Rectangle> getJEIExclusionArea() {
        return Collections.emptyList();
    }

    /**
     * @return true if the stack on the cursor holds a fluid, i.e. clicking a filter slot with it should set
     *         the filter to that fluid rather than to the container item.
     */
    private boolean holdsFluidContainer() {
        final ItemStack held = this.mc.player.inventory.getItemStack();
        if (held.isEmpty()) {
            return false;
        }

        final FluidStack fluid = FluidUtil.getFluidContained(held);
        return fluid != null && fluid.amount > 0;
    }

    protected void drawGuiSlot(GuiCustomSlot slot, int mouseX, int mouseY, float partialTicks) {
        if (slot.isSlotEnabled()) {
            final int left = slot.xPos();
            final int top = slot.yPos();
            final int right = left + slot.getWidth();
            final int bottom = top + slot.getHeight();

            slot.drawContent(this.mc, mouseX, mouseY, partialTicks);

            if (this.isPointInRegion(left, top, slot.getWidth(), slot.getHeight(), mouseX, mouseY) && slot.canClick(this.mc.player)) {
                GlStateManager.disableLighting();
                GlStateManager.colorMask(true, true, true, false);
                this.drawGradientRect(left, top, right, bottom, -2130706433, -2130706433);
                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.enableLighting();
            }
        }
    }

    protected void drawTooltip(ITooltip tooltip, int mouseX, int mouseY) {
        final int x = tooltip.xPos(); // ((GuiImgButton) c).x;
        int y = tooltip.yPos(); // ((GuiImgButton) c).y;

        if (x < mouseX && x + tooltip.getWidth() > mouseX && tooltip.isVisible()) {
            if (y < mouseY && y + tooltip.getHeight() > mouseY) {
                if (y < 15) {
                    y = 15;
                }

                final String msg = tooltip.getMessage();
                if (msg != null) {
                    this.drawTooltip(x + 11, y + 4, msg);
                }
            }
        }
    }

    protected void drawTooltip(int x, int y, String message) {
        String[] lines = message.split("\n");
        this.drawTooltip(x, y, Arrays.asList(lines));
    }

    protected void drawTooltip(int x, int y, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }

        // For an explanation of the formatting codes, see http://minecraft.gamepedia.com/Formatting_codes
        lines = Lists.newArrayList(lines); // Make a copy

        // Make the first line white
        lines.set(0, TextFormatting.WHITE + lines.get(0));

        // All lines after the first are colored gray
        for (int i = 1; i < lines.size(); i++) {
            lines.set(i, TextFormatting.GRAY + lines.get(i));
        }

        this.drawHoveringText(lines, x, y, this.fontRenderer);
    }

    @Override
    protected final void drawGuiContainerForegroundLayer(final int x, final int y) {
        final int ox = this.guiLeft; // (width - xSize) / 2;
        final int oy = this.guiTop; // (height - ySize) / 2;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (this.getScrollBar() != null) {
            this.getScrollBar().draw(this);
        }

        this.drawFG(ox, oy, x, y);
    }

    public abstract void drawFG(int offsetX, int offsetY, int mouseX, int mouseY);

    @Override
    protected final void drawGuiContainerBackgroundLayer(final float f, final int x, final int y) {
        final int ox = this.guiLeft; // (width - xSize) / 2;
        final int oy = this.guiTop; // (height - ySize) / 2;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawBG(ox, oy, x, y);

        final List<Slot> slots = this.getInventorySlots();
        for (final Slot slot : slots) {
            if (slot instanceof IOptionalSlot) {
                final IOptionalSlot optionalSlot = (IOptionalSlot) slot;
                if (optionalSlot.isRenderDisabled()) {
                    final AppEngSlot aeSlot = (AppEngSlot) slot;
                    if (aeSlot.isSlotEnabled()) {
                        this.drawTexturedModalRect(ox + aeSlot.xPos - 1, oy + aeSlot.yPos - 1, optionalSlot.getSourceX() - 1, optionalSlot.getSourceY() - 1, 18, 18);
                    } else {
                        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.4F);
                        GlStateManager.enableBlend();
                        this.drawTexturedModalRect(ox + aeSlot.xPos - 1, oy + aeSlot.yPos - 1, optionalSlot.getSourceX() - 1, optionalSlot.getSourceY() - 1, 18, 18);
                        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    }
                }
            }
        }

        for (final GuiCustomSlot slot : this.guiSlots) {
            slot.drawBackground(ox, oy);
        }
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        this.drag_click.clear();

        if (btn == 1) {
            for (final Object o : this.buttonList) {
                final GuiButton guibutton = (GuiButton) o;
                if (guibutton.mousePressed(this.mc, xCoord, yCoord)) {
                    super.mouseClicked(xCoord, yCoord, 0);
                    return;
                }
            }
        }

        for (GuiCustomSlot slot : this.guiSlots) {
            if (this.isPointInRegion(slot.xPos(), slot.yPos(), slot.getWidth(), slot.getHeight(), xCoord, yCoord) && slot.canClick(this.mc.player)) {
                slot.slotClicked(this.mc.player.inventory.getItemStack(), btn);
            }
        }

        if (this.getScrollBar() != null) {
            this.getScrollBar().click(this, xCoord - this.guiLeft, yCoord - this.guiTop);
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.drag_click.clear();
        this.haltDragging = false;

        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int c, final long d) {
        final Slot slot = this.getSlot(x, y);
        final ItemStack itemstack = this.mc.player.inventory.getItemStack();

        if (this.getScrollBar() != null) {
            this.getScrollBar().click(this, x - this.guiLeft, y - this.guiTop);
        }

        if (slot instanceof SlotFake && !itemstack.isEmpty()) {
            if (this.drag_click.add(slot)) {
                final PacketInventoryAction p = new PacketInventoryAction(c == 0 ? InventoryAction.PICKUP_OR_SET_DOWN : InventoryAction.PLACE_SINGLE, slot.slotNumber, 0);
                NetworkHandler.instance().sendToServer(p);
            }
        } else if (slot instanceof SlotDisconnected) {
            if (!haltDragging && this.drag_click.add(slot)) {
                if (!itemstack.isEmpty()) {
                    if (slot.getStack().isEmpty()) {
                        InventoryAction action;
                        if (slot.getSlotStackLimit() == 1) {
                            action = InventoryAction.SPLIT_OR_PLACE_SINGLE;
                        } else {
                            action = InventoryAction.PICKUP_OR_SET_DOWN;
                        }
                        final PacketInventoryAction p = new PacketInventoryAction(action, slot.getSlotIndex(), ((SlotDisconnected) slot).getSlot().getId());
                        NetworkHandler.instance().sendToServer(p);
                    }
                }
            } else if (isShiftKeyDown()) {
                for (final Slot dr : this.drag_click) {
                    InventoryAction action = null;
                    if (!slot.getStack().isEmpty()) {
                        action = InventoryAction.SHIFT_CLICK;
                    }
                    if (action != null) {
                        final PacketInventoryAction p = new PacketInventoryAction(action, dr.getSlotIndex(), ((SlotDisconnected) slot).getSlot().getId());
                        NetworkHandler.instance().sendToServer(p);
                    }
                }
            }
        } else {
            super.mouseClickMove(x, y, c, d);
        }
    }

    // TODO 1.9.4 aftermath - Whole ClickType thing, to be checked.
    @Override
    protected void handleMouseClick(final Slot slot, final int slotIdx, final int mouseButton, final ClickType clickType) {
        final EntityPlayer player = Minecraft.getMinecraft().player;

        // A slot holding real stock of a key the player cannot pick up - an interface's fluid. Left click
        // fills what is held from that slot, right click empties into it; same convention as a terminal row.
        // Anything else falls through to the ordinary item handling, so the item slots are untouched.
        if (slot instanceof SlotGenericStorage && clickType == ClickType.PICKUP && !player.inventory.getItemStack().isEmpty()) {
            final GenericStack held = GenericStack.resolveItemStack(player.inventory.getItemStack());
            final boolean holdsContainer = ContainerItemStrategies.getContainedStack(player.inventory.getItemStack()) != null;
            final GenericStack inSlot = GenericStack.unwrapItemStack(slot.getStack());

            if (mouseButton == 0 && inSlot != null && ContainerItemStrategies.isKeySupported(inSlot.what())) {
                NetworkHandler.instance().sendToServer(new PacketInventoryAction(InventoryAction.FILL_ITEM, slotIdx, 0));
                return;
            }
            if (mouseButton == 1 && holdsContainer && held != null) {
                NetworkHandler.instance().sendToServer(new PacketInventoryAction(InventoryAction.EMPTY_ITEM, slotIdx, 0));
                return;
            }
        }

        if (slot instanceof SlotFake) {
            if (mouseButton == 2 && slot.getHasStack() && this.allowsTypedAmount(slot)) {
                NetworkHandler.instance().sendToServer(new PacketInventoryAction(InventoryAction.SET_AMOUNT, slotIdx, 0));
                return;
            }

            final InventoryAction action;
            if (mouseButton == 1) {
                action = InventoryAction.SPLIT_OR_PLACE_SINGLE;
            } else if (holdsFluidContainer()) {
                // Left-clicking a filter slot with a bucket or tank in hand sets the filter to the FLUID it
                // holds, not to the container. Right click still places the container itself, so an item
                // filter can still be set to a bucket - that is the only way to tell the two apart, since a
                // bucket is a perfectly ordinary item everywhere else.
                action = InventoryAction.EMPTY_ITEM;
            } else {
                action = InventoryAction.PICKUP_OR_SET_DOWN;
            }

            if (this.drag_click.size() > 1) {
                return;
            }

            PacketInventoryAction p = new PacketInventoryAction(action, slotIdx, 0);
            NetworkHandler.instance().sendToServer(p);
            return;
        }

        if (slot instanceof SlotPatternTerm) {
            if (mouseButton == 6) {
                return; // prevent weird double clicks..
            }

            try {
                NetworkHandler.instance().sendToServer(((SlotPatternTerm) slot).getRequest(isShiftKeyDown()));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        } else if (slot instanceof SlotCraftingTerm) {
            if (mouseButton == 6) {
                return; // prevent weird double clicks..
            }

            InventoryAction action = null;
            if (isShiftKeyDown()) {
                action = InventoryAction.CRAFT_SHIFT;
            } else {
                // Craft stack on right-click, craft single on left-click
                action = (mouseButton == 1) ? InventoryAction.CRAFT_STACK : InventoryAction.CRAFT_ITEM;
            }

            final PacketInventoryAction p = new PacketInventoryAction(action, slotIdx, 0);
            NetworkHandler.instance().sendToServer(p);

            return;
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
            if (this.enableSpaceClicking()) {
                GridInventoryEntry entry = null;
                if (slot instanceof SlotME) {
                    entry = ((SlotME) slot).getEntry();
                }

                int slotNum = this.getInventorySlots().size();

                if (!(slot instanceof SlotME) && slot != null) {
                    slotNum = slot.slotNumber;
                }

                ((AEBaseContainer) this.inventorySlots).setTargetStack(entry == null ? null : entry.getWhat());
                final PacketInventoryAction p = new PacketInventoryAction(InventoryAction.MOVE_REGION, slotNum, 0);
                NetworkHandler.instance().sendToServer(p);
                return;
            }
        }

        if (slot instanceof SlotDisconnected) {
            if (this.drag_click.size() >= 1) {
                return;
            }

            // Same middle click as a filter slot on the interface's own screen. These slots are that very
            // config inventory, reached remotely, so the amount means the same thing here.
            if (mouseButton == 2 && slot.getHasStack()) {
                NetworkHandler.instance().sendToServer(new PacketInventoryAction(InventoryAction.SET_AMOUNT,
                        slot.getSlotIndex(), ((SlotDisconnected) slot).getSlot().getId()));
                return;
            }

            InventoryAction action = null;

            switch (clickType) {
                case PICKUP: // pickup / set-down.
                    if (mouseButton == 1) {
                        action = InventoryAction.SPLIT_OR_PLACE_SINGLE;
                    } else if (holdsFluidContainer()) {
                        // Left click configures this slot to what the held container *holds*; right click
                        // still places the container itself. Same rule a SlotFake has followed since stage
                        // 0 - the config terminal writes into the very same config inventory, it just
                        // reaches it through a different slot type and so was missed.
                        action = InventoryAction.EMPTY_ITEM;
                    } else {
                        action = InventoryAction.PICKUP_OR_SET_DOWN;
                    }
                    break;
                case QUICK_MOVE:
                    action = (mouseButton == 1) ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
                    break;

                case CLONE: // creative dupe:

                    if (player.capabilities.isCreativeMode) {
                        action = InventoryAction.CREATIVE_DUPLICATE;
                    }

                    break;

                default:
                case THROW: // drop item:
            }

            if (action != null) {
                final PacketInventoryAction p = new PacketInventoryAction(action, slot.getSlotIndex(), ((SlotDisconnected) slot).getSlot().getId());
                NetworkHandler.instance().sendToServer(p);
            }

            return;
        }

        if (slot instanceof SlotME) {
            InventoryAction action = null;
            GridInventoryEntry entry = null;

            switch (clickType) {
                case PICKUP: // pickup / set-down.
                    action = (mouseButton == 1) ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN;
                    entry = ((SlotME) slot).getEntry();

                    if (entry != null
                            && action == InventoryAction.PICKUP_OR_SET_DOWN
                            && (entry.getStoredAmount() == 0 || GuiScreen.isAltKeyDown())
                            && player.inventory.getItemStack().isEmpty()) {
                        action = InventoryAction.AUTO_CRAFT;
                    }

                    // A row the player cannot pick up by hand - a fluid - but can carry away in a container.
                    // Left click fills what is held from the network; with an empty hand the server borrows
                    // an empty container out of storage and fills that instead. Never overrides AUTO_CRAFT,
                    // which is what an empty hand on a craftable-but-unstocked row already means.
                    if (mouseButton == 0 && entry != null && action != InventoryAction.AUTO_CRAFT
                            && ContainerItemStrategies.isKeySupported(entry.getWhat())) {
                        action = InventoryAction.FILL_ITEM;
                    } else if (mouseButton == 1 && !player.inventory.getItemStack().isEmpty()
                            && ContainerItemStrategies.getContainedStack(player.inventory.getItemStack()) != null) {
                        // Right click empties a held container into the network. Gated on the held item
                        // actually containing something, so right-clicking with an ordinary stack still
                        // places a single item as it always did - a bucket is a normal item everywhere else.
                        action = InventoryAction.EMPTY_ITEM;
                    }

                    break;
                case QUICK_MOVE:
                    action = (mouseButton == 1) ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
                    entry = ((SlotME) slot).getEntry();
                    break;

                case CLONE: // creative dupe:

                    entry = ((SlotME) slot).getEntry();
                    if (entry != null && entry.isCraftable()) {
                        action = InventoryAction.AUTO_CRAFT;
                    } else if (player.capabilities.isCreativeMode) {
                        final GridInventoryEntry slotItem = ((SlotME) slot).getEntry();
                        if (slotItem != null) {
                            action = InventoryAction.CREATIVE_DUPLICATE;
                        }
                    }
                    break;

                default:
                case THROW: // drop item:
            }

            if (action != null) {
                ((AEBaseContainer) this.inventorySlots).setTargetStack(entry == null ? null : entry.getWhat());
                final PacketInventoryAction p = new PacketInventoryAction(action, this.getInventorySlots().size(), 0);
                NetworkHandler.instance().sendToServer(p);
            }

            return;
        }

        if (!this.disableShiftClick && isShiftKeyDown() && mouseButton == 0) {
            this.disableShiftClick = true;

            if (this.dbl_whichItem.isEmpty() || this.bl_clicked != slot || this.dbl_clickTimer.elapsed(TimeUnit.MILLISECONDS) > 250) {
                // some simple double click logic.
                this.bl_clicked = slot;
                this.dbl_clickTimer = Stopwatch.createStarted();
                if (slot != null) {
                    this.dbl_whichItem = slot.getHasStack() ? slot.getStack().copy() : ItemStack.EMPTY;
                } else {
                    this.dbl_whichItem = ItemStack.EMPTY;
                }
            } else if (!this.dbl_whichItem.isEmpty()) {
                // a replica of the weird broken vanilla feature.

                final List<Slot> slots = this.getInventorySlots();
                for (final Slot inventorySlot : slots) {
                    if (inventorySlot != null && inventorySlot.canTakeStack(this.mc.player) && inventorySlot.getHasStack() && inventorySlot.isSameInventory(slot) && Container.canAddItemToSlot(inventorySlot, this.dbl_whichItem, true)) {
                        this.handleMouseClick(inventorySlot, inventorySlot.slotNumber, 0, ClickType.QUICK_MOVE);
                    }
                }
                this.dbl_whichItem = ItemStack.EMPTY;
            }

            this.disableShiftClick = false;
        }

        super.handleMouseClick(slot, slotIdx, mouseButton, clickType);
    }

    @Override
    protected boolean checkHotbarKeys(final int keyCode) {
        final Slot theSlot = this.getSlotUnderMouse();

        // A filter holds an identity, not a stack: nothing to swap with, and vanilla would empty it.
        if (theSlot instanceof SlotFake) {
            return false;
        }

        if (this.mc.player.inventory.getItemStack().isEmpty() && theSlot != null) {
            for (int j = 0; j < 9; ++j) {
                if (keyCode == this.mc.gameSettings.keyBindsHotbar[j].getKeyCode()) {
                    // Found by class: every AppEngSlot reports the same dummy inventory, so comparing
                    // s.inventory to the player's matches nothing.
                    Slot hotbarSlot = null;
                    for (final Slot s : this.getInventorySlots()) {
                        if (s.getSlotIndex() != j) {
                            continue;
                        }
                        if (s instanceof SlotDisabled) {
                            return false;
                        }
                        if (s instanceof SlotPlayerHotBar) {
                            hotbarSlot = s;
                            break;
                        }
                    }

                    // getHasStack first: an empty slot also answers "cannot take", and taking into one is
                    // half of what this key does.
                    if (hotbarSlot != null && hotbarSlot.getHasStack() && !hotbarSlot.canTakeStack(this.mc.player)) {
                        return false;
                    }

                    // Vanilla's swap would hand over the whole oversized stack; ours leaves the rest behind.
                    final ItemStack inSlot = theSlot.getStack();
                    if (hotbarSlot != null && inSlot.getCount() > inSlot.getMaxStackSize()) {
                        NetworkHandler.instance().sendToServer(new PacketSwapSlots(hotbarSlot.slotNumber, theSlot.slotNumber));
                    } else {
                        this.handleMouseClick(theSlot, theSlot.slotNumber, j, ClickType.SWAP);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    protected Slot getSlot(final int mouseX, final int mouseY) {
        final List<Slot> slots = this.getInventorySlots();
        for (final Slot slot : slots) {
            // isPointInRegion
            if (this.isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY)) {
                return slot;
            }
        }

        return null;
    }

    public abstract void drawBG(int offsetX, int offsetY, int mouseX, int mouseY);

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        final int i = Mouse.getEventDWheel();
        if (i != 0 && isShiftKeyDown()) {
            final int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
            final int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            this.mouseWheelEvent(x, y, i / Math.abs(i));
        } else if (i != 0 && this.getScrollBar() != null) {
            this.getScrollBar().wheel(i);
        }
    }

    protected void mouseWheelEvent(final int x, final int y, final int wheel) {
        final Slot slot = this.getSlot(x, y);
        if (slot instanceof SlotME) {
            final GridInventoryEntry entry = ((SlotME) slot).getEntry();
            if (entry != null) {
                ((AEBaseContainer) this.inventorySlots).setTargetStack(entry.getWhat());
                final InventoryAction direction = wheel > 0 ? InventoryAction.ROLL_DOWN : InventoryAction.ROLL_UP;
                final int times = Math.abs(wheel);
                final int inventorySize = this.getInventorySlots().size();
                for (int h = 0; h < times; h++) {
                    final PacketInventoryAction p = new PacketInventoryAction(direction, inventorySize, 0);
                    NetworkHandler.instance().sendToServer(p);
                }
            }
        }
        if (slot instanceof SlotFake || slot instanceof SlotDisconnected) {
            final ItemStack stack = slot.getStack();
            if (stack != ItemStack.EMPTY) {
                final InventoryAction direction;
                if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                    direction = wheel > 0 ? InventoryAction.DOUBLE : InventoryAction.HALVE;
                } else {
                    direction = wheel > 0 ? InventoryAction.PLACE_SINGLE : InventoryAction.PICKUP_SINGLE;
                }

                // A disconnected slot belongs to a remote interface, so it is addressed by that interface's
                // id and its own index - not by a slot number in this container.
                final PacketInventoryAction p = slot instanceof SlotDisconnected disconnected
                        ? new PacketInventoryAction(direction, slot.getSlotIndex(), disconnected.getSlot().getId())
                        : new PacketInventoryAction(direction, slot.slotNumber, 0);
                NetworkHandler.instance().sendToServer(p);
            }
        }
    }

    protected boolean enableSpaceClicking() {
        return true;
    }

    public void bindTexture(final String base, final String file) {
        final ResourceLocation loc = new ResourceLocation(base, "textures/" + file);
        this.mc.getTextureManager().bindTexture(loc);
    }

    protected void drawItem(final int x, final int y, final ItemStack is) {
        this.zLevel = 100.0F;
        this.itemRender.zLevel = 100.0F;

        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        this.itemRender.renderItemAndEffectIntoGUI(is, x, y);
        GlStateManager.disableDepth();

        this.itemRender.zLevel = 0.0F;
        this.zLevel = 0.0F;
    }

    protected String getGuiDisplayName(final String in) {
        return this.hasCustomInventoryName() ? this.getInventoryName() : in;
    }

    private boolean hasCustomInventoryName() {
        if (this.inventorySlots instanceof AEBaseContainer) {
            return ((AEBaseContainer) this.inventorySlots).getCustomName() != null;
        }
        return false;
    }

    private String getInventoryName() {
        return ((AEBaseContainer) this.inventorySlots).getCustomName();
    }

    /**
     * This overrides the base-class method through some access transformer hackery...
     */
    @Override
    public void drawSlot(Slot s) {
        if (s instanceof SlotME) {

            try {
                this.zLevel = 100.0F;
                this.itemRender.zLevel = 100.0F;

                if (!this.isPowered()) {
                    drawRect(s.xPos, s.yPos, 16 + s.xPos, 16 + s.yPos, 0x66111111);
                }

                this.zLevel = 0.0F;
                this.itemRender.zLevel = 0.0F;

                // Annoying but easier than trying to splice into render item
                super.drawSlot(new Size1Slot((SlotME) s));

                this.stackSizeRenderer.renderStackSize(this.fontRenderer, ((SlotME) s).getEntry(), s.xPos, s.yPos);

            } catch (final Exception err) {
                AELog.warn("[AppEng] AE prevented crash while drawing slot: " + err);
            }

            return;
        } else {
            try {
                final ItemStack is = s.getStack();
                if (s instanceof AppEngSlot && (((AppEngSlot) s).renderIconWithItem() || is.isEmpty()) && (((AppEngSlot) s).shouldDisplay())) {
                    final AppEngSlot aes = (AppEngSlot) s;
                    if (aes.getIcon() >= 0) {
                        this.bindTexture("guis/states.png");

                        try {
                            final int uv_y = (int) Math.floor(aes.getIcon() / 16);
                            final int uv_x = aes.getIcon() - uv_y * 16;

                            GlStateManager.enableBlend();
                            GlStateManager.disableLighting();
                            GlStateManager.enableTexture2D();
                            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                            final float par1 = aes.xPos;
                            final float par2 = aes.yPos;
                            final float par3 = uv_x * 16;
                            final float par4 = uv_y * 16;

                            final Tessellator tessellator = Tessellator.getInstance();
                            final BufferBuilder vb = tessellator.getBuffer();

                            vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

                            final float f1 = 0.00390625F;
                            final float f = 0.00390625F;
                            final float par6 = 16;
                            vb.pos(par1 + 0, par2 + par6, this.zLevel).tex((par3 + 0) * f, (par4 + par6) * f1).color(1.0f, 1.0f, 1.0f, aes.getOpacityOfIcon()).endVertex();
                            final float par5 = 16;
                            vb.pos(par1 + par5, par2 + par6, this.zLevel).tex((par3 + par5) * f, (par4 + par6) * f1).color(1.0f, 1.0f, 1.0f, aes.getOpacityOfIcon()).endVertex();
                            vb.pos(par1 + par5, par2 + 0, this.zLevel).tex((par3 + par5) * f, (par4 + 0) * f1).color(1.0f, 1.0f, 1.0f, aes.getOpacityOfIcon()).endVertex();
                            vb.pos(par1 + 0, par2 + 0, this.zLevel).tex((par3 + 0) * f, (par4 + 0) * f1).color(1.0f, 1.0f, 1.0f, aes.getOpacityOfIcon()).endVertex();
                            tessellator.draw();

                        } catch (final Exception err) {
                        }
                    }
                }

                if (!is.isEmpty() && s instanceof AppEngSlot) {
                    if (((AppEngSlot) s).getIsValid() == hasCalculatedValidness.NotAvailable) {
                        boolean isValid = s.isItemValid(is) || s instanceof SlotOutput || s instanceof AppEngCraftingSlot || s instanceof SlotDisabled || s instanceof SlotInaccessible || s instanceof SlotFake || s instanceof SlotRestrictedInput || s instanceof SlotDisconnected;
                        if (isValid && s instanceof SlotRestrictedInput) {
                            try {
                                isValid = ((SlotRestrictedInput) s).isValid(is, this.mc.world);
                            } catch (final Exception err) {
                                AELog.debug(err);
                            }
                        }
                        ((AppEngSlot) s).setIsValid(isValid ? hasCalculatedValidness.Valid : hasCalculatedValidness.Invalid);
                    }

                    if (((AppEngSlot) s).getIsValid() == hasCalculatedValidness.Invalid) {
                        this.zLevel = 100.0F;
                        this.itemRender.zLevel = 100.0F;

                        GlStateManager.disableLighting();
                        drawRect(s.xPos, s.yPos, 16 + s.xPos, 16 + s.yPos, 0x66ff6666);
                        GlStateManager.enableLighting();

                        this.zLevel = 0.0F;
                        this.itemRender.zLevel = 0.0F;
                    }
                }
                if (s instanceof SlotPlayerInv || s instanceof SlotPlayerHotBar) {
                    if (!is.isEmpty() && is.getItem() instanceof ItemEncodedPattern) {
                        final ItemEncodedPattern iep = (ItemEncodedPattern) is.getItem();
                        final ItemStack out = iep.getOutput(is);
                        if (!out.isEmpty()) {
                            AppEngSlot appEngSlot = ((AppEngSlot) s);
                            appEngSlot.setDisplay(true);
                            appEngSlot.setReturnAsSingleStack(true);

                            this.zLevel = 100.0F;
                            this.itemRender.zLevel = 100.0F;

                            if (!this.isPowered()) {
                                drawRect(s.xPos, s.yPos, 16 + s.xPos, 16 + s.yPos, 0x66111111);
                            }

                            this.zLevel = 0.0F;
                            this.itemRender.zLevel = 0.0F;

                            // Annoying but easier than trying to splice into render item
                            super.drawSlot(s);

                            if (isShiftKeyDown()) {
                                // getOutput wraps the pattern's output key, so a fluid arrives here as a
                                // placeholder and has to be resolved rather than read (CONTRACT.md §9.1d).
                                this.stackSizeRenderer.renderStackSize(this.fontRenderer, GenericStack.resolveItemStack(out), s.xPos, s.yPos);
                            } else {
                                super.drawSlot(s);
                            }
                            return;
                        }
                    } else {
                        super.drawSlot(s);
                    }
                } else if (s instanceof AppEngSlot) {
                    AppEngSlot appEngSlot = ((AppEngSlot) s);
                    if (s.getStack().isEmpty()) {
                        super.drawSlot(s);
                        return;
                    }
                    appEngSlot.setDisplay(true);
                    appEngSlot.setReturnAsSingleStack(true);

                    this.zLevel = 100.0F;
                    this.itemRender.zLevel = 100.0F;

                    if (!this.isPowered()) {
                        drawRect(s.xPos, s.yPos, 16 + s.xPos, 16 + s.yPos, 0x66111111);
                    }

                    this.zLevel = 0.0F;
                    this.itemRender.zLevel = 0.0F;

                    boolean wasDragSplitting = this.dragSplitting;
                    this.dragSplitting = false; // to prevent the vanilla slot renderer from rendering the stack count during drag splitting, we're re-enabling it later

                    // Annoying but easier than trying to splice into render item
                    super.drawSlot(s);

                    ItemStack stackInSlot = ((AppEngSlot)s).getDisplayStack();
                    ItemStack stackUnderCursor = this.mc.player.inventory.getItemStack();

                    if (wasDragSplitting
                        && this.dragSplittingSlots.contains(s)
                        && this.dragSplittingSlots.size() > 1
                        && !stackUnderCursor.isEmpty()) {
                        if (Container.canAddItemToSlot(s, stackUnderCursor, true) && this.inventorySlots.canDragIntoSlot(s))
                        {
                            drawRect(s.xPos, s.yPos, s.xPos + 16, s.yPos + 16, -2130706433);

                            stackInSlot = stackUnderCursor.copy();
                            Container.computeStackSize(this.dragSplittingSlots, this.dragSplittingLimit, stackInSlot, s.getStack().isEmpty() ? 0 : s.getStack().getCount());
                            int k = Math.min(stackInSlot.getMaxStackSize(), s.getItemStackLimit(stackInSlot));

                            if (stackInSlot.getCount() > k)
                            {
                                stackInSlot.setCount(k);
                            }
                        }
                        else
                        {
                            this.dragSplittingSlots.remove(s);
                            this.updateDragSplitting();
                        }
                    }

                    this.dragSplitting = wasDragSplitting;
                    // Resolve, not fromItemStack: the latter reads a placeholder as the ordinary item it is -
                    // one WrappedGenericStack - so a slot holding a bucket of water drew "1" for "1000".
                    this.stackSizeRenderer.renderStackSize(this.fontRenderer,
                            GenericStack.resolveItemStack(stackInSlot), s.xPos, s.yPos);

                    return;
                } else {
                    super.drawSlot(s);
                }

                return;
            } catch (final Exception err) {
                AELog.warn("[AppEng] AE prevented crash while drawing slot: " + err);
            }
        }
        // do the usual for non-ME Slots.
        super.drawSlot(s);
    }

    protected boolean isPowered() {
        return true;
    }

    public void bindTexture(final String file) {
        final ResourceLocation loc = new ResourceLocation(AppEng.MOD_ID, "textures/" + file);
        this.mc.getTextureManager().bindTexture(loc);
    }

    protected GuiScrollbar getScrollBar() {
        return this.myScrollBar;
    }

    protected void setScrollBar(final GuiScrollbar myScrollBar) {
        this.myScrollBar = myScrollBar;
    }

    protected List<InternalSlotME> getMeSlots() {
        return this.meSlots;
    }

    // TODO: remove this when refactoring slot rendering
    private void updateDragSplitting()
    {
        ItemStack itemstack = this.mc.player.inventory.getItemStack();

        if (!itemstack.isEmpty() && this.dragSplitting)
        {
            if (this.dragSplittingLimit == 2)
            {
                this.dragSplittingRemnant = itemstack.getMaxStackSize();
            }
            else
            {
                this.dragSplittingRemnant = itemstack.getCount();

                for (Slot slot : this.dragSplittingSlots)
                {
                    ItemStack itemstack1 = itemstack.copy();
                    ItemStack itemstack2 = slot.getStack();
                    int i = itemstack2.isEmpty() ? 0 : itemstack2.getCount();
                    Container.computeStackSize(this.dragSplittingSlots, this.dragSplittingLimit, itemstack1, i);
                    int j = Math.min(itemstack1.getMaxStackSize(), slot.getItemStackLimit(itemstack1));

                    if (itemstack1.getCount() > j)
                    {
                        itemstack1.setCount(j);
                    }

                    this.dragSplittingRemnant -= itemstack1.getCount() - i;
                }
            }
        }
    }

    @Override
    @Optional.Method(modid = "mousetweaks")
    public boolean MT_isMouseTweaksDisabled() {
        return false;
    }

    @Override
    @Optional.Method(modid = "mousetweaks")
    public boolean MT_isWheelTweakDisabled() {
        return true;
    }

    @Override
    @Optional.Method(modid = "mousetweaks")
    public Container MT_getContainer() {
        return this.inventorySlots;
    }

    @Override
    @Optional.Method(modid = "mousetweaks")
    public Slot MT_getSlotUnderMouse() {
        return getSlotUnderMouse();
    }

    @Override
    @Optional.Method(modid = "mousetweaks")
    public boolean MT_isCraftingOutput(Slot slot) {
        return slot instanceof SlotOutput || slot instanceof AppEngCraftingSlot;
    }

    @Override
    @Optional.Method(modid = "mousetweaks")
    public boolean MT_isIgnored(Slot slot) {
        return false;
    }

    @Override
    @Optional.Method(modid = "mousetweaks")
    public boolean MT_disableRMBDraggingFunctionality() {
       if (this.dragSplitting && this.dragSplittingButton == 1) {
           this.dragSplitting = false;
           // Don't ignoreMouseUp on slots that can't accept the item. (crafting output, ME slot, etc.)
           if (this.getSlotUnderMouse() != null && this.getSlotUnderMouse().isItemValid(this.mc.player.inventory.getItemStack())) {
               this.ignoreMouseUp = true;
           }
           return true;
       }
       return false;
    }
}
