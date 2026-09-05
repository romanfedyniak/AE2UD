/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui.implementations;

import appeng.api.config.ActionItems;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.config.Settings;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiSmallButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerRenamer;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.SlotFake;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.ICustomNameObject;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GuiRenamer extends AEBaseGui implements IJEIGhostIngredients {

    private static final int PLAIN_HEIGHT = 61;
    private static final int ICON_HEIGHT = 150;

    /** The window without an icon slot is kept lower in the same sheet. */
    private static final int PLAIN_V = 160;

    private static final int FIELD_Y = 33;
    private static final int FIELD_HEIGHT = 12;
    private static final int FIELD_X_WITH_ICON = 31;
    private static final int FIELD_WIDTH_WITH_ICON = 180;
    private static final int FIELD_X_PLAIN = 9;
    private static final int FIELD_WIDTH_PLAIN = 202;
    private static final int CONFIRM_X = 216;
    private static final int RESET_X = 232;
    private static final int RESET_Y = 31;

    /** Laid over the icon a machine picked for itself, so it reads as a hint rather than as a choice. */
    private static final int HINT_VEIL = 0x99C6C6C6;

    private final boolean hasIcon;

    private MEGuiTextField textField;
    private GuiButton confirmButton;
    private GuiImgButton resetButton;

    public Map<IGhostIngredientHandler.Target<?>, Object> mapTargetSlot = new HashMap<>();

    public GuiRenamer(InventoryPlayer ip, ICustomNameObject obj) {
        super(new ContainerRenamer(ip, obj));
        this.hasIcon = this.container().hasIcon();
        this.xSize = 256;
        this.ySize = this.hasIcon ? ICON_HEIGHT : PLAIN_HEIGHT;
    }

    private ContainerRenamer container() {
        return (ContainerRenamer) this.inventorySlots;
    }

    @Override
    public void initGui() {
        super.initGui();

        final int fieldX = this.hasIcon ? FIELD_X_WITH_ICON : FIELD_X_PLAIN;
        final int fieldWidth = this.hasIcon ? FIELD_WIDTH_WITH_ICON : FIELD_WIDTH_PLAIN;

        this.textField = new MEGuiTextField(this.fontRenderer, this.guiLeft + fieldX, this.guiTop + FIELD_Y,
                fieldWidth, FIELD_HEIGHT);

        this.textField.setEnableBackgroundDrawing(false);
        this.textField.setMaxStringLength(32);

        this.textField.setFocused(true);

        this.buttonList.add(this.confirmButton = new GuiSmallButton(0, this.guiLeft + CONFIRM_X,
                this.guiTop + FIELD_Y, 12, 12, "↵"));
        this.buttonList.add(this.resetButton = new GuiImgButton(this.guiLeft + RESET_X, this.guiTop + RESET_Y,
                Settings.ACTIONS, ActionItems.RESET_IDENTITY));

        this.container().setTextField(this.textField);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        this.resetButton.enabled = !this.textField.getText().isEmpty()
                || !this.container().getChosenIcon().isEmpty();
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.Renamer.getLocal()), 12, 8, 4210752);

        if (this.hasIcon) {
            this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);
            this.drawIconHint();
        }
    }

    /** What an empty slot means: the picture the machine works out on its own. */
    private void drawIconHint() {
        final ItemStack hint = this.container().getDetectedIcon();

        if (hint.isEmpty() || !this.container().getChosenIcon().isEmpty()) {
            return;
        }

        this.drawItem(ContainerRenamer.ICON_X, ContainerRenamer.ICON_Y, hint);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        drawRect(ContainerRenamer.ICON_X, ContainerRenamer.ICON_Y, ContainerRenamer.ICON_X + 16,
                ContainerRenamer.ICON_Y + 16, HINT_VEIL);
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.color(1, 1, 1, 1);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/renamer.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, this.hasIcon ? 0 : PLAIN_V, this.xSize, this.ySize);
        this.textField.drawTextBox();
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        if (this.textField.isMouseIn(xCoord, yCoord)) {
            if (btn == 1) {
                this.textField.setText("");
            }
            this.textField.mouseClicked(xCoord, yCoord, btn);
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { // Enter
            this.send("QuartzKnife.ReName", this.textField.getText());
            this.mc.player.closeScreen();
        } else if (!this.textField.textboxKeyTyped(character, key)) {
            super.keyTyped(character, key);
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.confirmButton) {
            this.send("QuartzKnife.ReName", this.textField.getText());
            this.mc.player.closeScreen();
        } else if (btn == this.resetButton) {
            this.textField.setText("");
            this.send("Renamer.Reset", "");
        }
    }

    private void send(final String name, final String value) {
        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig(name, value));
        } catch (IOException e) {
            AELog.debug(e);
        }
    }

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        if (!this.hasIcon || !(ingredient instanceof ItemStack stack) || stack.isEmpty()) {
            return Collections.emptyList();
        }

        final List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();

        for (final Slot slot : this.inventorySlots.inventorySlots) {
            if (!(slot instanceof SlotFake fake)) {
                continue;
            }

            final IGhostIngredientHandler.Target<Object> target = new IGhostIngredientHandler.Target<Object>() {
                @Override
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + slot.xPos, getGuiTop() + slot.yPos, 16, 16);
                }

                @Override
                public void accept(final Object dropped) {
                    if (!(dropped instanceof ItemStack dropp) || dropp.isEmpty()) {
                        return;
                    }
                    try {
                        NetworkHandler.instance().sendToServer(new PacketInventoryAction(
                                InventoryAction.PLACE_JEI_GHOST_ITEM, fake, new GenericStack(AEItemKey.of(dropp), 1)));
                    } catch (IOException e) {
                        AELog.debug(e);
                    }
                }
            };

            targets.add(target);
            this.mapTargetSlot.putIfAbsent(target, slot);
        }

        return targets;
    }

    @Override
    public Map<IGhostIngredientHandler.Target<?>, Object> getFakeSlotTargetMap() {
        return this.mapTargetSlot;
    }
}
