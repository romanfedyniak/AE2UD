/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui.widgets;

import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.client.gui.AEBaseGui;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Says why a crafting job did not start, over the screen that tried to start it. The plan behind it is kept,
 * so Retry submits that same plan again and Replan is a choice rather than something that happens by itself.
 * <p>
 * Drawn in place rather than as a screen of its own: every screen change in this mod goes through the server,
 * and the plan would not survive one - it lives in the container as a running task.
 */
public class GuiCraftErrorPanel {

    private static final int PANEL_COLOR = 0xF0202020;
    private static final int BORDER_COLOR = 0xFF8B8B8B;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    private final AEBaseGui parent;
    private final ContainerCraftConfirm container;

    private GuiButton retry;
    private GuiButton replan;
    private GuiButton dismiss;
    private int left;
    private int top;
    private int width;
    private int height;

    public GuiCraftErrorPanel(final AEBaseGui parent, final ContainerCraftConfirm container) {
        this.parent = parent;
        this.container = container;
    }

    /**
     * @param left,top,width,height the area to cover, in window coordinates.
     */
    public void initGui(final int left, final int top, final int width, final int height, final List<GuiButton> buttons) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;

        final int row = this.parent.getGuiTop() + top + height - BUTTON_HEIGHT - 6;
        final int totalWidth = 3 * BUTTON_WIDTH + 2 * BUTTON_GAP;
        int x = this.parent.getGuiLeft() + left + (width - totalWidth) / 2;

        this.retry = new GuiButton(0, x, row, BUTTON_WIDTH, BUTTON_HEIGHT, GuiText.CraftErrorRetry.getLocal());
        x += BUTTON_WIDTH + BUTTON_GAP;
        this.replan = new GuiButton(0, x, row, BUTTON_WIDTH, BUTTON_HEIGHT, GuiText.CraftErrorReplan.getLocal());
        x += BUTTON_WIDTH + BUTTON_GAP;
        this.dismiss = new GuiButton(0, x, row, BUTTON_WIDTH, BUTTON_HEIGHT, GuiText.Cancel.getLocal());

        buttons.add(this.retry);
        buttons.add(this.replan);
        buttons.add(this.dismiss);

        // Hidden until there is something to report, so they do not flash on the frame the screen opens.
        this.update();
    }

    public boolean isShowing() {
        return this.errorCode() != null;
    }

    /**
     * Kept in step every frame, since the error arrives from the server between frames.
     */
    public void update() {
        final boolean showing = this.isShowing();
        for (final GuiButton button : new GuiButton[] { this.retry, this.replan, this.dismiss }) {
            if (button != null) {
                button.visible = showing;
                button.enabled = showing;
            }
        }
    }

    /**
     * Drawn in the background layer, in the screen's absolute coordinates, because the buttons are drawn
     * between that layer and the foreground one - a panel drawn in {@code drawFG} covers its own buttons and
     * leaves them looking greyed out. The screen leaves out whatever the panel stands in for instead of
     * drawing it underneath.
     */
    public void drawBG(final int offsetX, final int offsetY) {
        if (!this.isShowing()) {
            return;
        }

        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        final int x = offsetX + this.left;
        final int y = offsetY + this.top;
        Gui.drawRect(x, y, x + this.width, y + this.height, PANEL_COLOR);
        Gui.drawRect(x, y, x + this.width, y + 1, BORDER_COLOR);
        Gui.drawRect(x, y + this.height - 1, x + this.width, y + this.height, BORDER_COLOR);

        final String title = GuiText.CraftErrorTitle.getLocal();
        font.drawString(title, x + (this.width - font.getStringWidth(title)) / 2, y + 8, TEXT_COLOR);

        final List<String> lines = font.listFormattedStringToWidth(this.describe(), this.width - 16);
        int lineY = y + 24;
        for (final String line : lines) {
            font.drawString(line, x + (this.width - font.getStringWidth(line)) / 2, lineY, TEXT_COLOR);
            lineY += font.FONT_HEIGHT + 1;
        }

        // drawRect leaves its colour set, and the buttons drawn over this panel are textured.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * @return true when the button belonged to this panel and the screen should do nothing further with it.
     */
    public boolean actionPerformed(final GuiButton button) {
        if (!this.isShowing()) {
            return false;
        }

        final String action;
        if (button == this.retry) {
            action = "Terminal.Start";
        } else if (button == this.replan) {
            action = "Terminal.Replan";
        } else if (button == this.dismiss) {
            action = "Terminal.ClearError";
        } else {
            return false;
        }

        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig(action, action));
        } catch (final IOException e) {
            AELog.debug(e);
        }
        return true;
    }

    private CraftingSubmitErrorCode errorCode() {
        final int ordinal = this.container.submitError;
        final CraftingSubmitErrorCode[] codes = CraftingSubmitErrorCode.values();
        return ordinal < 0 || ordinal >= codes.length ? null : codes[ordinal];
    }

    private String describe() {
        final CraftingSubmitErrorCode code = this.errorCode();
        if (code == null) {
            return "";
        }

        switch (code) {
            case INCOMPLETE_PLAN:
                return GuiText.CraftErrorIncompletePlan.getLocal();
            case NO_CPU_FOUND:
                return GuiText.CraftErrorNoCpuFound.getLocal();
            case CPU_BUSY:
                return GuiText.CraftErrorCpuBusy.getLocal();
            case CPU_OFFLINE:
                return GuiText.CraftErrorCpuOffline.getLocal();
            case CPU_TOO_SMALL:
                return GuiText.CraftErrorCpuTooSmall.getLocal();
            case MISSING_INGREDIENT:
                return this.container.missingIngredient.isEmpty()
                        ? GuiText.CraftErrorMissingIngredient.getLocal()
                        : GuiText.CraftErrorMissingIngredientNamed.getLocalizedWithArgs(this.container.missingIngredient)
                                .getUnformattedText();
            default:
                return this.describeUnsuitableCpus();
        }
    }

    /**
     * The counts are what turns "no CPU would take it" into something actionable: too small asks for a bigger
     * CPU, busy asks for patience, excluded points at a CPU kept for the other kind of request.
     */
    private String describeUnsuitableCpus() {
        final List<String> counts = new ArrayList<>(4);
        this.appendCount(counts, GuiText.CraftErrorNoSuitableCpuOffline, this.container.unsuitableOffline);
        this.appendCount(counts, GuiText.CraftErrorNoSuitableCpuBusy, this.container.unsuitableBusy);
        this.appendCount(counts, GuiText.CraftErrorNoSuitableCpuTooSmall, this.container.unsuitableTooSmall);
        this.appendCount(counts, GuiText.CraftErrorNoSuitableCpuExcluded, this.container.unsuitableExcluded);

        final String reason = GuiText.CraftErrorNoSuitableCpu.getLocal();
        return counts.isEmpty() ? reason : reason + " (" + String.join(", ", counts) + ")";
    }

    private void appendCount(final List<String> counts, final GuiText text, final int amount) {
        if (amount > 0) {
            counts.add(text.getLocalizedWithArgs(amount).getUnformattedText());
        }
    }
}
