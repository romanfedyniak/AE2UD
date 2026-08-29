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


import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiNumberBox;
import appeng.client.gui.widgets.GuiStepButtons;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerPriority;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IPriorityHost;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;


public class GuiPriority extends AEBaseGui {

    private GuiNumberBox priority;
    private GuiTabButton originalGuiBtn;

    private final GuiStepButtons steps = new GuiStepButtons();

    private GuiBridge OriginalGui;

    public GuiPriority(final InventoryPlayer inventoryPlayer, final IPriorityHost te) {
        super(new ContainerPriority(inventoryPlayer, te));
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        super.initGui();

        this.steps.addTo(this.buttonList, this.guiLeft, this.guiTop + 32, this.guiTop + 69);

        final ContainerPriority con = ((ContainerPriority) this.inventorySlots);
        final ItemStack myIcon = con.getPriorityHost().getItemStackRepresentation();
        this.OriginalGui = con.getPriorityHost().getGuiBridge();

        if (this.OriginalGui != null && !myIcon.isEmpty()) {
            this.buttonList.add(this.originalGuiBtn = new GuiTabButton(this.guiLeft + 154, this.guiTop, myIcon, myIcon.getDisplayName(), this.itemRender));
        }

        this.priority = new GuiNumberBox(this.fontRenderer, this.guiLeft + 62, this.guiTop + 57, 59, this.fontRenderer.FONT_HEIGHT, Long.class);
        this.priority.setEnableBackgroundDrawing(false);
        this.priority.setMaxStringLength(16);
        this.priority.setTextColor(0xFFFFFF);
        this.priority.setVisible(true);
        this.priority.setFocused(true);
        ((ContainerPriority) this.inventorySlots).setTextField(this.priority);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(GuiText.Priority.getLocal(), 8, 6, 4210752);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.steps.update();

        this.bindTexture("guis/priority.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.priority.drawTextBox();
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.OriginalGui));
        }

        if (this.steps.isStep(btn)) {
            this.applyStep(btn);
        }
    }

    private void applyStep(final GuiButton btn) {
        try {
            final long current = Long.parseLong(this.trimmed());
            // A priority is a plain number with nothing bounding it either way, and no unit to read it in.
            final String out = Long.toString(this.steps.apply(btn, current, Long.MIN_VALUE, Long.MAX_VALUE, 1));

            this.priority.setText(out);

            NetworkHandler.instance().sendToServer(new PacketValueConfig("PriorityHost.Priority", out));
        } catch (final NumberFormatException e) {
            // nope..
            this.priority.setText("0");
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    /**
     * The field as a number, less the leading zeros a typed digit leaves in front of one.
     */
    private String trimmed() {
        String out = this.priority.getText();

        boolean fixed = false;
        while (out.startsWith("0") && out.length() > 1) {
            out = out.substring(1);
            fixed = true;
        }

        if (fixed) {
            this.priority.setText(out);
        }

        return out.isEmpty() ? "0" : out;
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if ((key == 211 || key == 205 || key == 203 || key == 14 || character == '-' || Character.isDigit(character)) && this.priority
                    .textboxKeyTyped(character, key)) {
                try {
                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PriorityHost.Priority", this.trimmed()));
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            } else {
                super.keyTyped(character, key);
            }
        }
    }

    protected String getBackground() {
        return "guis/priority.png";
    }
}
