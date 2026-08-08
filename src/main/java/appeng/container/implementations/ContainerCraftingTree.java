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

package appeng.container.implementations;


import appeng.api.storage.ITerminalHost;
import appeng.container.me.GridInventoryEntry;
import appeng.core.AELog;
import appeng.core.sync.packets.PacketCraftingPlanTree;
import appeng.crafting.CraftingJob;
import appeng.crafting.tree.CraftingPlanTree;
import appeng.util.Platform;
import net.minecraft.entity.player.InventoryPlayer;

import java.io.IOException;
import java.util.List;


/**
 * The same plan as {@link ContainerCraftConfirm}, shown as a tree instead of a list. It inherits the plan
 * itself, the CPU table and starting the job, and adds only the tree the screen draws.
 */
public class ContainerCraftingTree extends ContainerCraftConfirm {

    private boolean treeWanted;

    public ContainerCraftingTree(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }

    /**
     * Marks the tree for building on the next tick. Switching screens is handled on the network thread,
     * where walking the solver's nodes would race the tick that can replace the job underneath it.
     */
    public void requestTree() {
        this.treeWanted = true;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isClient() || !this.treeWanted) {
            return;
        }

        final CraftingJob job = this.getResult();
        if (job == null) {
            return;
        }

        this.treeWanted = false;
        this.sendTree(job);
    }

    /**
     * NOTE: built on the tick thread so nothing can swap the job out mid-walk. If a huge plan ever costs
     * noticeable tick time, snapshot the tree here and move the walk and compression off-thread.
     */
    private void sendTree(final CraftingJob job) {
        final CraftingPlanTree tree = CraftingPlanTree.of(job);
        if (tree == null) {
            return;
        }

        try {
            this.sendPacketToListeners(new PacketCraftingPlanTree(tree));
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    @Override
    public void postUpdate(final List<GridInventoryEntry> list, final byte ref) {
        // The tree screen has no plan list to fill; it draws the same plan from the tree instead.
    }
}
