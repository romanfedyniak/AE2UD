/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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

package appeng.client.gui.implementations;


import appeng.api.config.FluidSubstitution;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.AEGuiHandler;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerPatternView;
import appeng.core.localization.GuiText;
import appeng.integration.modules.jei.JEIPlugin;
import appeng.util.Platform;
import mezz.jei.api.recipe.IFocus;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import java.io.IOException;
import java.util.List;


/**
 * Shows what one encoded pattern makes and what it takes, without the pattern having to be carried to a
 * terminal first. Read-only on purpose: changing a pattern is what the pattern terminal is for.
 * <p>
 * The screen is summoned over whichever one the player was already in and puts that one back when it
 * closes, so looking into a pattern never costs the terminal you were browsing. Nothing here reaches a
 * server - see {@link ContainerPatternView}.
 */
public class GuiPatternView extends AEBaseGui {

    /**
     * The arrow between the two grids. Its shape is the pattern terminal's, but it is drawn rather than
     * taken out of that texture: the texture carries its own background with it, a shade darker than this
     * panel, which showed as a grey patch around the arrow.
     */
    private static final int ARROW_COLOR = 0xFF8B8B8B;
    /** Where the shaft ends and the head begins, and the row the point sits on. */
    private static final int ARROW_HEAD_LEFT = 14;
    private static final int ARROW_MIDDLE = 7;

    /** The pattern terminal's own tint for the same hint, so both screens mark the same thing alike. */
    private static final int FABRICATED_SLOT_TINT = 0x8032CD32;

    private final ContainerPatternView view;
    private final GuiScreen parent;
    private final boolean crafting;
    private final boolean substitutes;
    private final boolean substitutesFluids;
    private final boolean[] fabricated;

    private GuiImgButton fluidIcon;

    public GuiPatternView(final InventoryPlayer inventoryPlayer, final ICraftingPatternDetails details,
            final List<GenericStack> inputs, final GenericStack[] outputs, final GuiScreen parent) {
        super(new ContainerPatternView(inventoryPlayer, details, inputs, outputs));

        this.view = (ContainerPatternView) this.inventorySlots;
        this.parent = parent;
        this.crafting = details.isCraftable();
        this.substitutes = details.canSubstitute();
        this.substitutesFluids = details.canSubstituteFluids();

        // Which ingredients the network fills in for out of its own tanks rather than handing over the
        // container. The pattern terminal works this out with PatternHelper.findFabricatedSlots because
        // nothing is encoded yet; an encoded pattern already carries the answer, and it is the same one.
        final GenericStack[] sparse = details.getInputs();
        this.fabricated = new boolean[this.crafting ? sparse.length : 0];

        for (int i = 0; i < this.fabricated.length; i++) {
            this.fabricated[i] = details.getPatternInputs().get(i).isFabricated();
        }
    }

    @Override
    public void initGui() {
        // The grids decide the width, unless the title is wider than they are - a one-in, one-out pattern
        // is narrower than its own heading.
        this.xSize = Math.max(this.view.getWidth(),
                this.fontRenderer.getStringWidth(this.title()) + 2 * ContainerPatternView.MARGIN);
        this.ySize = this.view.getHeight();

        super.initGui();

        if (this.view.hasHeader()) {
            // The same icons the pattern terminal toggles these settings with, so the two screens say the
            // same thing the same way. They are indicators here, not controls - nothing acts on a click -
            // and each carries the setting's own tooltip, which explains what it does.
            this.addFlagIcon(0, this.substitutes ? ItemSubstitution.ENABLED : ItemSubstitution.DISABLED);
            this.fluidIcon = this.addFlagIcon(1,
                    this.substitutesFluids ? FluidSubstitution.ENABLED : FluidSubstitution.DISABLED);
        }
    }

    private GuiImgButton addFlagIcon(final int column, final Enum<?> value) {
        final GuiImgButton icon = new GuiImgButton(
                this.guiLeft + ContainerPatternView.MARGIN
                        + column * (ContainerPatternView.ICON_SIZE + ContainerPatternView.ICON_GAP),
                this.guiTop + ContainerPatternView.HEADER_TOP,
                Settings.ACTIONS, value);

        this.buttonList.add(icon);
        return icon;
    }

    private String title() {
        return (this.crafting ? GuiText.CraftingPattern : GuiText.ProcessingPattern).getLocal();
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        drawPanel(offsetX, offsetY, this.xSize, this.ySize);

        for (final Slot slot : this.inventorySlots.inventorySlots) {
            drawSlotWell(offsetX + slot.xPos, offsetY + slot.yPos);
        }

        this.drawArrow(offsetX + this.view.getArrowLeft(), offsetY + this.view.getArrowTop());

        // drawRect leaves its colour set, and the slot contents are drawn textured after this.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** A shaft three rows deep, and a head that tapers to its point one row at a time. */
    private void drawArrow(final int left, final int top) {
        for (int row = 0; row < ContainerPatternView.ARROW_HEIGHT; row++) {
            final int taper = Math.abs(row - ARROW_MIDDLE);
            final int y = top + row;

            if (taper <= 1) {
                drawRect(left, y, left + ARROW_HEAD_LEFT, y + 1, ARROW_COLOR);
            }

            drawRect(left + ARROW_HEAD_LEFT, y, left + ContainerPatternView.ARROW_WIDTH - taper, y + 1,
                    ARROW_COLOR);
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // Already translated to the window origin, so these are window coordinates.
        this.fontRenderer.drawString(this.title(), ContainerPatternView.MARGIN, 6, 0x404040);

        this.drawFluidSubstitutionHint();
    }

    /**
     * Tints the ingredients the network fills in for, while the fluid-substitution icon is under the cursor
     * - the same hint the pattern terminal gives while a pattern is being written, in the same green, so a
     * pattern marks the same slots whether it is being encoded or read back.
     */
    private void drawFluidSubstitutionHint() {
        if (this.fluidIcon == null || !this.fluidIcon.isMouseOver()) {
            return;
        }

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        for (int i = 0; i < this.fabricated.length && i < this.view.getInputSlots(); i++) {
            if (this.fabricated[i]) {
                final Slot slot = this.inventorySlots.inventorySlots.get(i);
                drawRect(slot.xPos, slot.yPos, slot.xPos + 16, slot.yPos + 16, FABRICATED_SLOT_TINT);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * A click here can never move anything - the container was never opened on the server and has no window
     * id to report against - so it is spent the way HEI spends one in its own item list instead: left shows
     * what makes this ingredient, right shows what it is used for.
     */
    @Override
    protected void handleMouseClick(final Slot slot, final int slotId, final int mouseButton,
            final ClickType type) {
        if (slot != null && !slot.getStack().isEmpty() && mouseButton <= 1
                && Platform.isModLoaded("jei")) {
            showRecipes(slot.getStack(), mouseButton == 0);
        }
    }

    @Optional.Method(modid = "jei")
    private static void showRecipes(final ItemStack stack, final boolean makes) {
        if (JEIPlugin.runtime == null) {
            return;
        }

        // A placeholder has to be read back into the fluid it stands for first, or HEI looks the shim up.
        final Object ingredient = AEGuiHandler.ingredientOf(stack);
        final IFocus.Mode mode = makes ? IFocus.Mode.OUTPUT : IFocus.Mode.INPUT;

        JEIPlugin.runtime.getRecipesGui()
                .show(JEIPlugin.runtime.getRecipeRegistry().createFocus(mode, ingredient));
    }

    /**
     * Escape and the inventory key would ordinarily tell the server to close a window, but this screen never
     * opened one - the player's real container is still the screen underneath. Put that back instead.
     */
    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (key == 1 || key == this.mc.gameSettings.keyBindInventory.getKeyCode()) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }

        super.keyTyped(character, key);
    }
}
