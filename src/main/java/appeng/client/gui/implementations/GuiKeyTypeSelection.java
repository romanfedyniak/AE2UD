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


import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.stacks.AEKeyType;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerKeyTypeSelection;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;


/**
 * One row per key type the host allows: a toggle and the type's own name. The panel grows with the number
 * of rows rather than reserving a fixed number, because how many there are is up to whoever registered them.
 *
 * @see ContainerKeyTypeSelection
 */
public class GuiKeyTypeSelection extends AEBaseGui {

    private static final int HEADER_HEIGHT = 18;
    private static final int ROW_HEIGHT = 18;
    private static final int FOOTER_HEIGHT = 11;
    private static final int WIDTH = 176;

    private static final int ICON_ON = 11 * 16 + 6;
    private static final int ICON_OFF = 12 * 16 + 6;

    private final List<AEKeyType> rows = new ArrayList<>();
    private final Map<AEKeyType, GuiToggleButton> toggles = new LinkedHashMap<>();

    private GuiTabButton originalGuiBtn;
    private GuiBridge originalGui;

    public GuiKeyTypeSelection(final InventoryPlayer inventoryPlayer, final KeyTypeSelectionHost te) {
        super(new ContainerKeyTypeSelection(inventoryPlayer, te));
        this.xSize = WIDTH;
        this.ySize = HEADER_HEIGHT + FOOTER_HEIGHT;
    }

    @Override
    public void initGui() {
        this.rebuildRows();
        super.initGui();

        final ContainerKeyTypeSelection container = (ContainerKeyTypeSelection) this.inventorySlots;
        final ItemStack icon = container.getSubMenuHost().getItemStackRepresentation();
        this.originalGui = container.getSubMenuHost().getGuiBridge();

        if (this.originalGui != null && !icon.isEmpty()) {
            this.buttonList.add(this.originalGuiBtn = new GuiTabButton(this.guiLeft + 154, this.guiTop, icon, icon.getDisplayName(), this.itemRender));
        }

        this.toggles.clear();
        for (int i = 0; i < this.rows.size(); i++) {
            final AEKeyType type = this.rows.get(i);
            final GuiToggleButton toggle = new GuiToggleButton(this.guiLeft + 8, this.guiTop + HEADER_HEIGHT + i * ROW_HEIGHT + 1,
                    ICON_ON, ICON_OFF, type.getDescription().getFormattedText(), GuiText.ConfigureImportedTypesHint.getLocal());
            this.toggles.put(type, toggle);
            this.buttonList.add(toggle);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        // The row list arrives a tick after the screen opens, and its length decides how tall the panel is.
        if (this.rebuildRows()) {
            this.buttonList.clear();
            this.initGui();
        }
    }

    private boolean rebuildRows() {
        final List<AEKeyType> current = new ArrayList<>(((ContainerKeyTypeSelection) this.inventorySlots).getSelection().keySet());
        if (current.equals(this.rows)) {
            return false;
        }

        this.rows.clear();
        this.rows.addAll(current);
        this.ySize = HEADER_HEIGHT + this.rows.size() * ROW_HEIGHT + FOOTER_HEIGHT;
        return true;
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(GuiText.ConfigureImportedTypes.getLocal(), 8, 6, 4210752);

        final Map<AEKeyType, Boolean> selection = ((ContainerKeyTypeSelection) this.inventorySlots).getSelection();
        for (int i = 0; i < this.rows.size(); i++) {
            final AEKeyType type = this.rows.get(i);

            final GuiToggleButton toggle = this.toggles.get(type);
            if (toggle != null) {
                toggle.setState(Boolean.TRUE.equals(selection.get(type)));
            }

            this.fontRenderer.drawString(type.getDescription().getFormattedText(), 30, HEADER_HEIGHT + i * ROW_HEIGHT + 5, 4210752);
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/keytypes.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, WIDTH, HEADER_HEIGHT);
        for (int i = 0; i < this.rows.size(); i++) {
            this.drawTexturedModalRect(offsetX, offsetY + HEADER_HEIGHT + i * ROW_HEIGHT, 0, HEADER_HEIGHT, WIDTH, ROW_HEIGHT);
        }
        this.drawTexturedModalRect(offsetX, offsetY + HEADER_HEIGHT + this.rows.size() * ROW_HEIGHT, 0, HEADER_HEIGHT + ROW_HEIGHT, WIDTH, FOOTER_HEIGHT);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
            return;
        }

        for (final Map.Entry<AEKeyType, GuiToggleButton> entry : this.toggles.entrySet()) {
            if (entry.getValue() == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("KeyTypes.Toggle", entry.getKey().getRegistryName().toString()));
                return;
            }
        }
    }
}
