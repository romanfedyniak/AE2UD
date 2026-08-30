/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
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
import appeng.api.stacks.AEKey;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.GuiImageExport;
import appeng.client.gui.IKeyUnderMouse;
import appeng.client.gui.widgets.GuiCraftErrorPanel;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.client.gui.widgets.GuiCraftPriorityButton;
import appeng.client.gui.widgets.GuiIconButton;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.GuiCraftingPlanTree;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerCraftingTree;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.crafting.tree.CraftingPlanTree;
import appeng.util.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * The crafting plan drawn as a tree. The same container as the plan list, so switching between the two
 * neither recalculates the job nor loses the chosen CPU.
 */
public class GuiCraftingTree extends AEBaseGui implements IKeyUnderMouse {

    private static final int MIN_WIDTH = 238;
    private static final int MAX_WIDTH = 480;
    private static final int IMAGE_PADDING = 8;
    private static final float IMAGE_SCALE = 2.0f;
    private static final int SWITCH_VIEW_ICON = 13 * 16 + 3;
    private static final int SAVE_IMAGE_ICON = 8 * 16 + 4;
    private static final int MIN_HEIGHT = 160;
    private static final int VERTICAL_MARGIN = 24;
    private static final int BORDER = 3;
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 28;
    /** Cancel against the left edge and Start against the right, as on the plan screen. */
    private static final int BUTTON_MARGIN = 6;
    private static final int PRIORITY_GAP = 4;
    private static final int BUTTON_WIDTH = 54;

    private static final int CANVAS_COLOR = 0xFF3B3B3B;
    private static final int SEARCH_X = BORDER + 5;
    private static final int SEARCH_Y = BORDER + 6;
    private static final int SEARCH_WIDTH = 120;
    private static final int SEARCH_HEIGHT = 12;
    /** Where the terminal texture keeps the frame around its own search field. */
    private static final int FRAME_U = 79;
    private static final int FRAME_V = 3;
    private static final int FRAME_SPAN = 92;
    private static final int FRAME_CAP = 8;
    private static final int FRAME_HEIGHT = 14;

    private final ContainerCraftingTree container;
    private final GuiCraftingCPUTable cpuTable;
    private final GuiCraftErrorPanel errorPanel;
    private final GuiCraftingPlanTree tree;

    private MEGuiTextField searchField;
    private GuiTabButton back;
    private GuiButton start;
    private GuiCraftPriorityButton priority;
    private GuiButton cancel;
    private GuiButton missingOnly;
    private GuiImgButton terminalStyleBox;
    private GuiIconButton saveImage;

    private boolean missingOnlyChosen;

    /** Where Cancel leads when no amount screen preceded this job. Null when there is nowhere to go back to. */
    private final GuiBridge originalGui;

    public GuiCraftingTree(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftingTree(inventoryPlayer, te));

        this.originalGui = GuiBridge.terminalFor(te);
        this.container = (ContainerCraftingTree) this.inventorySlots;
        this.cpuTable = new GuiCraftingCPUTable(this, this.container);
        this.errorPanel = new GuiCraftErrorPanel(this, this.container);
        this.tree = new GuiCraftingPlanTree(this);
    }

    @Override
    public void initGui() {
        final TerminalStyle style = (TerminalStyle) AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);

        this.xSize = Math.max(MIN_WIDTH,
                Math.min(MAX_WIDTH, this.width - 2 * (GuiCraftingCPUTable.WIDTH + 8)));
        this.ySize = Math.max(MIN_HEIGHT, style.getRows(this.height - 2 * VERTICAL_MARGIN));

        super.initGui();

        this.cpuTable.initGui(Math.max(1, (this.ySize - GuiCraftingCPUTable.FIXED_HEIGHT) / 23), this.buttonList);

        // Placed in the window's own coordinates: the foreground layer this is drawn in is already
        // translated there, and so are the clicks handed to it.
        this.searchField = new MEGuiTextField(this.fontRenderer, SEARCH_X, SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT) {
            @Override
            public void onTextChange(final String oldText) {
                GuiCraftingTree.this.tree.updateSearch(this.getText());
            }
        };
        // The window draws the terminals' sunken frame behind it; the field keeps its own fill, which is
        // what darkens when it takes the keyboard. Only the black inner box is left off.
        this.searchField.setEnableBackgroundDrawing(false);
        // Held keys have to repeat in the search field, as they do on every other screen with one.
        Keyboard.enableRepeatEvents(true);

        this.back = new GuiTabButton(this.guiLeft + this.xSize - 25, this.guiTop - 4, SWITCH_VIEW_ICON,
                GuiText.CraftingPlan.getLocal(), this.itemRender);
        this.back.setHideEdge(1);
        this.buttonList.add(this.back);

        final int buttonTop = this.guiTop + this.ySize - 24;
        this.start = new GuiButton(0, this.guiLeft + this.xSize - BUTTON_MARGIN - BUTTON_WIDTH, buttonTop,
                BUTTON_WIDTH, 20, GuiText.Start.getLocal());
        this.buttonList.add(this.start);

        this.priority = new GuiCraftPriorityButton(0, buttonTop + 2);
        this.buttonList.add(this.priority);

        // Only when there is a screen to go back to, the same condition the plan screen puts on its own.
        if (this.originalGui != null || this.container.hasAmountScreen) {
            this.cancel = new GuiButton(0, this.guiLeft + BUTTON_MARGIN, buttonTop, BUTTON_WIDTH, 20,
                    GuiText.Cancel.getLocal());
            this.buttonList.add(this.cancel);
        }

        // Over the tree canvas, since Start can fail from this screen just as it can from the plan.
        this.errorPanel.initGui(6, 36, this.xSize - 12, this.ySize - 66, this.buttonList);

        // Under the terminal-style button, on the strip outside the window where this screen keeps its
        // own controls.
        this.saveImage = new GuiIconButton(this.guiLeft + this.xSize, this.guiTop + 28, SAVE_IMAGE_ICON,
                GuiText.SaveAsImage.getLocal());
        this.buttonList.add(this.saveImage);

        this.missingOnly = new GuiButton(0, this.guiLeft + this.xSize - 152, this.guiTop + BORDER + 3, 118, 20,
                GuiText.ShowMissingOnly.getLocal());
        this.buttonList.add(this.missingOnly);

        this.terminalStyleBox = new GuiImgButton(this.guiLeft + this.xSize, this.guiTop + 8,
                Settings.TERMINAL_STYLE, style);
        this.buttonList.add(this.terminalStyleBox);

        this.tree.setBounds(BORDER + 2, HEADER_HEIGHT,
                this.xSize - 2 * (BORDER + 2), this.ySize - HEADER_HEIGHT - FOOTER_HEIGHT);
    }

    public void postTree(final CraftingPlanTree tree) {
        this.tree.setTree(tree);

        // A plan that came up short is the reason this screen gets opened, so it opens on what is short.
        if (!this.missingOnlyChosen) {
            this.tree.setMissingOnly(this.tree.hasMissing());
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        // Every frame, with the cursor where it is now - see GuiCraftingPlanTree.mouseMoved.
        this.tree.mouseMoved(mouseX, mouseY);

        this.cpuTable.updateScrollRange();

        this.errorPanel.update();
        // A plan that came up short can be started from here on the same terms as from the plan list.
        final boolean force = this.container.isSimulation() && !this.container.hasNoCPU();
        this.start.enabled = !this.container.hasNoCPU();
        this.start.displayString = (force ? GuiText.ForceStart : GuiText.Start).getLocal();
        // Grows leftwards for the longer label, keeping its right edge where the eye expects it.
        this.start.width = Math.max(BUTTON_WIDTH,
                this.fontRenderer.getStringWidth(this.start.displayString) + 12);
        this.start.x = this.guiLeft + this.xSize - BUTTON_MARGIN - this.start.width;
        // Follows Start leftwards, as it does on the plan screen. The number is the container's, and this
        // screen's container is the plan screen's, so the two always agree.
        this.priority.setPriority(this.container.getCraftPriority());
        this.priority.x = this.start.x - PRIORITY_GAP - this.priority.width;
        this.missingOnly.enabled = this.tree.hasMissing();
        this.missingOnly.displayString = this.missingOnly.enabled
                ? GuiText.ShowMissingOnly.getLocal() + ": "
                        + (this.tree.isMissingOnly() ? GuiText.Yes.getLocal() : GuiText.No.getLocal())
                : GuiText.NothingMissing.getLocal();

        super.drawScreen(mouseX, mouseY, btn);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        drawPanel(offsetX, offsetY, this.xSize, this.ySize);
        drawRect(offsetX + BORDER + 2, offsetY + HEADER_HEIGHT,
                offsetX + this.xSize - BORDER - 2, offsetY + this.ySize - FOOTER_HEIGHT, CANVAS_COLOR);
        // drawRect leaves its colour set, and everything below here is textured.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawSearchFrame(offsetX + SEARCH_X - 1, offsetY + SEARCH_Y - 1);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.cpuTable.drawBG(offsetX, offsetY);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        this.errorPanel.drawBG(offsetX, offsetY);
    }

    /**
     * The search field's sunken frame, taken straight out of the terminal's own texture: a left cap, the
     * middle repeated across, and a right cap. Same pixels, so it reads as the same field.
     */
    private void drawSearchFrame(final int x, final int y) {
        this.bindTexture("guis/terminal.png");

        final int width = SEARCH_WIDTH + 2;
        this.drawTexturedModalRect(x, y, FRAME_U, FRAME_V, FRAME_CAP, FRAME_HEIGHT);

        int drawn = FRAME_CAP;
        while (drawn < width - FRAME_CAP) {
            final int piece = Math.min(FRAME_CAP, width - FRAME_CAP - drawn);
            this.drawTexturedModalRect(x + drawn, y, FRAME_U + FRAME_CAP, FRAME_V, piece, FRAME_HEIGHT);
            drawn += piece;
        }

        this.drawTexturedModalRect(x + width - FRAME_CAP, y, FRAME_U + FRAME_SPAN - FRAME_CAP, FRAME_V,
                FRAME_CAP, FRAME_HEIGHT);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.cpuTable.drawFG(mouseX, mouseY);

        // The panel stands in for the tree, and is drawn in the background layer so its own buttons stay on
        // top of it. Nothing of the tree is drawn underneath.
        if (this.errorPanel.isShowing()) {
            return;
        }

        this.searchField.drawTextBox();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        this.tree.draw(mouseX, mouseY);

        if (!this.tree.hasTree()) {
            final String message = this.tree.isMissingOnly()
                    ? GuiText.NoCraftingJobs.getLocal()
                    : GuiText.CalculatingWait.getLocal();
            this.fontRenderer.drawString(message, BORDER + 8, HEADER_HEIGHT + 8, 0xFFFFFF);
        }

        final String cpuTooltip = this.cpuTable.getTooltip(mouseX, mouseY);
        if (cpuTooltip != null) {
            this.drawTooltip(mouseX - offsetX, mouseY - offsetY, cpuTooltip);
            return;
        }

        final String treeTooltip = this.tree.getTooltip(mouseX, mouseY);
        if (treeTooltip != null) {
            this.drawTooltip(mouseX - offsetX, mouseY - offsetY, treeTooltip);
        }
    }

    /**
     * Lets HEI look recipes up for whatever the cursor is over, the same way it does over a slot.
     */
    @Nullable
    @Override
    public AEKey getKeyUnderMouse(final int mouseX, final int mouseY) {
        return this.tree.getKeyUnderMouse(mouseX, mouseY);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (this.cpuTable.actionPerformed(btn)) {
            return;
        }

        if (this.errorPanel.actionPerformed(btn)) {
            return;
        }

        if (btn == this.terminalStyleBox) {
            final TerminalStyle current = (TerminalStyle) AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
            final TerminalStyle next = (TerminalStyle) Platform.rotateEnum(current, Mouse.isButtonDown(1),
                    Settings.TERMINAL_STYLE.getPossibleValues());
            AEConfig.instance().getConfigManager().putSetting(Settings.TERMINAL_STYLE, next);
            this.buttonList.clear();
            this.initGui();
            return;
        }

        if (btn == this.back) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_CRAFTING_CONFIRM));
        } else if (btn == this.cancel) {
            // Back to the order rather than out of it, and out to the terminal only when no amount screen
            // preceded this job - exactly what Cancel does on the plan screen.
            if (this.container.hasAmountScreen) {
                NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_CRAFTING_AMOUNT));
            } else if (this.originalGui != null) {
                NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
            }
        } else if (btn == this.priority) {
            this.mc.displayGuiScreen(new GuiCraftPriority(this, this.mc.player.inventory,
                    AEApi.instance().definitions().blocks().craftingUnit().maybeStack(1).orElse(ItemStack.EMPTY),
                    this.container));
        } else if (btn == this.start) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("Terminal.Start",
                        this.container.isSimulation() ? "Force" : "Start"));
            } catch (final Throwable e) {
                AELog.debug(e);
            }
        } else if (btn == this.saveImage) {
            GuiImageExport.save(this.tree.createImage(IMAGE_PADDING, IMAGE_SCALE), "-crafting-tree");
        } else if (btn == this.missingOnly) {
            this.missingOnlyChosen = true;
            this.tree.setMissingOnly(!this.tree.isMissingOnly());
        }
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (this.cpuTable.keyTyped(character, key)) {
            return;
        }

        if (this.searchField.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.tree.goToMatch(!isShiftKeyDown());
                return;
            }
            if (key != Keyboard.KEY_ESCAPE && this.searchField.textboxKeyTyped(character, key)) {
                return;
            }
        }

        super.keyTyped(character, key);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        super.mouseClicked(xCoord, yCoord, btn);

        this.searchField.mouseClicked(xCoord - this.guiLeft, yCoord - this.guiTop, btn);
        this.cpuTable.mouseClicked(xCoord, yCoord, btn);
        this.tree.mouseClicked(xCoord, yCoord);
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int c, final long d) {
        super.mouseClickMove(x, y, c, d);
        this.cpuTable.mouseClickMove(x, y);
    }

    @Override
    protected void mouseReleased(final int x, final int y, final int which) {
        super.mouseReleased(x, y, which);
        this.tree.mouseReleased();
    }

    @Override
    public void handleMouseInput() throws IOException {
        final int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
        final int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        final int wheel = Mouse.getEventDWheel();

        if (this.cpuTable.handleMouseWheel(x, y, wheel) || this.tree.mouseWheel(x, y, wheel)) {
            return;
        }

        super.handleMouseInput();
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> area = new ArrayList<>();
        area.add(this.cpuTable.getExclusionArea());
        addButtonArea(area, this.terminalStyleBox);
        addButtonArea(area, this.back);
        addButtonArea(area, this.saveImage);
        return area;
    }
}
