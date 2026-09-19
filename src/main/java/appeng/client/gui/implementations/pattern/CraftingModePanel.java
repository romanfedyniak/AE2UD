/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.gui.implementations.pattern;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;

import appeng.api.config.FluidSubstitution;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.api.patterns.PatternEncodingMode;
import appeng.api.patterns.client.IPatternTerminalScreen;
import appeng.api.patterns.client.PatternModePanel;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.ContainerNull;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.PatternHelper;
import appeng.helpers.encoding.CraftingEncodingMode;

/** The three-by-three grid, its two substitution toggles and the hint that says what they will act on. */
public class CraftingModePanel extends PatternModePanel {

    private static final int HEIGHT = 81;
    private static final int FABRICATED_SLOT_TINT = 0x8032CD32;

    private GuiImgButton substitutionsEnabledBtn;
    private GuiImgButton substitutionsDisabledBtn;
    private GuiImgButton fluidSubstitutionsEnabledBtn;
    private GuiImgButton fluidSubstitutionsDisabledBtn;

    private GenericStack[] fabricatedSlots;
    private int fabricatedFrom;

    public CraftingModePanel(final PatternEncodingMode mode, final IPatternTerminalScreen screen) {
        super(mode, screen);
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public String getBackground() {
        return "guis/pattern.png";
    }

    /** The matrix is drawn into the window texture and never moves, so there is nothing to place. */
    @Override
    public void layOut() {
    }

    @Override
    public void addButtons(final List<GuiButton> buttons) {
        final int left = this.getScreen().getGuiLeft();
        final int top = this.getScreen().getGuiTop() + this.getScreen().getYSize() - 163;

        this.substitutionsEnabledBtn = halfSize(new GuiImgButton(left + 84, top, Settings.ACTIONS, ItemSubstitution.ENABLED));
        this.substitutionsDisabledBtn = halfSize(new GuiImgButton(left + 84, top, Settings.ACTIONS, ItemSubstitution.DISABLED));
        this.fluidSubstitutionsEnabledBtn = halfSize(new GuiImgButton(left + 94, top, Settings.ACTIONS, FluidSubstitution.ENABLED));
        this.fluidSubstitutionsDisabledBtn = halfSize(new GuiImgButton(left + 94, top, Settings.ACTIONS, FluidSubstitution.DISABLED));

        buttons.add(this.substitutionsEnabledBtn);
        buttons.add(this.substitutionsDisabledBtn);
        buttons.add(this.fluidSubstitutionsEnabledBtn);
        buttons.add(this.fluidSubstitutionsDisabledBtn);
    }

    private static GuiImgButton halfSize(final GuiImgButton button) {
        button.setHalfSize(true);
        return button;
    }

    @Override
    public void updateButtons() {
        final boolean substitute = this.getScreen().getHost().isSubstitution();
        this.substitutionsEnabledBtn.visible = substitute;
        this.substitutionsDisabledBtn.visible = !substitute;

        final boolean fluids = this.getScreen().getHost().isFluidSubstitution();
        this.fluidSubstitutionsEnabledBtn.visible = fluids;
        this.fluidSubstitutionsDisabledBtn.visible = !fluids;

        this.getScreen().placeButton(IPatternTerminalScreen.TerminalButton.CLEAR, 74, this.getScreen().getYSize() - 163);
    }

    @Override
    public boolean actionPerformed(final GuiButton button) {
        final String name;
        final boolean on;

        if (button == this.substitutionsEnabledBtn || button == this.substitutionsDisabledBtn) {
            name = "PatternTerminal.Substitute";
            on = button == this.substitutionsDisabledBtn;
        } else if (button == this.fluidSubstitutionsEnabledBtn || button == this.fluidSubstitutionsDisabledBtn) {
            name = "PatternTerminal.SubstituteFluids";
            on = button == this.fluidSubstitutionsDisabledBtn;
        } else {
            return false;
        }

        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig(name, on ? "1" : "0"));
        } catch (final IOException e) {
            AELog.error(e);
        }
        return true;
    }

    /**
     * Tints the ingredients the network would fill in for, while the fluid-substitution button is under the
     * cursor. Decided by {@link PatternHelper#findFabricatedSlots}, the same rule the pattern itself will use
     * once encoded - so what lights up green here is exactly what the toggle will act on, and a container the
     * recipe does not simply empty stays dark instead of promising something.
     */
    @Override
    public void drawForeground(final int mouseX, final int mouseY) {
        final GuiImgButton button = this.fluidSubstitutionsEnabledBtn.visible
                ? this.fluidSubstitutionsEnabledBtn
                : this.fluidSubstitutionsDisabledBtn;

        if (!button.visible || !button.isMouseOver()) {
            this.fabricatedSlots = null;
            return;
        }

        final List<Slot> grid = this.getScreen().getGridSlots(CraftingEncodingMode.GRID);

        // Finding the recipe is a scan of every one registered, so it is redone only when the grid actually
        // changed - hovering a still grid costs nothing after the first frame.
        final int contents = contentsOf(grid);
        if (this.fabricatedSlots == null || contents != this.fabricatedFrom) {
            this.fabricatedSlots = findFabricated(grid);
            this.fabricatedFrom = contents;
        }

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        for (int i = 0; i < grid.size() && i < this.fabricatedSlots.length; i++) {
            if (this.fabricatedSlots[i] != null) {
                final Slot slot = grid.get(i);
                Gui.drawRect(slot.xPos, slot.yPos, slot.xPos + 16, slot.yPos + 16, FABRICATED_SLOT_TINT);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static GenericStack[] findFabricated(final List<Slot> slots) {
        final InventoryCrafting grid = new InventoryCrafting(new ContainerNull(), 3, 3);

        for (int i = 0; i < slots.size() && i < 9; i++) {
            grid.setInventorySlotContents(i, slots.get(i).getStack().copy());
        }

        return PatternHelper.findFabricatedSlots(grid, CraftingManager.findMatchingRecipe(grid, Minecraft.getMinecraft().world));
    }

    private static int contentsOf(final List<Slot> slots) {
        int hash = 1;

        for (final Slot slot : slots) {
            final ItemStack is = slot.getStack();
            hash = hash * 31 + (is.isEmpty() ? 0 : Item.getIdFromItem(is.getItem()) * 31 + is.getItemDamage());
        }

        return hash;
    }
}
