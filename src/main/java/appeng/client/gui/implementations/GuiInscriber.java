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


import appeng.api.config.InscriberInputCapacity;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiProgressBar;
import appeng.client.gui.widgets.GuiProgressBar.Direction;
import appeng.container.implementations.ContainerInscriber;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.tile.misc.TileInscriber;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;


public class GuiInscriber extends GuiUpgradeable {

    /**
     * The arrow between what goes in and what comes out. JEI opens the inscriber's recipes from there, which
     * is where the eye looks for them - not the thin progress bar at the edge of the window.
     */
    public static final int RECIPE_LEFT = 82;
    public static final int RECIPE_TOP = 39;
    public static final int RECIPE_WIDTH = 26;
    public static final int RECIPE_HEIGHT = 16;

    /** Whether a recipe viewer is there to open, which is the only reason the arrow answers the mouse. */
    private static final boolean RECIPE_VIEWER = Loader.isModLoaded("jei");


    private final ContainerInscriber cvc;
    private GuiProgressBar pb;

    private GuiImgButton separateSides;
    private GuiImgButton autoExport;
    private GuiImgButton bufferSize;

    public GuiInscriber(final InventoryPlayer inventoryPlayer, final TileInscriber te) {
        super(new ContainerInscriber(inventoryPlayer, te));
        this.cvc = (ContainerInscriber) this.inventorySlots;
        this.ySize = 176;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.pb = new GuiProgressBar(this.cvc, "guis/inscriber.png", 135, 39, 135, 177, 6, 18, Direction.VERTICAL);
        this.buttonList.add(this.pb);
    }

    @Override
    protected void addButtons() {
        this.separateSides = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.INSCRIBER_SEPARATE_SIDES, YesNo.NO);
        this.autoExport = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.AUTO_EXPORT, YesNo.NO);
        this.bufferSize = new GuiImgButton(this.guiLeft - 18, this.guiTop + 48, Settings.INSCRIBER_INPUT_CAPACITY, InscriberInputCapacity.SIXTY_FOUR);

        this.buttonList.add(this.separateSides);
        this.buttonList.add(this.autoExport);
        this.buttonList.add(this.bufferSize);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.separateSides || btn == this.autoExport || btn == this.bufferSize) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(((GuiImgButton) btn).getSetting(), Mouse.isButtonDown(1)));
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        this.pb.setFullMsg(this.cvc.getCurrentProgress() * 100 / this.cvc.getMaxProgress() + "%");

        this.separateSides.set(this.cvc.getSeparateSides());
        this.autoExport.set(this.cvc.getAutoExport());
        this.bufferSize.set(this.cvc.getBufferSize());
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // The line is JEI's own, so whoever has it installed already has it translated
        if (RECIPE_VIEWER && mouseX >= this.guiLeft + RECIPE_LEFT && mouseX < this.guiLeft + RECIPE_LEFT + RECIPE_WIDTH
                && mouseY >= this.guiTop + RECIPE_TOP && mouseY < this.guiTop + RECIPE_TOP + RECIPE_HEIGHT) {
            this.drawHoveringText(Collections.singletonList(I18n.format("jei.tooltip.show.recipes")), mouseX, mouseY);
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.pb.x = 135 + this.guiLeft;
        this.pb.y = 39 + this.guiTop;

        super.drawBG(offsetX, offsetY, mouseX, mouseY);
    }

    @Nullable
    @Override
    protected GuiText getName() {
        return GuiText.Inscriber;
    }

    @Override
    protected String getBackground() {
        return "guis/inscriber.png";
    }
}
