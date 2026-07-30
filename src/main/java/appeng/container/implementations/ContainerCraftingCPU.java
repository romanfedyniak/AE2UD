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


import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.me.GridInventoryEntry;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.ICustomNameObject;
import appeng.me.cluster.IAEMultiBlock;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cluster.implementations.ICraftingCPUListener;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * Push-based, not polled: see {@link ICraftingCPUListener} and CONTRACT.md §10 ("Crafting CPU push
 * notifications" - the owner rejected replacing this mechanism with polling). This container subscribes to
 * a {@link CraftingCPUCluster} via {@link CraftingCPUCluster#addListener(ICraftingCPUListener, Object)} the
 * same way the pre-migration code subscribed an {@code IMEMonitorHandlerReceiver}, and unsubscribes via
 * {@link CraftingCPUCluster#removeListener(ICraftingCPUListener)}.
 * <p/>
 * {@link #onCraftingCPUChange(AEKey, IActionSource)} only marks {@code what} dirty - the actual
 * stored/active/pending amounts are re-read authoritatively from the cluster in
 * {@link #detectAndSendChanges()} via {@link CraftingCPUCluster#getItemStack(AEKey, CraftingItemList)},
 * exactly as {@link ICraftingCPUListener}'s javadoc describes.
 */
public class ContainerCraftingCPU extends AEBaseContainer implements ICraftingCPUListener, ICustomNameObject {

    /**
     * Every key this container has ever reported to its GUI. Replaces the old {@code IItemList<IAEItemStack>}
     * that {@code postChange} fed with size-1 markers: since only key identity mattered there (the amounts
     * were always re-read fresh via {@code getItemStack}), a plain key set carries the same information.
     */
    private final Set<AEKey> trackedKeys = new LinkedHashSet<>();
    private IGrid network;
    private CraftingCPUCluster monitor = null;
    private String cpuName = null;

    @GuiSync(0)
    public long eta = -1;
    /**
     * Time the running job has actually taken. Synced separately from {@link #eta} because the crafting
     * status screen shows elapsed time rather than a prediction - a prediction that kept moving as the
     * job's own throughput changed was more distracting than useful.
     */
    @GuiSync(1)
    public long elapsed = 0;
    private GuiCraftingCPU guiCraftingCPU;

    public ContainerCraftingCPU(final InventoryPlayer ip, final Object te) {
        super(ip, te);
        final IActionHost host = (IActionHost) (te instanceof IActionHost ? te : null);

        if (host != null && host.getActionableNode() != null) {
            this.setNetwork(host.getActionableNode().getGrid());
        }

        if (te instanceof TileCraftingTile) {
            this.setCPU((ICraftingCPU) ((IAEMultiBlock) te).getCluster());
        }

        if (this.getNetwork() == null && Platform.isServer()) {
            this.setValidContainer(false);
        }
    }

    protected void setCPU(final ICraftingCPU c) {
        if (c == this.getMonitor()) {
            return;
        }

        if (this.getMonitor() != null) {
            this.getMonitor().removeListener(this);
        }

        for (final Object g : this.listeners) {
            if (g instanceof EntityPlayer) {
                try {
                    NetworkHandler.instance().sendTo(new PacketValueConfig("CraftingStatus", "Clear"), (EntityPlayerMP) g);
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            }
        }

        if (c instanceof CraftingCPUCluster) {
            this.cpuName = c.getName();
            this.setMonitor((CraftingCPUCluster) c);
            this.trackedKeys.clear();

            final KeyCounter all = new KeyCounter();
            this.getMonitor().getListOfItem(all, CraftingItemList.ALL);
            for (final var entry : all) {
                this.trackedKeys.add(entry.getKey());
            }

            this.getMonitor().addListener(this, null);
            this.setEstimatedTime(0);
        } else {
            this.setMonitor(null);
            this.cpuName = "";
            this.setEstimatedTime(-1);
        }
    }

    public void cancelCrafting() {
        if (this.getMonitor() != null) {
            this.getMonitor().cancel();
        }
        this.setEstimatedTime(-1);
    }

    @Override
    public void removeListener(final IContainerListener c) {
        super.removeListener(c);

        if (this.listeners.isEmpty() && this.getMonitor() != null) {
            this.getMonitor().removeListener(this);
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        if (this.getMonitor() != null) {
            this.getMonitor().removeListener(this);
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer() && this.getMonitor() != null) {
            if (this.getEstimatedTime() >= 0) {
                final long elapsedTime = this.getMonitor().getElapsedTime();
                final double remainingItems = this.getMonitor().getRemainingItemCount();
                final double startItems = this.getMonitor().getStartItemCount();
                final long eta = (long) (elapsedTime / Math.max(1d, (startItems - remainingItems)) * remainingItems);
                this.setEstimatedTime(eta);
                this.elapsed = elapsedTime;
            }
            if (!this.trackedKeys.isEmpty()) {
                try {
                    final PacketMEInventoryUpdate a = new PacketMEInventoryUpdate((byte) 0);
                    final PacketMEInventoryUpdate b = new PacketMEInventoryUpdate((byte) 1);
                    final PacketMEInventoryUpdate c = new PacketMEInventoryUpdate((byte) 2);

                    for (final AEKey what : this.trackedKeys) {
                        final GenericStack stored = this.getMonitor().getItemStack(what, CraftingItemList.STORAGE);
                        final GenericStack active = this.getMonitor().getItemStack(what, CraftingItemList.ACTIVE);
                        final GenericStack pending = this.getMonitor().getItemStack(what, CraftingItemList.PENDING);

                        a.appendItem(new GridInventoryEntry(what, stored.amount(), 0, false));
                        b.appendItem(new GridInventoryEntry(what, active.amount(), 0, false));
                        c.appendItem(new GridInventoryEntry(what, pending.amount(), 0, false));
                    }

                    for (final Object g : this.listeners) {
                        if (g instanceof EntityPlayer) {
                            if (!a.isEmpty()) {
                                NetworkHandler.instance().sendTo(a, (EntityPlayerMP) g);
                            }

                            if (!b.isEmpty()) {
                                NetworkHandler.instance().sendTo(b, (EntityPlayerMP) g);
                            }

                            if (!c.isEmpty()) {
                                NetworkHandler.instance().sendTo(c, (EntityPlayerMP) g);
                            }
                        }
                    }
                } catch (final IOException e) {
                    // :P
                }
            }
        }
        super.detectAndSendChanges();
    }

    @Override
    public boolean isValid(final Object verificationToken) {
        return true;
    }

    @Override
    public void onCraftingCPUChange(final AEKey what, final IActionSource source) {
        this.trackedKeys.add(what);
    }

    @Override
    public String getCustomInventoryName() {
        return this.cpuName;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return this.cpuName != null && this.cpuName.length() > 0;
    }

    public long getEstimatedTime() {
        return this.eta;
    }

    private void setEstimatedTime(final long eta) {
        this.eta = eta;
    }

    CraftingCPUCluster getMonitor() {
        return this.monitor;
    }

    private void setMonitor(final CraftingCPUCluster monitor) {
        this.monitor = monitor;
    }

    IGrid getNetwork() {
        return this.network;
    }

    private void setNetwork(final IGrid network) {
        this.network = network;
    }

    public void postUpdate(final List<GridInventoryEntry> list, final byte ref) {
        this.guiCraftingCPU.postUpdate(list, ref);
    }

    public void setGui(GuiCraftingCPU guiCraftingCPU) {
        this.guiCraftingCPU = guiCraftingCPU;
    }
}
