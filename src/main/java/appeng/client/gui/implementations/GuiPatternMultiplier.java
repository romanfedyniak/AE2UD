/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 * Adapted from NAE2's pattern multi-tool (https://github.com/AE2-UEL/NAE2) by NotMyWing.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.parts.IPart;
import appeng.api.parts.PartItemStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiSmallButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerPatternMultiplier;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.OptionalSlotRestrictedInput;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.patternmultiplier.PatternEdits;
import appeng.helpers.patternmultiplier.PatternMultiplierHost;
import appeng.helpers.patternmultiplier.PatternMultiplierTab;

public class GuiPatternMultiplier extends AEBaseGui implements IJEIGhostIngredients {

    private static final String TEXTURE = "guis/pattern_multiplier.png";
    private static final String LANG = "item.appliedenergistics2.pattern_multiplier.";
    private static final int MULTIPLY_ICON = 6 + 4 * 16;
    private static final int BUTTONS_Y = 92 + ContainerPatternMultiplier.HEADER_EXTRA;
    /** Where the texture's header ends and its first row of slots is drawn. */
    private static final int TEXTURE_HEADER = 17;
    private static final int TEXTURE_PATTERNS_Y = 19;
    private static final int TEXT_BUTTON_X = 118;
    private static final int TEXT_BUTTON_WIDTH = 50;

    private final ContainerPatternMultiplier container;
    private final Map<Target<?>, Object> targetSlots = new HashMap<>();
    private final Map<Slot, PatternEdits.Replacement> replacements = new HashMap<>();
    private final List<GuiImgButton> scaleButtons = new ArrayList<>();
    private GuiTabButton multiplyTab;
    private GuiTabButton replaceTab;
    private GuiTabButton showTool;
    private GuiTabButton showInterface;
    private GuiSmallButton clear;
    private GuiSmallButton replace;

    public GuiPatternMultiplier(final InventoryPlayer inventory, final PatternMultiplierHost host) {
        super(new ContainerPatternMultiplier(inventory, host));
        this.container = (ContainerPatternMultiplier) this.inventorySlots;
        this.xSize = 211;
        this.ySize = 207 + ContainerPatternMultiplier.HEADER_EXTRA;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        this.scaleButtons.clear();

        final ActionItems[] scales = { ActionItems.MULTIPLY_BY_TWO, ActionItems.MULTIPLY_BY_THREE,
                ActionItems.INCREASE_BY_ONE, ActionItems.DIVIDE_BY_TWO, ActionItems.DIVIDE_BY_THREE,
                ActionItems.DECREASE_BY_ONE };
        for (int i = 0; i < scales.length; i++) {
            final GuiImgButton button = new GuiImgButton(this.guiLeft + 8 + i * 18, this.guiTop + BUTTONS_Y,
                    Settings.ACTIONS, scales[i]);
            this.scaleButtons.add(button);
            this.buttonList.add(button);
        }
        this.clear = textButton("clear");
        this.replace = textButton("replace");
        this.buttonList.add(this.clear);
        this.buttonList.add(this.replace);

        // One tab, to the page not shown.
        this.multiplyTab = new GuiTabButton(this.guiLeft + 154, this.guiTop, MULTIPLY_ICON,
                I18n.format(LANG + "tab.multiply"), this.itemRender);
        this.replaceTab = new GuiTabButton(this.guiLeft + 154, this.guiTop,
                AEApi.instance().definitions().items().encodedPattern().maybeStack(1).orElse(ItemStack.EMPTY),
                I18n.format(LANG + "tab.replace"), this.itemRender);
        this.buttonList.add(this.multiplyTab);
        this.buttonList.add(this.replaceTab);
        if (this.container.hasTarget()) {
            this.showTool = new GuiTabButton(this.guiLeft + 129, this.guiTop, this.container.getHost().getItemStack(),
                    I18n.format(LANG + "view.tool"), this.itemRender);
            this.showInterface = new GuiTabButton(this.guiLeft + 129, this.guiTop, this.interfaceIcon(),
                    I18n.format(LANG + "view.interface"), this.itemRender);
            this.buttonList.add(this.showTool);
            this.buttonList.add(this.showInterface);
        }
        this.updateButtons();
    }

    private GuiSmallButton textButton(final String key) {
        final String title = I18n.format(LANG + key);
        return new GuiSmallButton(0, this.guiLeft + TEXT_BUTTON_X, this.guiTop + BUTTONS_Y, TEXT_BUTTON_WIDTH, 16,
                title).setTooltip(title + "\n" + wrap(I18n.format(LANG + key + ".desc")));
    }

    /**
     * Broken into lines of about 30 characters, as every setting button's description is.
     */
    private static String wrap(final String text) {
        final StringBuilder sb = new StringBuilder(text);
        int i = Math.max(0, sb.lastIndexOf("\n"));
        while (i + 30 < sb.length() && (i = sb.lastIndexOf(" ", i + 30)) != -1) {
            sb.replace(i, i + 1, "\n");
        }
        return sb.toString();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.updateButtons();
        this.updateReplacements();
    }

    private void updateButtons() {
        final boolean multiply = this.container.getTab() == PatternMultiplierTab.MULTIPLY;
        for (final GuiImgButton button : this.scaleButtons) {
            button.visible = multiply;
        }
        this.clear.visible = multiply;
        this.replace.visible = !multiply;
        this.replace.enabled = this.replacements.containsValue(PatternEdits.Replacement.OK);
        this.multiplyTab.visible = !multiply;
        this.replaceTab.visible = multiply;
        if (this.showTool != null) {
            this.showTool.visible = this.container.viewingInterface;
            this.showInterface.visible = !this.container.viewingInterface;
        }
    }

    /**
     * Which shown patterns a replace would change, and which it would skip; asked a few times a second rather
     * than every frame, since a crafting pattern is checked against the recipes.
     */
    private void updateReplacements() {
        this.replacements.clear();
        if (this.container.getTab() != PatternMultiplierTab.REPLACE) {
            return;
        }
        final IItemHandler searchReplace = this.container.getHost().getSearchReplace();
        final ItemStack find = searchReplace.getStackInSlot(0);
        final ItemStack replacement = searchReplace.getStackInSlot(1);
        if (find.isEmpty()) {
            return;
        }
        for (final Slot slot : this.inventorySlots.inventorySlots) {
            if (!(slot instanceof OptionalSlotRestrictedInput) || !((OptionalSlotRestrictedInput) slot).isSlotEnabled()
                    || !PatternEdits.isPattern(slot.getStack())) {
                continue;
            }
            final PatternEdits.Replacement result = PatternEdits.replace(slot.getStack().copy(), find, replacement,
                    this.mc.world, false);
            if (result != PatternEdits.Replacement.NONE) {
                this.replacements.put(slot, result);
            }
        }
    }

    private ItemStack interfaceIcon() {
        final IInterfaceHost target = this.container.getTarget();
        if (target instanceof IPart) {
            return ((IPart) target).getItemStack(PartItemStack.NETWORK);
        }
        return AEApi.instance().definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void actionPerformed(final GuiButton button) throws IOException {
        super.actionPerformed(button);
        if (button == this.multiplyTab) {
            this.send(ContainerPatternMultiplier.TAB, String.valueOf(PatternMultiplierTab.MULTIPLY.ordinal()));
        } else if (button == this.replaceTab) {
            this.send(ContainerPatternMultiplier.TAB, String.valueOf(PatternMultiplierTab.REPLACE.ordinal()));
        } else if (button == this.showTool || button == this.showInterface) {
            this.send(ContainerPatternMultiplier.VIEW, "");
        } else if (button == this.clear) {
            this.send(ContainerPatternMultiplier.ACTION, "Clear");
        } else if (button == this.replace) {
            this.send(ContainerPatternMultiplier.ACTION, "Replace");
        } else if (button instanceof GuiImgButton && this.scaleButtons.contains(button)) {
            this.send(ContainerPatternMultiplier.ACTION, scaleOf((ActionItems) ((GuiImgButton) button).getCurrentValue()));
        }
    }

    private static String scaleOf(final ActionItems action) {
        switch (action) {
            case MULTIPLY_BY_TWO:
                return PatternEdits.Scale.MULTIPLY_BY_TWO.name();
            case MULTIPLY_BY_THREE:
                return PatternEdits.Scale.MULTIPLY_BY_THREE.name();
            case INCREASE_BY_ONE:
                return PatternEdits.Scale.INCREASE_BY_ONE.name();
            case DIVIDE_BY_TWO:
                return PatternEdits.Scale.DIVIDE_BY_TWO.name();
            case DIVIDE_BY_THREE:
                return PatternEdits.Scale.DIVIDE_BY_THREE.name();
            default:
                return PatternEdits.Scale.DECREASE_BY_ONE.name();
        }
    }

    private void send(final String name, final String value) {
        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig(name, value));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(I18n.format(LANG + "name"), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);
        for (final Map.Entry<Slot, PatternEdits.Replacement> entry : this.replacements.entrySet()) {
            final Slot slot = entry.getKey();
            final int colour = entry.getValue() == PatternEdits.Replacement.OK ? 0x8000FF00 : 0x80FF0000;
            drawRect(slot.xPos, slot.yPos, slot.xPos + 16, slot.yPos + 16, colour);
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture(TEXTURE);
        final int extra = ContainerPatternMultiplier.HEADER_EXTRA;
        // The header, stretched by repeating a plain line of it, then the rest of the window below.
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, 177, TEXTURE_HEADER);
        for (int line = 0; line < extra; line++) {
            this.drawTexturedModalRect(offsetX, offsetY + TEXTURE_HEADER + line, 0, TEXTURE_HEADER - 1, 177, 1);
        }
        this.drawTexturedModalRect(offsetX, offsetY + TEXTURE_HEADER + extra, 0, TEXTURE_HEADER, 177,
                this.ySize - extra - TEXTURE_HEADER);

        // The texture shows the rows past the first as locked; each open one is drawn over as the first.
        for (int row = 1; row < this.container.getShownRows(); row++) {
            this.drawTexturedModalRect(offsetX + 8, offsetY + ContainerPatternMultiplier.PATTERNS_Y + row * 18, 8,
                    TEXTURE_PATTERNS_Y, 18 * 9 - 1, 17);
        }

        final int cards = this.container.viewingInterface && this.container.getTarget() != null
                ? this.container.getTarget().getInventoryByName("upgrades").getSlots()
                : PatternMultiplierHost.CARD_SLOTS;
        this.drawTexturedModalRect(offsetX + 180, offsetY, 180, 0, 32, 32);
        for (int i = 1; i < cards; i++) {
            this.drawTexturedModalRect(offsetX + 180, offsetY + 7 + i * 18, 180, 7, 32, 26);
        }

        if (this.container.getTab() == PatternMultiplierTab.REPLACE) {
            for (final int x : new int[] { ContainerPatternMultiplier.SEARCH_X, ContainerPatternMultiplier.REPLACE_X }) {
                this.drawTexturedModalRect(offsetX + x - 1, offsetY + ContainerPatternMultiplier.SEARCH_Y - 1, 7, 18,
                        18, 18);
            }
            this.drawTexturedModalRect(offsetX + 28, offsetY + ContainerPatternMultiplier.SEARCH_Y, 187, 35, 22, 15);
        }
    }

    @Override
    public List<Target<?>> getPhantomTargets(final Object ingredient) {
        return this.fakeSlotTargets(ingredient, this.targetSlots);
    }

    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return this.targetSlots;
    }
}
