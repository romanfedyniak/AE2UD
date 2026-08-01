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


import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.me.GridInventoryEntry;
import appeng.core.AELog;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.crafting.CraftingJob;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.me.helpers.PlayerSource;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartTerminal;
import appeng.util.Platform;
import com.google.common.collect.ImmutableSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;


public class ContainerCraftConfirm extends AEBaseContainer {

    public static final long USED_PERCENT_SCALE = 10_000;

    private final ArrayList<CraftingCPURecord> cpus = new ArrayList<>();
    private Future<ICraftingJob> job;
    /**
     * Held as the concrete type, not the frozen {@link ICraftingJob} interface, specifically so this
     * container can call {@link CraftingJob#populatePlan(KeyCounter, KeyCounter)} - see CONTRACT.md §9.2.
     */
    private CraftingJob result;

    /**
     * What the player asked for, kept so cancelling can hand the order back to the amount screen exactly as
     * it was written. Not read from {@link #result}: an "up to" order plans the *difference*, so the job's
     * output is not the number that was typed.
     */
    @Nullable
    private AEKey requestedKey;
    private long requestedAmount;
    private boolean requestedCraftMissing;

    @GuiSync(0)
    public long bytesUsed;
    @GuiSync(1)
    public long cpuBytesAvail;
    @GuiSync(2)
    public int cpuCoProcessors;
    @GuiSync(3)
    public boolean autoStart = false;
    @GuiSync(4)
    public boolean simulation = true;
    @GuiSync(5)
    public int selectedCpu = -1;
    @GuiSync(6)
    public boolean noCPU = true;
    @GuiSync(7)
    public String myName = "";
    private GuiCraftConfirm guiCraftConfirm;

    public ContainerCraftConfirm(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }

    public void cycleCpu(final boolean next) {
        if (next) {
            this.setSelectedCpu(this.getSelectedCpu() + 1);
        } else {
            this.setSelectedCpu(this.getSelectedCpu() - 1);
        }

        if (this.getSelectedCpu() < -1) {
            this.setSelectedCpu(this.cpus.size() - 1);
        } else if (this.getSelectedCpu() >= this.cpus.size()) {
            this.setSelectedCpu(-1);
        }

        if (this.getSelectedCpu() == -1) {
            this.setCpuAvailableBytes(0);
            this.setCpuCoProcessors(0);
            this.setName("");
        } else {
            this.setName(this.cpus.get(this.getSelectedCpu()).getName());
            this.setCpuAvailableBytes(this.cpus.get(this.getSelectedCpu()).getSize());
            this.setCpuCoProcessors(this.cpus.get(this.getSelectedCpu()).getProcessors());
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isClient()) {
            return;
        }

        final IActionHost h = ((IActionHost) this.getTarget());
        if (h == null) {
            this.setValidContainer(false);
            return;
        }
        IGridNode node = h.getActionableNode();
        if (node == null) {
            this.setValidContainer(false);
            return;
        }
        IGrid grid = node.getGrid();

        final ICraftingGrid cc = grid.getCache(ICraftingGrid.class);
        final ImmutableSet<ICraftingCPU> cpuSet = cc.getCpus();

        int matches = 0;
        boolean changed = false;
        for (final ICraftingCPU c : cpuSet) {
            boolean found = false;
            for (final CraftingCPURecord ccr : this.cpus) {
                if (ccr.getCpu() == c) {
                    found = true;
                    break;
                }
            }

            final boolean matched = this.cpuMatches(c);

            if (matched) {
                matches++;
            }

            if (found == !matched) {
                changed = true;
            }
        }

        if (changed || this.cpus.size() != matches) {
            this.cpus.clear();
            for (final ICraftingCPU c : cpuSet) {
                if (this.cpuMatches(c)) {
                    this.cpus.add(new CraftingCPURecord(c.getAvailableStorage(), c.getCoProcessors(), c));
                }
            }

            this.sendCPUs();
        }

        this.setNoCPU(this.cpus.isEmpty());

        super.detectAndSendChanges();

        if (this.getJob() != null && this.getJob().isDone()) {
            try {
                this.result = (CraftingJob) this.getJob().get();

                if (!this.result.isSimulation()) {
                    this.setSimulation(false);
                    if (this.isAutoStart()) {
                        this.startJob();
                        return;
                    }
                } else {
                    this.setSimulation(true);
                }

                try {
                    final PacketMEInventoryUpdate a = new PacketMEInventoryUpdate((byte) 0);
                    final PacketMEInventoryUpdate b = new PacketMEInventoryUpdate((byte) 1);
                    final PacketMEInventoryUpdate c = this.result.isSimulation() ? new PacketMEInventoryUpdate((byte) 2) : null;

                    // CONTRACT.md §9.2: the frozen ICraftingJob.populatePlan(KeyCounter) would merge the
                    // used/missing amount and the to-craft amount into one number per key, losing exactly
                    // the used/to-craft column split this screen displays. this.result is held as the
                    // concrete CraftingJob type so its additive populatePlan(KeyCounter, KeyCounter)
                    // overload can be called instead, keeping the two apart.
                    final KeyCounter used = new KeyCounter();
                    final KeyCounter requestable = new KeyCounter();
                    final KeyCounter craftingSteps = new KeyCounter();
                    this.result.populatePlan(used, requestable, craftingSteps);

                    this.setUsedBytes(this.result.getByteTotal());

                    // IGrid.getCache infers its type variable from the assignment target, so the service
                    // has to land in a typed local before anything is called on it.
                    final IStorageService storageService = grid.getCache(IStorageService.class);
                    final MEStorage items = storageService.getInventory();

                    // A key can appear in only one of the two counters (e.g. fully on-hand needs no
                    // crafting, or fully missing-and-craftable has nothing "used" yet), so iterate the
                    // union rather than just one counter's keys.
                    final Set<AEKey> plannedKeys = new LinkedHashSet<>(used.keySet());
                    plannedKeys.addAll(requestable.keySet());
                    plannedKeys.addAll(craftingSteps.keySet());

                    for (final AEKey what : plannedKeys) {
                        long o = used.get(what);
                        final long p = requestable.get(what);

                        long m = 0;
                        if (c != null && this.result.isSimulation()) {
                            final long available = items.extract(what, o, Actionable.SIMULATE, this.getActionSource());
                            m = o - available;
                            o = available;
                        }

                        if (o > 0) {
                            final long availableAtStart = this.result.getAvailableAtStart(what);
                            final long usedPercent = encodeUsedPercent(o, availableAtStart);
                            a.appendItem(new GridInventoryEntry(what, o, usedPercent, false));
                        }

                        if (p > 0) {
                            b.appendItem(new GridInventoryEntry(what, p, craftingSteps.get(what), false));
                        }

                        if (c != null && m > 0) {
                            c.appendItem(new GridInventoryEntry(what, m, 0, false));
                        }
                    }

                    for (final Object g : this.listeners) {
                        if (g instanceof EntityPlayer) {
                            NetworkHandler.instance().sendTo(a, (EntityPlayerMP) g);
                            NetworkHandler.instance().sendTo(b, (EntityPlayerMP) g);
                            if (c != null) {
                                NetworkHandler.instance().sendTo(c, (EntityPlayerMP) g);
                            }
                        }
                    }
                } catch (final IOException e) {
                    // :P
                }
            } catch (final Throwable e) {
                this.getPlayerInv().player.sendMessage(new TextComponentString("Error: " + e));
                AELog.debug(e);
                this.setValidContainer(false);
                this.result = null;
            }

            this.setJob(null);
        }
        this.verifyPermissions(SecurityPermissions.CRAFT, false);
    }

    private boolean cpuMatches(final ICraftingCPU c) {
        return c.getAvailableStorage() >= this.getUsedBytes() && !c.isBusy();
    }

    private static long encodeUsedPercent(final long used, final long available) {
        if (used <= 0 || available <= 0) {
            return 0;
        }

        final double percentage = Math.min(100, (double) used / available * 100);
        return Math.round(percentage * USED_PERCENT_SCALE);
    }

    private void sendCPUs() {
        Collections.sort(this.cpus);

        if (this.getSelectedCpu() >= this.cpus.size()) {
            this.setSelectedCpu(-1);
            this.setCpuAvailableBytes(0);
            this.setCpuCoProcessors(0);
            this.setName("");
        } else if (this.getSelectedCpu() != -1) {
            this.setName(this.cpus.get(this.getSelectedCpu()).getName());
            this.setCpuAvailableBytes(this.cpus.get(this.getSelectedCpu()).getSize());
            this.setCpuCoProcessors(this.cpus.get(this.getSelectedCpu()).getProcessors());
        }
    }

    public void startJob() {
        GuiBridge originalGui = null;

        final IActionHost ah = this.getActionHost();
        if (ah instanceof WirelessTerminalGuiObject) {
            ItemStack myIcon = ((WirelessTerminalGuiObject) ah).getItemStack();
            originalGui = (GuiBridge) AEApi.instance().registries().wireless().getWirelessTerminalHandler(myIcon).getGuiHandler(myIcon);
        }

        if (ah instanceof PartTerminal) {
            originalGui = GuiBridge.GUI_ME;
        }

        if (ah instanceof PartCraftingTerminal) {
            originalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (ah instanceof PartPatternTerminal) {
            originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (ah instanceof PartExpandedProcessingPatternTerminal) {
            originalGui = GuiBridge.GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }

        final IActionHost h = ((IActionHost) this.getTarget());
        if (h == null) {
            return;
        }
        IGridNode node = h.getActionableNode();
        if (node == null) {
            return;
        }
        IGrid grid = node.getGrid();

        if (this.result != null && !this.isSimulation()) {
            final ICraftingGrid cc = grid.getCache(ICraftingGrid.class);
            final ICraftingLink g = cc.submitJob(this.result, null, this.getSelectedCpu() == -1 ? null : this.cpus.get(this.getSelectedCpu()).getCpu(), true, this.getActionSrc());
            this.setAutoStart(false);
            if (g == null) {
                this.setJob(cc.beginCraftingJob(this.getWorld(), grid, this.getActionSrc(), this.result.getOutput(), null));
            } else if (originalGui != null && this.getOpenContext() != null) {
                final TileEntity te = this.getOpenContext().getTile();
                if (te != null) {
                    Platform.openGUI(this.getInventoryPlayer().player, te, this.getOpenContext().getSide(), originalGui);
                } else {
                    if (ah instanceof IInventorySlotAware i) {
                        Platform.openGUI(this.getInventoryPlayer().player, i.getInventorySlot(), originalGui, i.isBaubleSlot());
                    }
                }
            }
        }
    }

    private IActionSource getActionSrc() {
        return new PlayerSource(this.getPlayerInv().player, (IActionHost) this.getTarget());
    }

    @Override
    public void removeListener(final IContainerListener c) {
        super.removeListener(c);
        if (this.getJob() != null) {
            this.getJob().cancel(true);
            this.setJob(null);
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer par1EntityPlayer) {
        super.onContainerClosed(par1EntityPlayer);
        if (this.getJob() != null) {
            this.getJob().cancel(true);
            this.setJob(null);
        }
    }

    public World getWorld() {
        return this.getPlayerInv().player.world;
    }

    public boolean isAutoStart() {
        return this.autoStart;
    }

    public void setAutoStart(final boolean autoStart) {
        this.autoStart = autoStart;
    }

    public long getUsedBytes() {
        return this.bytesUsed;
    }

    private void setUsedBytes(final long bytesUsed) {
        this.bytesUsed = bytesUsed;
    }

    public long getCpuAvailableBytes() {
        return this.cpuBytesAvail;
    }

    private void setCpuAvailableBytes(final long cpuBytesAvail) {
        this.cpuBytesAvail = cpuBytesAvail;
    }

    public int getCpuCoProcessors() {
        return this.cpuCoProcessors;
    }

    private void setCpuCoProcessors(final int cpuCoProcessors) {
        this.cpuCoProcessors = cpuCoProcessors;
    }

    public int getSelectedCpu() {
        return this.selectedCpu;
    }

    private void setSelectedCpu(final int selectedCpu) {
        this.selectedCpu = selectedCpu;
    }

    public String getName() {
        return this.myName;
    }

    private void setName(@Nonnull final String myName) {
        this.myName = myName;
    }

    public boolean hasNoCPU() {
        return this.noCPU;
    }

    private void setNoCPU(final boolean noCPU) {
        this.noCPU = noCPU;
    }

    public boolean isSimulation() {
        return this.simulation;
    }

    private void setSimulation(final boolean simulation) {
        this.simulation = simulation;
    }

    private Future<ICraftingJob> getJob() {
        return this.job;
    }

    public void setJob(final Future<ICraftingJob> job) {
        this.job = job;
    }

    public void setRequest(final AEKey what, final long amount, final boolean craftMissing) {
        this.requestedKey = what;
        this.requestedAmount = amount;
        this.requestedCraftMissing = craftMissing;
    }

    /**
     * Hands the order being shown back to an amount screen, so cancelling a plan returns the player to what
     * they typed rather than to a blank one.
     */
    public void restoreRequestTo(final ContainerCraftAmount amount) {
        if (this.requestedKey != null) {
            amount.setRequest(this.requestedKey, this.requestedAmount, this.requestedCraftMissing);
        }
    }

    public void postUpdate(final List<GridInventoryEntry> list, final byte ref) {
        this.guiCraftConfirm.postUpdate(list, ref);
    }

    public void setGui(GuiCraftConfirm guiCraftConfirm) {
        this.guiCraftConfirm = guiCraftConfirm;
    }
}
