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


import appeng.api.config.BlockingMode;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.upgrades.CardTraits;
import appeng.api.upgrades.UpgradeCards;
import appeng.client.gui.widgets.GuiCraftPriorityButton;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiImgLabel;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerInterface;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.OptionalSlotRestrictedInput;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.IInterfaceHost;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import org.lwjgl.input.Mouse;

import java.io.IOException;


public class GuiInterface extends GuiUpgradeable {

    private GuiTabButton priority;
    private GuiCraftPriorityButton craftPriority;
    private GuiImgButton UnlockMode;
    private GuiImgButton BlockMode;
    private GuiToggleButton interfaceMode;
    private GuiImgLabel lockReason;

    private static final int STRANDED_COLOR = 0x80FF0000;
    private static final long STRANDED_SHOWN_FOR = 1500;

    /** The first slot a card the player just tried to pull would have stranded, and until when. */
    private int strandedFrom = -1;
    private long strandedUntil;

    public GuiInterface(final InventoryPlayer inventoryPlayer, final IInterfaceHost te) {
        super(new ContainerInterface(inventoryPlayer, te));
        this.ySize = 256;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.addLabel();
    }

    @Override
    protected void addButtons() {
        this.priority = GuiTabButton.priority(this.guiLeft + 154, this.guiTop, this.itemRender);
        this.buttonList.add(this.priority);

        this.BlockMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.BLOCK, BlockingMode.NO);
        this.buttonList.add(this.BlockMode);

        this.UnlockMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 26, Settings.UNLOCK, LockCraftingMode.NONE);
        this.buttonList.add(this.UnlockMode);

        this.interfaceMode = new GuiToggleButton(this.guiLeft - 18, this.guiTop + 44, 84, 85, GuiText.PatternAccessTerminal.getLocal(), GuiText.PatternAccessTerminalHint.getLocal());
        this.buttonList.add(this.interfaceMode);

        // Last in the column, and only while the card that lets an interface order a craft is in.
        this.craftPriority = new GuiCraftPriorityButton(this.guiLeft - 18, this.guiTop + 62);
        this.buttonList.add(this.craftPriority);
    }

    protected void addLabel() {
        if (lockReason != null) {
            labelList.remove(this.lockReason);
        }
        // Beside the title, where it covers nothing the window draws. Three down, below the frame: the open
        // padlock's shackle starts in the top row of its cell.
        final int titleWidth = this.fontRenderer.getStringWidth(this.getGuiDisplayName(GuiText.Interface.getLocal()));
        this.lockReason = new GuiImgLabel(this.fontRenderer, guiLeft + 8 + titleWidth + 2, guiTop + 3, Settings.UNLOCK, LockCraftingMode.NONE);
        this.lockReason.setVisibility(false);
        labelList.add(lockReason);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (this.BlockMode != null) {
            this.BlockMode.set(((ContainerInterface) this.cvb).getBlockingMode());
        }

        if (this.UnlockMode != null) {
            this.UnlockMode.set(((ContainerInterface) this.cvb).getUnlockMode());

            if (this.lockReason != null) {
                if (this.UnlockMode.getCurrentValue() == LockCraftingMode.NONE) {
                    this.lockReason.setVisibility(false);
                } else {
                    this.lockReason.setVisibility(true);
                    this.lockReason.set(((ContainerInterface) this.cvb).getCraftingLockedReason());
                }
            }
        }

        if (this.interfaceMode != null) {
            this.interfaceMode.setState(((ContainerInterface) this.cvb).getPatternAccessMode() == YesNo.YES);
        }

        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.Interface.getLocal()), 8, 6, 4210752);

        this.fontRenderer.drawString(GuiText.Config.getLocal(), 8, 6 + 11 + 7, 4210752);
        this.fontRenderer.drawString(GuiText.StoredItems.getLocal(), 8, 6 + 60 + 7, 4210752);
        this.fontRenderer.drawString(GuiText.Patterns.getLocal(), 8, 6 + 73 + 7, 4210752);

        this.drawStranded();
    }

    /** The patterns keeping a card in place. A refused click says nothing on its own. */
    private void drawStranded() {
        if (this.strandedFrom < 0 || System.currentTimeMillis() > this.strandedUntil) {
            return;
        }

        for (final Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof OptionalSlotRestrictedInput && slot.getSlotIndex() >= this.strandedFrom
                    && slot.getHasStack()) {
                final AppEngSlot ae = (AppEngSlot) slot;
                drawRect(ae.xPos, ae.yPos, ae.xPos + 16, ae.yPos + 16, STRANDED_COLOR);
            }
        }

        // drawRect leaves its colour set on whatever is textured next.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void handleMouseClick(final Slot slot, final int slotIdx, final int mouseButton,
            final ClickType clickType) {
        // Asked before the click is handled, because a refused one is handled by doing nothing.
        if (slot instanceof ContainerInterface.PatternAwareUpgradeSlot && slot.getHasStack()
                && !slot.canTakeStack(this.mc.player)) {
            final int stranded = ((ContainerInterface) this.cvb).getDuality()
                    .firstStrandedPattern(slot.getSlotIndex());

            if (stranded >= 0) {
                this.strandedFrom = stranded;
                this.strandedUntil = System.currentTimeMillis() + STRANDED_SHOWN_FOR;
            }
        }

        super.handleMouseClick(slot, slotIdx, mouseButton, clickType);
    }

    @Override
    protected void handleButtonVisibility() {
        super.handleButtonVisibility();

        if (this.craftPriority != null) {
            final boolean carded = this.bc.isInstalled(CardTraits.CRAFTING);
            this.craftPriority.visible = carded;
            this.craftPriority.enabled = carded;
            this.craftPriority.setPriority(this.cvb.getCraftPriority());
        }
    }

    @Override
    protected String getBackground() {
        int upgrades = ((ContainerInterface) this.cvb).getPatternUpgrades();
        if (upgrades == 0) {
            return "guis/newinterface.png";
        } else {
            return "guis/newinterface" + upgrades + ".png";
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.priority) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
        }

        if (btn == this.craftPriority) {
            this.mc.displayGuiScreen(new GuiCraftPriority(this, this.mc.player.inventory,
                    UpgradeCards.crafting(), this.cvb));
            return;
        }

        if (btn == this.interfaceMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(Settings.PATTERN_ACCESS_TERMINAL, backwards));
        }

        if (btn == this.BlockMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.BlockMode.getSetting(), backwards));
        }

        if (btn == this.UnlockMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.UnlockMode.getSetting(), backwards));
        }
    }

}
