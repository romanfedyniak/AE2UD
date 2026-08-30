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


import appeng.container.implementations.ContainerCraftPriority;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftPriority;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.helpers.ICraftPriorityTarget;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;


/**
 * The amount screen, typing the priority of a crafting job.
 * <p>
 * It is summoned over the screen that asked for it and puts that one back when it leaves, the way the
 * amount button settings are: the screen underneath is showing a crafting plan or a list of processors, and
 * both are dear enough to rebuild that they should not be thrown away to type one number. It reads the
 * number off the container underneath and never says whose it is: the server puts the answer wherever that
 * player's open container keeps it.
 */
public class GuiCraftPriority extends GuiCraftAmount {

    private static final int BACK_X = 154;

    private final GuiScreen parent;
    private final ICraftPriorityTarget target;
    private final Container parentContainer;
    private final ItemStack icon;
    private GuiTabButton back;

    public GuiCraftPriority(final GuiScreen parent, final InventoryPlayer ip, final ItemStack icon, final ICraftPriorityTarget target) {
        super(new ContainerCraftPriority(ip, icon));

        this.parent = parent;
        this.target = target;
        this.icon = icon;
        this.parentContainer = parent instanceof GuiContainer container ? container.inventorySlots : null;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Where every other amount screen keeps the way out. This one goes back to a screen rather than to a
        // GuiBridge, so it is added here instead of by the tab the base class builds from the container's host.
        this.back = new GuiTabButton(this.guiLeft + BACK_X, this.guiTop, this.icon,
                GuiText.CraftPriorityBack.getLocal(), this.itemRender);
        this.buttonList.add(this.back);

        // GuiContainer.initGui points the player at this screen's container. The one underneath is still the
        // open one as far as the server is concerned, and a synced field is only ever sent when it moves - so
        // anything that moved while the client was looking elsewhere would never arrive at all.
        if (this.parentContainer != null) {
            this.mc.player.openContainer = this.parentContainer;
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.back) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    protected void confirm(final long amount) {
        NetworkHandler.instance().sendToServer(new PacketCraftPriority((int) amount));
        this.mc.displayGuiScreen(this.parent);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        // Escape belongs to this screen, not to the one underneath: leaving here is going back, not
        // closing the terminal the plan is sitting in.
        if (key == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }

        super.keyTyped(character, key);
    }

    @Override
    protected long getMinAmount() {
        return Integer.MIN_VALUE;
    }

    @Override
    protected long getMaxAmount() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected long getInitialAmount() {
        return this.target.getCraftPriority();
    }

    @Override
    protected boolean isReady() {
        // The number came with the screen rather than over the wire, so there is nothing to wait for.
        return true;
    }

    @Override
    protected boolean startsFromSuggestion() {
        return false;
    }

    @Override
    protected boolean startsAsMissingAmount() {
        return false;
    }

    @Override
    protected boolean craftsMissingAmount() {
        return false;
    }

    @Override
    protected String getTitle() {
        return GuiText.CraftPriority.getLocal();
    }

    @Override
    protected String getConfirmLabel() {
        return GuiText.Set.getLocal();
    }
}
